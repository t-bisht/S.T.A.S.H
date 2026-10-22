"""Process entrypoint for the parse engine (spec §4.1, §9 task 9).

Wires the pieces and runs until a shutdown signal arrives:

1. load :class:`~pen.config.Config` from the environment,
2. configure dual console/file logging,
3. build the shared DB engine,
4. install SIGTERM/SIGINT handlers for a clean shutdown,
5. start the orchestrator (one scheduler per enabled source — each handler builds its OWN LLM
   provider from its per-handler settings) and block until stop.

Run standalone (``python -m pen.main`` / ``uv run --package pen python -m pen.main``) or as the
container entrypoint — identical either way.
"""

from __future__ import annotations

import logging
import signal
import threading

from pen.config import Config, ConfigError
from pen.db.engine import create_db_engine
from pen.logging_setup import configure_logging
from pen.orchestrator import Orchestrator

__all__ = ["main"]

_SERVICE_NAME = "pen"

log = logging.getLogger(__name__)


def main() -> int:
    """Start the parse engine. Returns a process exit code (``0`` clean, ``2`` bad config)."""
    try:
        config = Config.from_env()
    except ConfigError as exc:
        logging.basicConfig(level=logging.ERROR)
        logging.getLogger(_SERVICE_NAME).error("invalid configuration: %s", exc)
        return 2

    configure_logging(_SERVICE_NAME, level=config.log_level, log_dir=config.log_dir)
    log.info("starting pen (content parse engine)")

    engine = create_db_engine(config.database_url)

    stop_event = threading.Event()
    _install_signal_handlers(stop_event)

    orchestrator = Orchestrator(engine, config, stop_event)
    try:
        orchestrator.run()
    finally:
        engine.dispose()
        log.info("pen shut down")
    return 0


def _install_signal_handlers(stop_event: threading.Event) -> None:
    """Install SIGTERM/SIGINT handlers that request a clean shutdown.

    Both signals set the stop event; the orchestrator wakes from its wait and the schedulers
    each exit after the current row's transaction — never mid-write (spec §4.1).
    """

    def _handle(signum: int, _frame: object) -> None:
        log.info("received signal %s — requesting shutdown", signal.Signals(signum).name)
        stop_event.set()

    signal.signal(signal.SIGTERM, _handle)
    signal.signal(signal.SIGINT, _handle)


if __name__ == "__main__":
    raise SystemExit(main())
