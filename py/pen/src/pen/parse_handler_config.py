"""Code-level (mechanical) knobs per parse handler (spec §6, "Code" tier).

Parse cadence, the retry/pause policy, **and each handler's LLM choice** are operational knobs,
not per-user product config, so — unlike ingestion, which keeps cadence in the DB
``scan_sources`` row — pen keeps them here, read once at boot (spec §6 "Divergence from
ingestion"). ``scan_sources`` is still the source registry; its ``scan_frequency_seconds`` is
ingestion's cadence and is **ignored** by pen.

Each handler picks its OWN provider + model (:class:`LLMSettings`): Swiggy can run on Groq while
Paytm runs on OpenRouter / NVIDIA / a locally-hosted OpenAI-compatible endpoint — no shared,
one-size provider. Only the *secret* stays in env: ``LLMSettings.api_key_env`` names the env var
holding that provider's key (the name is config, the key is the secret), resolved at build time
from :attr:`Config.api_keys`.

Add a per-source override by keying :data:`HANDLER_CONFIGS` on the ``scan_source_name``; any
source without an explicit entry falls back to :data:`DEFAULT_HANDLER_CONFIG`.
"""

from __future__ import annotations

from dataclasses import dataclass

__all__ = [
    "DEFAULT_HANDLER_CONFIG",
    "HANDLER_CONFIGS",
    "HandlerConfig",
    "LLMSettings",
    "config_for",
]


@dataclass(frozen=True, slots=True)
class LLMSettings:
    """One handler's LLM choice (spec §5). All OpenAI-compatible in v1.

    Attributes:
        provider: Label for logging (e.g. ``groq``, ``openrouter``, ``nvidia``, ``local``).
        base_url: OpenAI-compatible base URL (per provider / local host).
        model: Model string for this handler.
        api_key_env: Name of the env var holding this provider's API key — the key itself is a
            secret resolved from :attr:`Config.api_keys`; this is just its var name. Use an
            empty string for a keyless endpoint (e.g. a local model that needs no auth).
        reasoning_effort: Optional ``reasoning_effort`` hint; auto-dropped if the provider
            rejects it.
    """

    provider: str
    base_url: str
    model: str
    api_key_env: str
    reasoning_effort: str | None = None


@dataclass(frozen=True, slots=True)
class HandlerConfig:
    """Mechanical knobs for one handler's scheduler + retry policy + LLM.

    Attributes:
        interval_seconds: How often this handler's scheduler fires a pass (drains the source's
            unparsed candidates). Independent per source.
        retries: Max attempts for a transient/rate-limited LLM call before parking the row
            (spec §4.3). ``3`` by default.
        rate_limit_pause_seconds: Pause on a 429 before retrying — the free-tier window is
            per-minute, so a sub-minute retry just re-hits it (spec §4.3). ``60`` by default.
        llm: This handler's own provider + model (:class:`LLMSettings`).
    """

    llm: LLMSettings
    interval_seconds: int = 300
    retries: int = 3
    rate_limit_pause_seconds: int = 60


# ─── per-provider LLM presets (compose a handler's LLMSettings from these) ───────────────────
# Groq free endpoint, gpt-oss-20b — the v1 default (spec §5.4).
_GROQ_GPT_OSS_20B = LLMSettings(
    provider="groq",
    base_url="https://api.groq.com/openai/v1",
    model="openai/gpt-oss-20b",
    api_key_env="GROQ_API_KEY",
    reasoning_effort="low",
)

# Fallback for any enabled source without an explicit entry below (defaults to the Groq preset).
DEFAULT_HANDLER_CONFIG = HandlerConfig(llm=_GROQ_GPT_OSS_20B)

# Per-source overrides, keyed by scan_sources.scan_source_name. Each may pick a DIFFERENT
# provider/model — that is the whole point of per-handler LLM settings.
HANDLER_CONFIGS: dict[str, HandlerConfig] = {
    "swiggy_instamart": HandlerConfig(
        llm=_GROQ_GPT_OSS_20B,
        interval_seconds=300,
        retries=3,
        rate_limit_pause_seconds=60,
    ),
    # Example of a future source on a different provider (uncomment + add its scan_sources row):
    # "paytm": HandlerConfig(
    #     llm=LLMSettings(
    #         provider="openrouter",
    #         base_url="https://openrouter.ai/api/v1",
    #         model="meta-llama/llama-3.1-70b-instruct",
    #         api_key_env="OPENROUTER_API_KEY",
    #     ),
    #     interval_seconds=600,
    # ),
}


def config_for(scan_source_name: str) -> HandlerConfig:
    """Return the handler config for a source, or the default if none is registered."""
    return HANDLER_CONFIGS.get(scan_source_name, DEFAULT_HANDLER_CONFIG)
