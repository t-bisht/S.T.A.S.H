"""Repository for ``user_scan_preferences`` — who is enrolled per source."""

from __future__ import annotations

from uuid import UUID

from sqlalchemy import select
from sqlalchemy.engine import Connection

from ingestion.db.models import user_scan_preferences

__all__ = ["list_enrolled_user_ids"]


def list_enrolled_user_ids(conn: Connection, scan_source_id: int) -> list[UUID]:
    """Return the user ids enrolled (and enabled) for a given source.

    Args:
        conn: Live DB connection (read).
        scan_source_id: Surrogate id of the source (FK), never a hardcoded literal.

    Returns:
        Enrolled user ids, ordered for stable iteration across runs.
    """
    stmt = (
        select(user_scan_preferences.c.user_id)
        .where(
            user_scan_preferences.c.scan_source_id == scan_source_id,
            user_scan_preferences.c.enabled.is_(True),
        )
        .order_by(user_scan_preferences.c.user_id)
    )
    return [row.user_id for row in conn.execute(stmt)]
