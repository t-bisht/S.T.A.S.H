"""The ``LLMProvider`` interface — the ONLY thing a handler sees of the LLM (spec §5.1).

A provider knows nothing about Swiggy or the taxonomy: it only turns a ``(system, prompt)``
pair into a validated JSON dict. All prompt building, schema, and post-processing live in the
handler. Keeping this boundary thin is what lets every handler reuse one call path and what
makes a provider swap a local change.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

__all__ = ["LLMProvider"]


class LLMProvider(ABC):
    """Abstract one-shot JSON extraction call."""

    @abstractmethod
    def complete_json(self, system: str, prompt: str) -> dict:
        """Run one extraction call and return the parsed JSON object.

        Args:
            system: System prompt (role/behaviour framing).
            prompt: The user prompt (schema + rules + the email body).

        Returns:
            The model's response parsed into a ``dict``.

        Raises:
            LLMRateLimited: On a 429 — the caller pauses 60s then retries (spec §4.3).
            LLMResponseInvalid: When the output is not parseable JSON (permanent — park).
            LLMError: On any other transport/5xx error (retryable).
        """
        raise NotImplementedError
