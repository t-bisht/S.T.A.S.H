"""The Swiggy sub-orchestrator: scan → parse → persist, one row per transaction (spec §4.3, §11.2).

Wires the three stages. Per candidate row:

1. ``parse`` through the shared retry/pause wrapper (3× transient, 429 → 60s pause),
2. on success, ``persist`` in a single transaction (order + items + source writeback),
3. on a terminal failure, **park** the row in ``parse_failures`` with the right reason and move
   on — a single bad row never stops the pass (the MVP no-batch-stop contract).

A row that *parses* but fails to *persist* (e.g. a transient DB error) is NOT parked: it is
left ``has_parsed=false`` so the next pass retries it — parking a parseable row would wrongly
exclude it forever. Run-level metadata is written by the scheduler, not here (spec §4.5).
"""

from __future__ import annotations

import logging

from pen.core.context import ParseContext
from pen.core.handler import ParseHandler, RunSummary
from pen.core.retry import run_with_retry
from pen.db import parse_failures_repo as failures
from pen.handlers.swiggy import parser, persister, scanner
from pen.handlers.swiggy.schema import ScanRow, SwiggyValidationError
from pen.llm.base import LLMProvider
from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid
from pen.logging_setup import run_context_logger

__all__ = ["SwiggyHandler"]

log = logging.getLogger(__name__)


class SwiggyHandler(ParseHandler):
    """Parse handler for Swiggy Instamart delivered-order mails."""

    source_name = "swiggy_instamart"

    def __init__(
        self,
        llm: LLMProvider,
        *,
        retries: int = 3,
        rate_limit_pause_seconds: int = 60,
    ) -> None:
        """Create the handler with its OWN provider + retry/pause policy.

        Args:
            llm: This handler's LLM provider (built from its own
                :class:`~pen.parse_handler_config.LLMSettings`; see spec §5, per-handler
                providers). Swiggy may use a different provider/model than Paytm.
            retries: Max attempts for a transient/rate-limited parse before parking.
            rate_limit_pause_seconds: Pause before retrying a 429.
        """
        self._llm = llm
        self._retries = retries
        self._rate_limit_pause_seconds = rate_limit_pause_seconds

    def run(self, ctx: ParseContext) -> RunSummary:
        """Drain the source's candidates (spec §4.3). See :meth:`ParseHandler.run`."""
        summary = RunSummary(scan_source_name=self.source_name, run_id=ctx.run_id)

        # Candidate read is its own short-lived connection (separate from the per-row writes).
        with ctx.engine.connect() as conn:
            candidates = scanner.select_candidates(conn)
        summary.candidates = len(candidates)
        ctx.log.info("candidates selected: %d", len(candidates))

        for row in candidates:
            if ctx.stop_event.is_set():
                ctx.log.info("shutdown requested — stopping drain after %d parsed", summary.parsed)
                break
            self._process_row(ctx, row, summary)

        return summary

    def _process_row(self, ctx: ParseContext, row: ScanRow, summary: RunSummary) -> None:
        """Parse + persist one row, or park it, never letting the failure escape the pass."""
        rlog = run_context_logger(
            log,
            run_id=ctx.run_id,
            scan_source_name=self.source_name,
            user_id=str(row.user_id),
            message_id=row.message_id,
        )

        try:
            parsed = run_with_retry(
                lambda: parser.parse(self._llm, ctx.cat_maps, row, rlog),
                retries=self._retries,
                rate_limit_pause_seconds=self._rate_limit_pause_seconds,
                stop_event=ctx.stop_event,
                log=rlog,
            )
        except LLMRateLimited:
            self._park(ctx, row, failures.REASON_RATE_LIMIT, rlog, summary)
            return
        except LLMResponseInvalid as exc:
            self._park(ctx, row, failures.REASON_INVALID_JSON, rlog, summary, llm_raw=exc.raw)
            return
        except SwiggyValidationError as exc:
            self._park(ctx, row, exc.reason, rlog, summary, llm_raw=exc.llm_raw)
            return
        except LLMError as exc:
            rlog.warning("LLM error after retries: %s", exc)
            self._park(ctx, row, failures.REASON_LLM_ERROR, rlog, summary)
            return

        # Parsed OK → persist atomically. A persist failure is transient: do NOT park (the row
        # stays has_parsed=false and is retried next pass).
        try:
            with ctx.engine.begin() as conn:
                persister.persist(conn, ctx, parsed, row)
        except Exception:  # noqa: BLE001 - isolation boundary: one bad write never stops the pass.
            rlog.exception("persist failed (will retry next pass): source_row_id=%d", row.id)
            summary.persist_errors += 1
            return

        summary.parsed += 1
        rlog.info(
            "parsed + persisted order_id=%s items=%d source_row_id=%d",
            parsed.order_id,
            len(parsed.items),
            row.id,
        )

    def _park(
        self,
        ctx: ParseContext,
        row: ScanRow,
        reason: str,
        rlog: logging.LoggerAdapter,  # type: ignore[type-arg]
        summary: RunSummary,
        *,
        llm_raw: str | None = None,
    ) -> None:
        """Park a row in ``parse_failures`` (its own transaction) and tally it (spec §4.4)."""
        try:
            with ctx.engine.begin() as conn:
                failures.park(
                    conn,
                    user_id=row.user_id,
                    source_table=scanner.SOURCE_TABLE,
                    source_row_id=row.id,
                    reason=reason,
                    message_id=row.message_id,
                    llm_raw=llm_raw,
                )
        except Exception:  # noqa: BLE001 - recording a park must never abort the pass.
            rlog.exception("failed to park row source_row_id=%d (reason=%s)", row.id, reason)
            return
        summary.record_park(reason)
        rlog.warning("parked source_row_id=%d reason=%s", row.id, reason)
