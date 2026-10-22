"""Provider factory — builds one provider PER HANDLER from its own settings (spec §5.1).

Each handler declares its own :class:`~pen.parse_handler_config.LLMSettings` (provider, base_url,
model, which env var holds its key). :func:`build_provider` turns those settings + the captured
secret map into a concrete :class:`~pen.llm.base.LLMProvider`. This is what lets Swiggy run on
Groq while Paytm runs on OpenRouter / NVIDIA / a local model — each gets its own provider
instance, built with its own key.

v1 providers (groq / openai / nvidia / openrouter / local) are all OpenAI-compatible → one
implementation. A future non-compatible provider (Anthropic-native, Vertex) adds a subclass + a
branch here, and nothing else changes.
"""

from __future__ import annotations

import logging
from collections.abc import Mapping

from pen.config import ConfigError
from pen.llm.base import LLMProvider
from pen.llm.openai_compatible import OpenAICompatibleProvider
from pen.parse_handler_config import LLMSettings

__all__ = ["OPENAI_COMPATIBLE_PROVIDERS", "build_provider"]

log = logging.getLogger(__name__)

# Known OpenAI-compatible provider labels (used for a friendly boot log; not a hard gate — any
# label resolves to the compatible provider, since the base_url is what actually routes).
OPENAI_COMPATIBLE_PROVIDERS = frozenset({"groq", "openai", "nvidia", "openrouter", "local"})


def build_provider(settings: LLMSettings, api_keys: Mapping[str, str]) -> LLMProvider:
    """Build the :class:`LLMProvider` for one handler from its settings + the secret map.

    Args:
        settings: The handler's own provider/model choice.
        api_keys: Captured secret map (name → value) from :attr:`Config.api_keys`.

    Returns:
        A ready :class:`LLMProvider`. In v1 always an
        :class:`~pen.llm.openai_compatible.OpenAICompatibleProvider`.

    Raises:
        ConfigError: If ``settings.api_key_env`` is set but missing from ``api_keys`` (a required
            secret was not supplied).
    """
    label = settings.provider.lower()
    if label not in OPENAI_COMPATIBLE_PROVIDERS:
        # Not an error: the label is cosmetic and the base_url routes the call. Just flag it.
        log.warning(
            "LLM provider=%r is not a known OpenAI-compatible label; using the compatible "
            "provider against base_url=%s anyway",
            settings.provider,
            settings.base_url,
        )

    api_key = _resolve_key(settings, api_keys)

    log.info(
        "built LLM provider=%s model=%s base_url=%s reasoning_effort=%s key_env=%s",
        settings.provider,
        settings.model,
        settings.base_url,
        settings.reasoning_effort or "-",
        settings.api_key_env or "(none)",
    )
    return OpenAICompatibleProvider(
        base_url=settings.base_url,
        api_key=api_key,
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
    )


def _resolve_key(settings: LLMSettings, api_keys: Mapping[str, str]) -> str:
    """Resolve the handler's API key by its env-var name (empty for a keyless endpoint)."""
    if not settings.api_key_env:
        return ""  # keyless endpoint (e.g. a local model that needs no auth)
    key = api_keys.get(settings.api_key_env, "")
    if not key:
        raise ConfigError(
            f"missing API key: set env var {settings.api_key_env} for provider "
            f"{settings.provider!r}"
        )
    return key
