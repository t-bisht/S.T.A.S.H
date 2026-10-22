"""Provider-agnostic LLM layer (spec §5).

A single :class:`~pen.llm.base.LLMProvider` interface sits between every handler and any
concrete LLM. Handlers depend only on the interface; the concrete provider is chosen once, by
config, in :func:`~pen.llm.factory.build_provider`. This is the "swap guarantee": moving
between OpenAI-compatible providers is config-only; a non-compatible provider is one new
subclass + one factory branch, with zero handler/orchestrator/scheduler change.
"""

from __future__ import annotations

from pen.llm.base import LLMProvider
from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid
from pen.llm.factory import build_provider

__all__ = [
    "LLMError",
    "LLMProvider",
    "LLMRateLimited",
    "LLMResponseInvalid",
    "build_provider",
]
