"""Repository for ``source_scan_run_metadata`` — per-(user, source) watermark + health.

A row is written on EVERY attempt (success and failure). ``last_scan_time`` advances only on
success; a failed run leaves it untouched so the next run retries the same window. The table
doubles as the queryable health surface for ops (who is failing, and why). See spec §7–§8.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.engine import Connection

from ingestion.db.models import source_scan_run_metadata

__all__ = ["RunMetadata", "get", "upsert_failure", "upsert_success"]

_STATUS_SUCCESS = "SUCCESS"
_STATUS_FAILED = "FAILED"


@dataclass(frozen=True, slots=True)
class RunMetadata:
    """Last-attempt state for a (user, source) pair.

    Attributes:
        last_scan_time: Watermark — start time of the last *successful* run; ``None`` until the
            first success (first run then uses the source's init-range lookback).
        last_scan_status: ``SUCCESS`` or ``FAILED``.
        last_error: Short reason when failed (e.g. ``login_required``); else ``None``.
    """

    last_scan_time: datetime | None
    last_scan_status: str
    last_error: str | None


def get(conn: Connection, user_id: UUID, scan_source_id: int) -> RunMetadata | None:
    """Return the metadata row for a (user, source) pair, or ``None`` if there is none.

    A missing row (or a null ``last_scan_time``) means the next run is a first run and should
    use the source's init-range lookback.
    """
    stmt = select(
        source_scan_run_metadata.c.last_scan_time,
        source_scan_run_metadata.c.last_scan_status,
        source_scan_run_metadata.c.last_error,
    ).where(
        source_scan_run_metadata.c.user_id == user_id,
        source_scan_run_metadata.c.scan_source_id == scan_source_id,
    )
    row = conn.execute(stmt).first()
    if row is None:
        return None
    return RunMetadata(
        last_scan_time=row.last_scan_time,
        last_scan_status=row.last_scan_status,
        last_error=row.last_error,
    )


def upsert_success(
    conn: Connection,
    user_id: UUID,
    scan_source_id: int,
    last_scan_time: datetime,
) -> None:
    """Record a successful attempt and advance the watermark.

    Args:
        conn: Live DB connection (inside the caller's transaction).
        user_id: The scanned user.
        scan_source_id: The source's surrogate id.
        last_scan_time: The run's start time — the new watermark. Using the run *start* (not
            end) guarantees no mail that arrived during the run is skipped next time.
    """
    _upsert(
        conn,
        user_id=user_id,
        scan_source_id=scan_source_id,
        status=_STATUS_SUCCESS,
        last_error=None,
        last_scan_time=last_scan_time,
        advance_watermark=True,
    )


def upsert_failure(
    conn: Connection,
    user_id: UUID,
    scan_source_id: int,
    last_error: str,
) -> None:
    """Record a failed attempt WITHOUT advancing the watermark.

    The next run then retries the same window; the three-layer dedup (spec §7) makes the retry
    safe. ``last_error`` is a short, stable reason (e.g. ``login_required``) for ops queries.
    """
    _upsert(
        conn,
        user_id=user_id,
        scan_source_id=scan_source_id,
        status=_STATUS_FAILED,
        last_error=last_error,
        last_scan_time=None,
        advance_watermark=False,
    )


def _upsert(
    conn: Connection,
    *,
    user_id: UUID,
    scan_source_id: int,
    status: str,
    last_error: str | None,
    last_scan_time: datetime | None,
    advance_watermark: bool,
) -> None:
    """Insert-or-update the (user, source) metadata row via Postgres ``ON CONFLICT``.

    On conflict the status/error/updated_at always update; ``last_scan_time`` updates only
    when ``advance_watermark`` is true (i.e. on success), otherwise the existing watermark is
    preserved.
    """
    values: dict[str, object] = {
        "user_id": user_id,
        "scan_source_id": scan_source_id,
        "last_scan_status": status,
        "last_error": last_error,
        "last_scan_time": last_scan_time,
        "updated_at": func.now(),
    }

    set_on_conflict: dict[str, object] = {
        "last_scan_status": status,
        "last_error": last_error,
        "updated_at": func.now(),
    }
    if advance_watermark:
        set_on_conflict["last_scan_time"] = last_scan_time

    stmt = insert(source_scan_run_metadata).values(**values)
    stmt = stmt.on_conflict_do_update(
        index_elements=["user_id", "scan_source_id"],
        set_=set_on_conflict,
    )
    conn.execute(stmt)
