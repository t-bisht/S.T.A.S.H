"""Environment bootstrap for the parse engine (spec §6, "Env" tier).

Config here holds ONLY what the engine needs to *reach* its dependencies — the shared DB — plus
the LLM **secrets** (API keys). It deliberately does NOT hold a single global LLM provider /
model: each handler picks its own provider + model in the code tier
(:mod:`pen.parse_handler_config`), because different sources may want different providers (e.g.
Swiggy on Groq, Paytm on OpenRouter / NVIDIA / a locally-hosted model). Only the *secret* lives
in env; which provider/model/endpoint a handler uses is code config, and the secret is looked up
by the env-var *name* its :class:`~pen.parse_handler_config.LLMSettings` declares.

Every API-key env var (anything whose name ends in ``_API_KEY``, plus the legacy ``LLM_API_KEY``)
is captured once here into :attr:`Config.api_keys`, so no other module reads ``os.environ`` for
secrets on its own. Everything about *what* to parse and *which* sources exist lives in the DB
(``scan_sources``).
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from types import MappingProxyType
from urllib.parse import quote_plus

__all__ = ["Config", "ConfigError"]


class ConfigError(RuntimeError):
    """Raised when required configuration is missing or malformed at startup."""


@dataclass(frozen=True, slots=True)
class Config:
    """Immutable runtime configuration, assembled once from the environment.

    Attributes:
        database_url: SQLAlchemy psycopg URL for the shared STASH Postgres DB
            (``postgresql+psycopg://user:pass@host:port/dbname``).
        api_keys: Read-only map of LLM secret env vars (name → value) — e.g.
            ``{"GROQ_API_KEY": "gsk_…", "OPENROUTER_API_KEY": "sk_…"}``. A handler's
            :class:`~pen.parse_handler_config.LLMSettings` names which key it needs; the factory
            resolves it here. **Secrets — never logged.**
        log_dir: Directory for the rotating log file.
        log_level: Root log level name (e.g. ``INFO``).
    """

    database_url: str
    api_keys: Mapping[str, str]
    log_dir: str
    log_level: str

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> Config:
        """Build a :class:`Config` from environment variables.

        Accepts either a single ``DATABASE_URL`` or the discrete ``DB_*`` parts (the parts win
        only when ``DATABASE_URL`` is absent). No global LLM key is required here — each handler
        validates its own key at provider-build time (spec §5; per-handler providers).

        Args:
            env: Environment mapping to read; defaults to :data:`os.environ`.

        Returns:
            A fully-populated, immutable :class:`Config`.

        Raises:
            ConfigError: If a required variable is missing or a numeric var is non-integer.
        """
        env = dict(os.environ if env is None else env)

        database_url = _resolve_database_url(env)
        api_keys = _collect_api_keys(env)

        return cls(
            database_url=database_url,
            api_keys=api_keys,
            log_dir=env.get("LOG_DIR", "logs"),
            log_level=env.get("LOG_LEVEL", "INFO").upper(),
        )


def _collect_api_keys(env: dict[str, str]) -> Mapping[str, str]:
    """Capture every LLM secret env var once, as a read-only map (name → value).

    Matches any var whose name ends in ``_API_KEY`` (e.g. ``GROQ_API_KEY``,
    ``OPENROUTER_API_KEY``, ``NVIDIA_API_KEY``) plus the generic ``LLM_API_KEY``. Blank values
    are dropped so a missing key surfaces clearly at build time rather than as an empty string.
    """
    keys = {
        name: value.strip()
        for name, value in env.items()
        if (name.endswith("_API_KEY") or name == "LLM_API_KEY") and value.strip()
    }
    return MappingProxyType(keys)


def _resolve_database_url(env: dict[str, str]) -> str:
    """Return a SQLAlchemy psycopg URL from ``DATABASE_URL`` or the discrete ``DB_*`` parts.

    A bare ``DATABASE_URL`` of the ``postgresql://`` form is normalised to the
    ``postgresql+psycopg`` driver so SQLAlchemy uses psycopg v3.
    """
    raw = env.get("DATABASE_URL", "").strip()
    if raw:
        if raw.startswith("postgresql://"):
            raw = raw.replace("postgresql://", "postgresql+psycopg://", 1)
        return raw

    required = ("DB_HOST", "DB_NAME", "DB_USER", "DB_PASSWORD")
    missing = [key for key in required if not env.get(key, "").strip()]
    if missing:
        raise ConfigError(
            "DB connection not configured: set DATABASE_URL, or all of "
            f"DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD (missing: {', '.join(missing)})"
        )

    host = env["DB_HOST"].strip()
    port = _int_env(env, "DB_PORT", 5432)
    name = env["DB_NAME"].strip()
    # URL-encode credentials so special characters in a password don't break the DSN.
    user = quote_plus(env["DB_USER"].strip())
    password = quote_plus(env["DB_PASSWORD"].strip())
    return f"postgresql+psycopg://{user}:{password}@{host}:{port}/{name}"


def _int_env(env: dict[str, str], key: str, default: int) -> int:
    """Read an integer env var, falling back to ``default`` when unset/blank.

    Raises:
        ConfigError: If the value is present but not a valid integer.
    """
    raw = env.get(key, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ConfigError(f"{key} must be an integer, got {raw!r}") from exc
