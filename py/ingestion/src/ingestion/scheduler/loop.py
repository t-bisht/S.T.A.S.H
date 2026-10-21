"""The long-running scheduler poll loop.

On a fixed poll interval (how often we re-read the DB — a mechanical knob, not product config)
the loop reads all **enabled** ``scan_sources`` fresh and dispatches each to the per-source
runner, which decides per-user due-ness and isolation. Reading sources fresh every tick means
new or edited sources (cadence, enable/disable, enrolments) are picked up without a restart.

A plain ``while not stop`` + interruptible wait keeps v1 single-process and dependency-light
(spec §4 explicitly allows this over APScheduler). The loop is stopped cleanly by setting the
injected :class:`threading.Event`, which :mod:`ingestion.main` wires to SIGTERM/SIGINT.
"""

from __future__ import annotations

import logging
import threading

from sqlalchemy.engine import Engine

from ingestion.db import scan_sources_repo
from ingestion.gmail.client import GmailClient
from ingestion.scheduler.runner import run_source_pass
from ingestion.tokens.broker import TokenBroker

__all__ = ["run_loop"]

log = logging.getLogger(__name__)


def run_loop(
        engine: Engine,
        broker: TokenBroker,
        gmail: GmailClient,
        *,
        poll_interval_seconds: int,
        skew_seconds: int,
        stop_event: threading.Event,
) -> None:
    """Run the poll loop until ``stop_event`` is set.

    Args:
        engine: Shared DB engine.
        broker: Token broker.
        gmail: Shared Gmail client.
        poll_interval_seconds: Seconds between re-reads of ``scan_sources``.
        skew_seconds: ``after:`` skew margin passed through to handlers.
        stop_event: Set by the entrypoint's signal handler to request a clean shutdown; the
            loop finishes the current tick's dispatch, then exits at the next wait.
    """
    log.info("scheduler loop starting (poll_interval=%ds)", poll_interval_seconds)
    while not stop_event.is_set():
        try:
            _tick(engine, broker, gmail, skew_seconds=skew_seconds)
        except Exception:
            # An error reading scan_sources (e.g. DB blip) is logged; we retry next tick.
            log.exception("scheduler tick failed; will retry next poll")
        # Interruptible sleep: wakes immediately when stop is requested.
        stop_event.wait(poll_interval_seconds)
    log.info("scheduler loop stopped")


def _tick(
        engine: Engine,
        broker: TokenBroker,
        gmail: GmailClient,
        *,
        skew_seconds: int,
) -> None:
    """Read enabled sources fresh and run one pass of each."""
    with engine.connect() as conn:
        sources = scan_sources_repo.list_enabled(conn)

    if not sources:
        log.debug("no enabled scan sources this tick")
        return

    for source in sources:
        # scan starting for every sources
        run_source_pass(engine, broker, gmail, source, skew_seconds=skew_seconds)
