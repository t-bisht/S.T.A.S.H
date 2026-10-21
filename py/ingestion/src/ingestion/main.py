"""Process entrypoint for the ingestion engine.

Wires the pieces together and runs the scheduler loop until a shutdown signal arrives:

1. load :class:`~ingestion.config.Config` from the environment,
2. configure dual console/file logging,
3. build the shared DB engine, token broker and Gmail client,
4. install SIGTERM/SIGINT handlers for a clean shutdown,
5. run the poll loop.

Run standalone (``python -m ingestion.main`` / ``uv run --package ingestion python -m
ingestion.main``) or as the container's entrypoint — identical either way.
"""

from __future__ import annotations

import logging
import signal
import threading

import requests

from ingestion.config import Config, ConfigError
from ingestion.db.engine import create_db_engine
from ingestion.gmail.client import GmailClient
from ingestion.logging_setup import configure_logging
from ingestion.scheduler.loop import run_loop
from ingestion.tokens.broker import UserEngineTokenBroker

__all__ = ["main"]

_SERVICE_NAME = "ingestion"

log = logging.getLogger(__name__)


def main() -> int:
    """Start the ingestion engine. Returns a process exit code.

    Returns:
        ``0`` on a clean shutdown, ``2`` if configuration is invalid.
    """
    try:
        config = Config.from_env()
    except ConfigError as exc:
        # Logging may not be configured yet — configure a minimal console logger to report.
        logging.basicConfig(level=logging.ERROR)
        logging.getLogger(_SERVICE_NAME).error("invalid configuration: %s", exc)
        return 2

    configure_logging(_SERVICE_NAME, level=config.log_level, log_dir=config.log_dir)
    log.info("starting ingestion engine")

    engine = create_db_engine(config.database_url)

    # One shared requests.Session each for the broker and Gmail — connection pooling, no
    # per-request socket churn.
    broker = UserEngineTokenBroker(
        config.user_engine_base_url,
        config.internal_svc_token,
        session=requests.Session(),
    )
    gmail = GmailClient(session=requests.Session())

    stop_event = threading.Event() #shared state object that your code can read or modify.
    _install_signal_handlers(stop_event) # set stop_event if there is SIGKILL like event. stop_Event is global thread safe variable to handle graceful shutdown

    try:
        run_loop(
            engine,
            broker,
            gmail,
            poll_interval_seconds=config.poll_interval_seconds,
            skew_seconds=config.gmail_skew_seconds,
            stop_event=stop_event,
        )
    finally:
        engine.dispose()
        log.info("ingestion engine shut down")
    return 0


def _install_signal_handlers(stop_event: threading.Event) -> None:
    """Install SIGTERM/SIGINT handlers that request a clean loop shutdown.

    Both signals simply set the stop event; the loop wakes from its interruptible wait and
    exits after the current tick, so an in-flight user scan is never torn mid-transaction.
    """

    def _handle(signum: int, _frame: object) -> None:
        log.info("received signal %s — requesting shutdown", signal.Signals(signum).name)
        stop_event.set()

    signal.signal(signal.SIGTERM, _handle) #registers a callback that Python invokes when it handles a particular operating-system signal
    signal.signal(signal.SIGINT, _handle)


if __name__ == "__main__":
    raise SystemExit(main())
