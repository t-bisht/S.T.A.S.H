"""Typed LLM errors that drive the retry / park policy (spec §4.3, §5.4).

The retry wrapper (:mod:`pen.core.retry`) distinguishes them by type:

- :class:`LLMRateLimited` (429) → pause 60s, then retry (free-tier window is per-minute).
- :class:`LLMResponseInvalid` → **permanent**, never retried → park ``invalid_json``.
- :class:`LLMError` (other transport / 5xx) → short backoff, then retry.

All three share the :class:`LLMError` base so a caller can catch "any LLM failure", but the
retry wrapper's ``except`` ordering (rate-limited → invalid → base) keeps an invalid response
from being retried.
"""

from __future__ import annotations

__all__ = ["LLMError", "LLMRateLimited", "LLMResponseInvalid"]


class LLMError(Exception):
    """Base for any LLM provider failure; also the concrete type for transient transport/5xx
    errors that *are* retryable."""


class LLMRateLimited(LLMError):
    """A 429 rate-limit response. Drives the 60s pause-then-retry (spec §4.3)."""


class LLMResponseInvalid(LLMError):
    """The model's output could not be parsed as JSON even after the fence-strip fallback.

    Permanent (not retryable): a retry would re-produce the same unparseable output. The raw
    text is carried for triage and written to ``parse_failures.llm_raw``.
    """

    def __init__(self, message: str, *, raw: str | None = None) -> None:
        super().__init__(message)
        self.raw = raw
