"""Per-source runner: iterate enrolled users, each scanned in strict isolation.

The runner owns all cross-source framework logic (spec §4, §10): resolving each user's
watermark, assembling the :class:`~ingestion.sources.base.ScanContext`, invoking the handler,
and writing ``source_scan_run_metadata`` on every attempt. The **critical** guarantee is
per-user isolation — one user's failure (dead token, ``login_required``, Gmail 5xx, DB or
mapping error) is caught, logged, recorded, and never aborts the pass for other users.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy.engine import Engine

from ingestion.db import preferences_repo, run_metadata_repo
from ingestion.db.scan_sources_repo import ScanSource
from ingestion.gmail.client import GmailClient
from ingestion.logging_setup import run_context_logger
from ingestion.scheduler.due import is_due
from ingestion.sources.base import ScanContext, SourceHandler
from ingestion.sources.registry import HANDLERS
from ingestion.tokens.broker import TokenBroker
from ingestion.tokens.exceptions import ReauthRequired, TokenError

__all__ = ["PassSummary", "run_source_pass"]

log = logging.getLogger(__name__)


@dataclass(slots=True)
class PassSummary:
    """Tally of one source pass, emitted as the per-run summary line (spec §8)."""

    scan_source_name: str
    run_id: str
    users_total: int = 0
    users_scanned: int = 0
    users_skipped: int = 0
    users_failed: int = 0
    matched: int = 0
    fetched: int = 0
    stored: int = 0
    failures_by_reason: dict[str, int] = field(default_factory=dict)

    def record_failure(self, reason: str) -> None:
        self.users_failed += 1
        self.failures_by_reason[reason] = self.failures_by_reason.get(reason, 0) + 1


def run_source_pass(
    engine: Engine,
    broker: TokenBroker,
    gmail: GmailClient,
    source: ScanSource,
    *,
    skew_seconds: int,
) -> PassSummary:
    """Run one pass of a source over all its enrolled, due users.

    Args:
        engine: Shared DB engine.
        broker: Token broker.
        gmail: Shared Gmail client.
        source: The source config row driving this pass.
        skew_seconds: ``after:`` skew margin handed to the handler.

    Returns:
        A :class:`PassSummary` of the pass (also logged as a one-line summary).
    """
    run_id = uuid4().hex[:8]
    summary = PassSummary(scan_source_name=source.scan_source_name, run_id=run_id)

    handler = _resolve_handler(source, gmail, skew_seconds=skew_seconds)
    if handler is None:
        return summary  # unknown source already logged; skip without crashing the loop.

    with engine.connect() as conn:
        # find user IDs that want o scan the specific source
        user_ids = preferences_repo.list_enrolled_user_ids(conn, source.scan_source_id)
    summary.users_total = len(user_ids)

    for user_id in user_ids:
        rlog = run_context_logger(
            log,
            run_id=run_id,
            user_id=str(user_id),
            scan_source_name=source.scan_source_name,
        )
        # start scanning source per user
        _scan_one_user(engine, broker, handler, source, user_id, run_id, rlog, summary)

    _log_summary(summary)
    return summary


def _resolve_handler(
    source: ScanSource,
    gmail: GmailClient,
    *,
    skew_seconds: int,
) -> SourceHandler | None:
    """Instantiate the handler for a source, or ``None`` if its name is unregistered.

    An unknown ``scan_source_name`` is a clear misconfiguration (a DB row with no matching
    code). It is logged as a warning and skipped — never a crash (spec §2).
    """
    handler_cls = HANDLERS.get(source.scan_source_name)
    if handler_cls is None:
        log.warning(
            "no handler registered for scan_source_name=%r (scan_source_id=%d) — skipping pass",
            source.scan_source_name,
            source.scan_source_id,
        )
        return None
    # v1 handlers are all Gmail-backed and share this constructor shape.
    return handler_cls(gmail, skew_seconds=skew_seconds)


def _scan_one_user(
    engine: Engine,
    broker: TokenBroker,
    handler: SourceHandler,
    source: ScanSource,
    user_id: UUID,
    run_id: str,
    rlog: logging.LoggerAdapter,  # type: ignore[type-arg]
    summary: PassSummary,
) -> None:
    """Scan a single user inside its own transaction and try/except boundary.

    On success, the handler's inserts and the watermark advance commit together. On any
    failure the work transaction rolls back and a FAILED metadata row is written in a separate
    transaction (watermark untouched, so the next run safely retries the window).
    """
    try:
        now = datetime.now(UTC)
        with engine.begin() as conn:
            meta = run_metadata_repo.get(conn, user_id, source.scan_source_id)
            last_scan_time = meta.last_scan_time if meta else None

            if not is_due(last_scan_time, source.scan_frequency_seconds, now):
                rlog.debug("not due yet — skipping")
                summary.users_skipped += 1
                return

            since = _resolve_since(last_scan_time, source, now)
            rlog.info("scanning since=%s (first_run=%s)", since.isoformat(), last_scan_time is None)

            ctx = ScanContext(
                user_id=user_id,
                scan_source_id=source.scan_source_id,
                since=since,
                broker=broker,
                db=conn,
                run_id=run_id,
                log=rlog,
            )
            result = handler.scan(ctx)

            # Advance the watermark to the run START so no mail that arrived mid-run is skipped.
            run_metadata_repo.upsert_success(conn, user_id, source.scan_source_id, now)

        summary.users_scanned += 1
        summary.matched += result.matched
        summary.fetched += result.fetched
        summary.stored += result.stored

    except ReauthRequired:
        # Expected, recoverable-by-the-user condition — record it, don't dump a stack trace.
        rlog.warning("re-auth required (login_required) — stopping this user's scan")
        _record_failure(engine, source, user_id, "login_required", rlog)
        summary.record_failure("login_required")

    except TokenError as exc:
        rlog.warning("token error (%s) — user scan failed", exc.code)
        _record_failure(engine, source, user_id, exc.code, rlog)
        summary.record_failure(exc.code)

    except Exception as exc:  # noqa: BLE001 - isolation boundary: nothing escapes per user.
        # Unexpected failure — log with full stack trace, record, and move on.
        rlog.exception("unexpected error during scan: %s", exc)
        reason = type(exc).__name__
        _record_failure(engine, source, user_id, reason, rlog)
        summary.record_failure(reason)


def _resolve_since(
    last_scan_time: datetime | None,
    source: ScanSource,
    now: datetime,
) -> datetime:
    """Resolve the scan lower bound: the watermark, or the init-range lookback on a first run."""
    if last_scan_time is not None:
        return last_scan_time
    return now - timedelta(seconds=source.scan_init_range_seconds)


def _record_failure(
    engine: Engine,
    source: ScanSource,
    user_id: UUID,
    reason: str,
    rlog: logging.LoggerAdapter,  # type: ignore[type-arg]
) -> None:
    """Write a FAILED metadata row in its own transaction, swallowing any secondary error.

    Recording the failure must itself never abort the pass, so a DB error here is logged and
    contained rather than propagated.
    """
    try:
        with engine.begin() as conn:
            run_metadata_repo.upsert_failure(conn, user_id, source.scan_source_id, reason)
    except Exception:  # noqa: BLE001 - never let failure-recording abort the pass.
        rlog.exception("failed to record FAILED metadata (reason=%s)", reason)


def _log_summary(summary: PassSummary) -> None:
    """Emit the single per-run summary line for the pass."""
    log.info(
        "pass summary src=%s run=%s users_total=%d scanned=%d skipped=%d failed=%d "
        "matched=%d fetched=%d stored=%d failures=%s",
        summary.scan_source_name,
        summary.run_id,
        summary.users_total,
        summary.users_scanned,
        summary.users_skipped,
        summary.users_failed,
        summary.matched,
        summary.fetched,
        summary.stored,
        summary.failures_by_reason or "{}",
    )
