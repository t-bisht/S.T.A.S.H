"""Repository for ``swiggy_instamart_scan`` — ingested Swiggy Instamart mails.

Two operations drive the dedup strategy (spec §7):

- :func:`existing_message_ids` — the id-diff: given the ids a Gmail search returned, which do
  we already hold? The handler fetches full bodies only for the rest (the cost saver).
- :func:`insert_new` — an idempotent ``INSERT ... ON CONFLICT (user_id, message_id) DO NOTHING``
  backstop, so any run is idempotent regardless of the id-diff.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.engine import Connection

from ingestion.db.models import swiggy_instamart_scan

__all__ = ["SwiggyScanRow", "existing_message_ids", "insert_new"]


@dataclass(frozen=True, slots=True)
class SwiggyScanRow:
    """A row to persist for one ingested mail.

    ``order_id`` and ``has_parsed`` are intentionally absent: ingestion always writes
    ``has_parsed = false`` and leaves ``order_id`` null for the downstream LLM parse module.

    Attributes:
        user_id: Owner of the mail.
        message_id: Gmail message id (the dedup key).
        received_at: When the mail arrived (from Gmail ``internalDate``), UTC.
        mail_body_text: HTML-stripped clean text (no raw body stored).
    """

    user_id: UUID
    message_id: str
    received_at: datetime
    mail_body_text: str


def existing_message_ids(
    conn: Connection,
    user_id: UUID,
    message_ids: list[str],
) -> set[str]:
    """Return which of ``message_ids`` already exist for this user.

    Args:
        conn: Live DB connection (read).
        user_id: The user whose mails to check.
        message_ids: Candidate Gmail ids from the search.

    Returns:
        The subset of ``message_ids`` already stored. Empty set when ``message_ids`` is empty
        (no query issued).
    """
    if not message_ids:
        return set()
    stmt = select(swiggy_instamart_scan.c.message_id).where(
        swiggy_instamart_scan.c.user_id == user_id,
        swiggy_instamart_scan.c.message_id.in_(message_ids),
    )
    return {row.message_id for row in conn.execute(stmt)}


def insert_new(conn: Connection, rows: list[SwiggyScanRow]) -> int:
    """Insert mail rows idempotently; already-present ``(user_id, message_id)`` pairs are skipped.

    Args:
        conn: Live DB connection (inside the caller's transaction).
        rows: Rows to persist.

    Returns:
        The number of rows actually inserted (conflicts are not counted).
    """
    if not rows:
        return 0
    stmt = insert(swiggy_instamart_scan).values(
        [
            {
                "user_id": row.user_id,
                "message_id": row.message_id,
                "received_at": row.received_at,
                "mail_body_text": row.mail_body_text,
                "has_parsed": False,
            }
            for row in rows
        ]
    )
    stmt = stmt.on_conflict_do_nothing(index_elements=["user_id", "message_id"])
    result = conn.execute(stmt)
    # rowcount reflects rows actually inserted (conflicts excluded) for ON CONFLICT DO NOTHING.
    return result.rowcount if result.rowcount is not None else 0
