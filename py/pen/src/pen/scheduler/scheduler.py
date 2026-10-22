"""One scheduler per handler (spec §2.1 tier 2, §4.1).

Each scheduler runs on its own thread and fires its handler on its own interval
(``parse_handler_config``), so one slow or failing source never stalls another. A plain
``while not stop`` + interruptible wait keeps v1 single-process and dependency-light (spec §4
allows this over APScheduler).

Per tick the scheduler assembles the pass context — building the taxonomy maps *fresh* so a
taxonomy edit is picked up without a restart — runs the handler, and writes the per-source
run-level metadata (spec §4.5): ``SUCCESS`` when the pass completes (even with parked rows),
``FAILED`` only when the pass could not start/complete (taxonomy load broken, DB down, …).
"""

from __future__ import annotations

import logging
import threading
from datetime import UTC, datetime
from uuid import uuid4

from sqlalchemy.engine import Engine

from pen.core.context import ParseContext
from pen.core.handler import ParseHandler, RunSummary
from pen.db import categories_repo, run_metadata_repo
from pen.db.scan_sources_repo import ScanSource
from pen.logging_setup import run_context_logger
from pen.parse_handler_config import HandlerConfig

__all__ = ["Scheduler"]

log = logging.getLogger(__name__)


class Scheduler(threading.Thread):
    """Fires one handler on a fixed interval until the shared stop event is set."""

    def __init__(
        self,
        *,
        engine: Engine,
        source: ScanSource,
        handler: ParseHandler,
        config: HandlerConfig,
        stop_event: threading.Event,
    ) -> None:
        """Create the scheduler thread.

        Args:
            engine: Shared DB engine.
            source: The source this scheduler drives (id + name).
            handler: The source's handler instance (owns its own LLM provider).
            config: Mechanical knobs (interval, retries, pause).
            stop_event: Set on shutdown; interrupts the inter-tick wait and in-pass pauses.
        """
        super().__init__(name=f"pen-scheduler-{source.scan_source_name}", daemon=True)
        self._engine = engine
        self._source = source
        self._handler = handler
        self._config = config
        self._stop_event = stop_event

    def run(self) -> None:
        """Thread body: tick, then interruptible wait, until stop is requested."""
        log.info(
            "scheduler starting src=%s interval=%ds",
            self._source.scan_source_name,
            self._config.interval_seconds,
        )
        while not self._stop_event.is_set():
            try:
                self._tick()
            except Exception:
                # A tick must never kill the scheduler thread — log and retry next interval.
                log.exception("scheduler tick crashed src=%s", self._source.scan_source_name)
            self._stop_event.wait(self._config.interval_seconds)
        log.info("scheduler stopped src=%s", self._source.scan_source_name)

    def _tick(self) -> None:
        """Run one pass: build context, run the handler, record run-level metadata (§4.5)."""
        run_id = uuid4().hex[:8]
        rlog = run_context_logger(
            log, run_id=run_id, scan_source_name=self._source.scan_source_name
        )
        now = datetime.now(UTC)

        try:
            # Build the taxonomy maps fresh each pass (picks up seed edits without a restart).
            with self._engine.connect() as conn:
                cat_maps = categories_repo.load(conn)

            ctx = ParseContext(
                scan_source_id=self._source.scan_source_id,
                source_table="",  # handlers know their own table; kept for forward-compat.
                engine=self._engine,
                cat_maps=cat_maps,
                run_id=run_id,
                log=rlog,
                stop_event=self._stop_event,
            )
            summary = self._handler.run(ctx)

            with self._engine.begin() as conn:
                run_metadata_repo.upsert_success(conn, self._source.scan_source_id, now)
            self._log_summary(summary)

        except Exception as exc:  # noqa: BLE001 - pass couldn't start/complete → FAILED (§4.5).
            rlog.exception("pass could not complete — recording FAILED run")
            self._record_failed(self._source.scan_source_id, str(exc)[:500], rlog)

    def _record_failed(
        self,
        scan_source_id: int,
        reason: str,
        rlog: logging.LoggerAdapter,  # type: ignore[type-arg]
    ) -> None:
        """Write the FAILED metadata row, swallowing any secondary error."""
        try:
            with self._engine.begin() as conn:
                run_metadata_repo.upsert_failure(conn, scan_source_id, reason)
        except Exception:  # noqa: BLE001
            rlog.exception("failed to record FAILED run metadata")

    def _log_summary(self, summary: RunSummary) -> None:
        """Emit the single per-run summary line (spec §12)."""
        log.info(
            "pass summary src=%s run=%s candidates=%d parsed=%d parked=%d persist_errors=%d "
            "parked_by_reason=%s",
            summary.scan_source_name,
            summary.run_id,
            summary.candidates,
            summary.parsed,
            summary.parked,
            summary.persist_errors,
            summary.parked_by_reason or "{}",
        )
