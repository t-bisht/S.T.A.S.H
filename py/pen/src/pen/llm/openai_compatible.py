"""OpenAI-compatible provider — covers Groq / OpenAI / NVIDIA / OpenRouter (spec §5.4).

All four expose the same Chat Completions API, so one implementation serves them; only
``base_url`` / ``api_key`` / ``model`` differ (chosen by the factory from config). The provider
is deterministic (``temperature=0``), reads ``message.content`` (``gpt-oss`` keeps its thinking
in ``reasoning_content``, which we ignore), and turns the response into a JSON dict — stripping
code fences / slicing the first ``{…}`` if the model wrapped the JSON.

Error typing is the contract the retry policy depends on (spec §4.3):
    429 → :class:`LLMRateLimited`;  unparseable JSON → :class:`LLMResponseInvalid`;
    any other transport/5xx → :class:`LLMError`.

The API key is never logged. Per-call token counts + elapsed time are logged for cost/latency
observability (spec §12).
"""

from __future__ import annotations

import json
import logging
import re
import time

from openai import (
    APIConnectionError,
    APIError,
    APITimeoutError,
    BadRequestError,
    OpenAI,
    RateLimitError,
)

from pen.llm.base import LLMProvider
from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid

__all__ = ["OpenAICompatibleProvider"]

log = logging.getLogger(__name__)

# Matches a ```json … ``` or bare ``` … ``` fence the model may wrap JSON in.
_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)


class OpenAICompatibleProvider(LLMProvider):
    """An :class:`LLMProvider` backed by any OpenAI-compatible Chat Completions endpoint."""

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        reasoning_effort: str | None = None,
        temperature: float = 0.0,
        max_tokens: int = 1500,
        system_fallback: str = "You are a precise information extraction and classification system.",
    ) -> None:
        """Create the provider.

        Args:
            base_url: OpenAI-compatible base URL (e.g. ``https://api.groq.com/openai/v1``).
            api_key: Provider API key. **Secret — never logged.**
            model: Model string (e.g. ``openai/gpt-oss-20b``).
            reasoning_effort: Optional ``reasoning_effort`` hint; sent only if non-null and
                auto-dropped (one retry) if the provider rejects it.
            temperature: Sampling temperature; ``0`` for deterministic extraction.
            max_tokens: Response token cap.
            system_fallback: Default system prompt if a caller passes an empty one.
        """
        # A keyless endpoint (e.g. a local model) passes an empty key; the SDK still requires a
        # non-empty string, so use a harmless placeholder it will send but the server ignores.
        self._client = OpenAI(base_url=base_url, api_key=api_key or "no-key")
        self._model = model
        self._reasoning_effort = reasoning_effort
        self._temperature = temperature
        self._max_tokens = max_tokens
        self._system_fallback = system_fallback

    def complete_json(self, system: str, prompt: str) -> dict:
        """See :meth:`pen.llm.base.LLMProvider.complete_json`."""
        content = self._call(system or self._system_fallback, prompt)
        return _parse_json(content)

    def _call(self, system: str, prompt: str) -> str:
        """Make the chat completion call and return ``message.content``.

        Sends ``reasoning_effort`` when configured; on a ``BadRequestError`` (the provider
        rejects that param) it retries ONCE without it, so the same code works across providers
        that do and don't accept the hint (spec §5.4).
        """
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ]
        extra = (
            {"reasoning_effort": self._reasoning_effort} if self._reasoning_effort else {}
        )

        start = time.perf_counter()
        try:
            response = self._create(messages, extra)
        except BadRequestError:
            if not extra:
                raise LLMError("LLM bad request") from None
            log.warning("provider rejected reasoning_effort — retrying once without it")
            response = self._create(messages, {})
        elapsed = time.perf_counter() - start

        usage = getattr(response, "usage", None)
        log.info(
            "llm call ok model=%s elapsed=%.2fs prompt_tokens=%s completion_tokens=%s",
            self._model,
            elapsed,
            getattr(usage, "prompt_tokens", "?"),
            getattr(usage, "completion_tokens", "?"),
        )

        message = response.choices[0].message
        # gpt-oss keeps its thinking in reasoning_content; we deliberately use only content.
        return message.content or ""

    def _create(self, messages: list[dict], extra: dict) -> object:
        """Issue one Chat Completions request, mapping SDK errors to the typed hierarchy.

        Raises:
            LLMRateLimited: On a 429.
            LLMError: On any other transport/5xx/SDK error (retryable). ``BadRequestError`` is
                re-raised unmapped so :meth:`_call` can drop ``reasoning_effort`` and retry.
        """
        try:
            return self._client.chat.completions.create(
                model=self._model,
                messages=messages,
                temperature=self._temperature,
                max_tokens=self._max_tokens,
                **extra,
            )
        except RateLimitError as exc:
            raise LLMRateLimited(str(exc)) from exc
        except BadRequestError:
            raise  # handled by _call (reasoning_effort drop); never reaches retry wrapper.
        except (APITimeoutError, APIConnectionError) as exc:
            raise LLMError(f"transport error: {exc}") from exc
        except APIError as exc:  # 5xx and other API-level errors
            raise LLMError(f"api error: {exc}") from exc


def _parse_json(content: str) -> dict:
    """Parse ``content`` into a JSON object, with a fence-strip / brace-slice fallback.

    Order: direct ``json.loads`` → strip a ```` ```json ```` fence → slice the first balanced
    ``{…}`` span. If none yields a dict, raise :class:`LLMResponseInvalid` (permanent; the raw
    text is carried for triage).
    """
    for candidate in _json_candidates(content):
        try:
            parsed = json.loads(candidate)
        except (json.JSONDecodeError, TypeError):
            continue
        if isinstance(parsed, dict):
            return parsed
    raise LLMResponseInvalid("model output is not parseable JSON", raw=content)


def _json_candidates(content: str):
    """Yield progressively more aggressive JSON candidate strings from raw model output."""
    text = (content or "").strip()
    if not text:
        return
    yield text

    fence = _FENCE_RE.search(text)
    if fence:
        yield fence.group(1).strip()

    first, last = text.find("{"), text.rfind("}")
    if first != -1 and last != -1 and last > first:
        yield text[first : last + 1]
