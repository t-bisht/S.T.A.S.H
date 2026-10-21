"""Environment bootstrap for the ingestion engine.

Config here holds ONLY what the engine needs to *reach* its dependencies (the DB, the token
broker) and secrets. All scan *behaviour* — what to scan, how often, who is enrolled —
lives in the database (``scan_sources`` / ``user_scan_preferences``), never here. See spec §6.

Everything is read once at startup via :meth:`Config.from_env` and passed down explicitly;
no module reads ``os.environ`` on its own.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from urllib.parse import quote_plus

__all__ = ["Config", "ConfigError"]

# How often the scheduler re-reads scan_sources from the DB. This is a mechanical knob
# (not product config), so it is an env override with a hardcoded default, not a DB column.
_DEFAULT_POLL_INTERVAL_SECONDS = 60

# Clock-skew / late-indexing margin subtracted from the watermark when building the Gmail
# `after:` bound, so a mail indexed slightly after our last run is not missed. See spec §7.3.
_DEFAULT_GMAIL_SKEW_SECONDS = 300


class ConfigError(RuntimeError):
    """Raised when required configuration is missing or malformed at startup."""


@dataclass(frozen=True, slots=True)
class Config:
    """Immutable runtime configuration, assembled once from the environment.

    Attributes:
        database_url: SQLAlchemy URL for the shared STASH Postgres DB
            (``postgresql+psycopg://user:pass@host:port/dbname``).
        user_engine_base_url: Base URL of the ``user_engine`` token broker, e.g.
            ``http://localhost:7083``.
        internal_svc_token: Shared secret presented as the ``X-Internal-Auth`` header on
            ``user_engine`` ``/internal/**`` calls. Secret — never logged.
        log_dir: Directory for the rotating log file.
        log_level: Root log level name (e.g. ``INFO``).
        poll_interval_seconds: How often the scheduler re-reads ``scan_sources``.
        gmail_skew_seconds: Margin subtracted from the watermark for the Gmail ``after:`` bound.
    """

    database_url: str
    user_engine_base_url: str
    internal_svc_token: str
    log_dir: str
    log_level: str
    poll_interval_seconds: int
    gmail_skew_seconds: int

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> Config:
        """Build a :class:`Config` from environment variables.

        Accepts either a single ``DATABASE_URL`` or the discrete ``DB_*`` parts (the parts
        win only when ``DATABASE_URL`` is absent). ``USER_ENGINE_BASE_URL`` and
        ``INTERNAL_SVC_TOKEN`` are required; the rest have sensible defaults.

        Args:
            env: Environment mapping to read; defaults to :data:`os.environ`.

        Returns:
            A fully-populated, immutable :class:`Config`.

        Raises:
            ConfigError: If a required variable is missing or a numeric var is non-integer.
        """
        env = dict(os.environ if env is None else env)

        database_url = _resolve_database_url(env)

        user_engine_base_url = env.get("USER_ENGINE_BASE_URL", "").strip().rstrip("/")
        if not user_engine_base_url:
            raise ConfigError("USER_ENGINE_BASE_URL is required")

        internal_svc_token = env.get("INTERNAL_SVC_TOKEN", "").strip()
        if not internal_svc_token:
            raise ConfigError("INTERNAL_SVC_TOKEN is required (X-Internal-Auth shared secret)")

        return cls(
            database_url=database_url,
            user_engine_base_url=user_engine_base_url,
            internal_svc_token=internal_svc_token,
            log_dir=env.get("LOG_DIR", "logs"),
            log_level=env.get("LOG_LEVEL", "INFO").upper(),
            poll_interval_seconds=_int_env(
                env, "POLL_INTERVAL_SECONDS", _DEFAULT_POLL_INTERVAL_SECONDS
            ),
            gmail_skew_seconds=_int_env(env, "GMAIL_SKEW_SECONDS", _DEFAULT_GMAIL_SKEW_SECONDS),
        )


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
