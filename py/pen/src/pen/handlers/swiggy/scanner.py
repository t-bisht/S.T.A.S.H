"""The "scan" stage — select unparsed candidate rows (spec §4.2).

Correctness gate: ``has_parsed = false`` AND not already parked (anti-join ``parse_failures``).
No sweep-window bookkeeping — a parsed row is excluded by ``has_parsed``, a permanently-failed
row by the anti-join. Oldest first, for deterministic draining.
"""

from __future__ import annotations

from sqlalchemy import and_, exists, select
from sqlalchemy.engine import Connection

from pen.db.models import parse_failures, swiggy_instamart_scan
from pen.handlers.swiggy.schema import ScanRow

__all__ = ["SOURCE_TABLE", "select_candidates"]

# The source scan table name — also the ``parse_failures.source_table`` provenance value.
SOURCE_TABLE = "swiggy_instamart_scan"


def select_candidates(conn: Connection, *, limit: int | None = None) -> list[ScanRow]:
    """Return unparsed, unparked candidate rows, oldest first (spec §4.2).

    Args:
        conn: Live DB connection (read).
        limit: Optional cap on rows per pass (defensive bound for a large backlog). ``None`` =
            all candidates.

    Returns:
        Candidate rows as :class:`ScanRow` records.
    """
    s = swiggy_instamart_scan
    parked = exists().where(
        and_(
            parse_failures.c.source_table == SOURCE_TABLE,
            parse_failures.c.source_row_id == s.c.id,
        )
    )
    stmt = (
        select(
            s.c.id,
            s.c.user_id,
            s.c.message_id,
            s.c.received_at,
            s.c.mail_body_text,
        )
        .where(s.c.has_parsed.is_(False), ~parked)
        .order_by(s.c.received_at.asc())
    )
    if limit is not None:
        stmt = stmt.limit(limit)

    return [
        ScanRow(
            id=row.id,
            user_id=row.user_id,
            message_id=row.message_id,
            received_at=row.received_at,
            mail_body_text=row.mail_body_text,
        )
        for row in conn.execute(stmt)
    ]
