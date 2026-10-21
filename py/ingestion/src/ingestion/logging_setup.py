"""Logging configuration for the ingestion engine.

Mirrors the established art_* engine convention (``art_ops/src/commons/logging_config.py``):
a single idempotent :func:`configure_logging` installs BOTH a console ``StreamHandler`` and a
rotating file handler on the root logger, so application loggers created via
``logging.getLogger(__name__)`` emit to stdout and to a rotating file. See spec §8.

For monitorability, every log line emitted during a scan carries correlation context — a
per-run ``run_id``, the ``user_id``, and the ``scan_source_name``. :func:`run_context_logger`
wraps a base logger in an adapter that prefixes that context onto each message, so the shared
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


def configure_logging(service_name: str, *, level: str = "INFO", log_dir: str | None = None) -> None:
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
    """Prefixes ``[run=.. user=.. src=..]`` onto every message for log correlation."""

    def process(self, msg: str, kwargs: Any) -> tuple[str, Any]:
        extra = self.extra or {}
        prefix = (
            f"[run={extra.get('run_id', '-')} "
            f"user={extra.get('user_id', '-')} "
            f"src={extra.get('scan_source_name', '-')}]"
        )
        return f"{prefix} {msg}", kwargs


def run_context_logger(
    base: logging.Logger,
    *,
    run_id: str,
    user_id: str,
    scan_source_name: str,
) -> logging.LoggerAdapter:  # type: ignore[type-arg]
    """Wrap ``base`` so every line carries the run/user/source correlation context.

    Args:
        base: The underlying module logger.
        run_id: Correlation id for this source pass.
        user_id: The user being scanned.
        scan_source_name: The source's stable registry key.

    Returns:
        A :class:`logging.LoggerAdapter` that prefixes the context onto each message.
    """
    return _RunContextAdapter(
        base,
        {"run_id": run_id, "user_id": user_id, "scan_source_name": scan_source_name},
    )
