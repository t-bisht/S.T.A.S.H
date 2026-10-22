"""Repository for ``parse_scan_run_metadata`` — per-source run-level health (spec §4.5).

ONE row per source (keyed by ``scan_source_id``), written once per pass. A pass that parsed
some rows and parked others is still ``SUCCESS`` — it did its job. ``FAILED`` is only for a
pass that could not start (DB down, taxonomy load broken, LLM auth bad → zero rows processed).
Row-level failures live in ``parse_failures``, never here (spec §4.5).

Unlike ingestion's metadata, there is no watermark: the correctness gate is
``has_parsed=false`` + the ``parse_failures`` anti-join (spec §4.2), so ``last_run_time`` is
bookkeeping/health only.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.engine import Connection

from pen.db.models import parse_scan_run_metadata

__all__ = ["upsert_failure", "upsert_success"]

_STATUS_SUCCESS = "SUCCESS"
_STATUS_FAILED = "FAILED"


def upsert_success(conn: Connection, scan_source_id: int, last_run_time: datetime) -> None:
    """Record a completed pass: ``last_status=SUCCESS``, ``last_run_time=now``, error cleared.

    Args:
        conn: Live DB connection (inside the caller's transaction).
        scan_source_id: The source whose pass completed.
        last_run_time: The pass's timestamp (health bookkeeping; not a correctness watermark).
    """
    _upsert(
        conn,
        scan_source_id=scan_source_id,
        status=_STATUS_SUCCESS,
        last_error=None,
        last_run_time=last_run_time,
        advance_run_time=True,
    )


def upsert_failure(conn: Connection, scan_source_id: int, last_error: str) -> None:
    """Record a pass that could not start/complete: ``last_status=FAILED`` with a reason.

    ``last_run_time`` is left untouched so the last good run time is preserved for ops.
    """
    _upsert(
        conn,
        scan_source_id=scan_source_id,
        status=_STATUS_FAILED,
        last_error=last_error,
        last_run_time=None,
        advance_run_time=False,
    )


def _upsert(
    conn: Connection,
    *,
    scan_source_id: int,
    status: str,
    last_error: str | None,
    last_run_time: datetime | None,
    advance_run_time: bool,
) -> None:
    """Insert-or-update the source's single metadata row via Postgres ``ON CONFLICT``."""
    values: dict[str, object] = {
        "scan_source_id": scan_source_id,
        "last_status": status,
        "last_error": last_error,
        "last_run_time": last_run_time,
        "updated_at": func.now(),
    }

    set_on_conflict: dict[str, object] = {
        "last_status": status,
        "last_error": last_error,
        "updated_at": func.now(),
    }
    if advance_run_time:
        set_on_conflict["last_run_time"] = last_run_time

    stmt = insert(parse_scan_run_metadata).values(**values)
    stmt = stmt.on_conflict_do_update(
        index_elements=["scan_source_id"],
        set_=set_on_conflict,
    )
    conn.execute(stmt)
