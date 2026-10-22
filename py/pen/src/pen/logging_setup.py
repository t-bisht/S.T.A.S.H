"""Logging configuration for the parse engine (spec §12).

Same convention as ingestion §8: a single idempotent :func:`configure_logging` installs BOTH a
console ``StreamHandler`` and a rotating file handler (``${LOG_DIR}/pen.log``, 10 MB × 5) on the
root logger, so loggers created via ``logging.getLogger(__name__)`` emit to stdout and to the
rotating file.

Every log line emitted during a pass carries correlation context — a per-run ``run_id``, the
``scan_source_name``, and (per row) the ``user_id`` / ``message_id``. :func:`run_context_logger`
wraps a base logger in an adapter that prefixes that context onto each message; the shared
format string stays untouched while the context still appears on every line.
"""

from __future__ import annotations

import logging
import os
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any

__all__ = ["configure_logging", "run_context_logger"]

_FORMAT = "%(asctime)s %(levelname)s %(name)s — %(message)s"
_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
_MAX_BYTES = 10 * 1024 * 1024  # 10 MB
_BACKUP_COUNT = 5

# Guards against duplicate handler installation on repeat calls (e.g. test reloads).
_CONFIGURED: set[str] = set()


def configure_logging(
    service_name: str, *, level: str = "INFO", log_dir: str | None = None
) -> None:
    """Install console + rotating-file handlers on the root logger. Idempotent.

    Call exactly once from the process entrypoint. Repeat calls for the same service are
    no-ops, so reloads never duplicate handlers.

    Args:
        service_name: Logical service name; also the log file basename
            (``<log_dir>/<service_name>.log``).
        level: Root log level name (e.g. ``INFO``, ``DEBUG``).
        log_dir: Directory for the log file. Defaults to the ``LOG_DIR`` env var, else ``logs``.
    """
    if service_name in _CONFIGURED:
        return

    formatter = logging.Formatter(_FORMAT, datefmt=_DATE_FORMAT)

    console = logging.StreamHandler()
    console.setFormatter(formatter)

    directory = Path(log_dir if log_dir is not None else os.environ.get("LOG_DIR", "logs"))
    directory.mkdir(parents=True, exist_ok=True)
    file_handler = RotatingFileHandler(
        directory / f"{service_name}.log",
        maxBytes=_MAX_BYTES,
        backupCount=_BACKUP_COUNT,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    root = logging.getLogger()
    root.setLevel(getattr(logging, level.upper(), logging.INFO))
    root.addHandler(console)
    root.addHandler(file_handler)

    _CONFIGURED.add(service_name)


class _RunContextAdapter(logging.LoggerAdapter):  # type: ignore[type-arg]
    """Prefixes ``[run=.. src=.. user=.. msg=..]`` onto every message for log correlation."""

    def process(self, msg: str, kwargs: Any) -> tuple[str, Any]:
        extra = self.extra or {}
        prefix = (
            f"[run={extra.get('run_id', '-')} "
            f"src={extra.get('scan_source_name', '-')} "
            f"user={extra.get('user_id', '-')} "
            f"msg={extra.get('message_id', '-')}]"
        )
        return f"{prefix} {msg}", kwargs


def run_context_logger(
    base: logging.Logger,
    *,
    run_id: str,
    scan_source_name: str,
    user_id: str = "-",
    message_id: str = "-",
) -> logging.LoggerAdapter:  # type: ignore[type-arg]
    """Wrap ``base`` so every line carries the run/source/(user/message) correlation context.

    Used two ways: at pass level (user/message default to ``-``), and rebound per candidate row
    with that row's ``user_id`` / ``message_id`` via :meth:`bind`-style re-creation.

    Args:
        base: The underlying module logger.
        run_id: Correlation id for this source pass.
        scan_source_name: The source's stable registry key.
        user_id: The row's user, when known.
        message_id: The row's Gmail message id, when known.

    Returns:
        A :class:`logging.LoggerAdapter` that prefixes the context onto each message.
    """
    return _RunContextAdapter(
        base,
        {
            "run_id": run_id,
            "scan_source_name": scan_source_name,
            "user_id": user_id,
            "message_id": message_id,
        },
    )
