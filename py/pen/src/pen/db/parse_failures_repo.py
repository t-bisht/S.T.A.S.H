"""Repository for ``parse_failures`` — the row-level failure surface (spec §4.4).

A parked row is recorded here with a reason and thereafter excluded from candidate selection by
the scanner's anti-join (spec §4.2). Parking is the terminal state for a row that can't be
parsed; re-queue is manual for MVP (delete the row and rerun).

The park is idempotent (``ON CONFLICT (source_table, source_row_id) DO NOTHING``): re-parking
the same row is a harmless no-op, so a handler never has to check-before-insert.
"""

from __future__ import annotations

from uuid import UUID

from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.engine import Connection

from pen.db.models import parse_failures

__all__ = [
    "REASON_INVALID_JSON",
    "REASON_LLM_ERROR",
    "REASON_MISSING_ORDER_ID",
    "REASON_RATE_LIMIT",
    "REASON_VALIDATION_ERROR",
    "park",
]

# The §4.4 reason taxonomy, as code constants so handlers never pass a stray literal.
REASON_RATE_LIMIT = "rate_limit"            # 429 survived retries (transient — mail is fine)
REASON_INVALID_JSON = "invalid_json"        # output not parseable JSON after fence-strip
REASON_MISSING_ORDER_ID = "missing_order_id"  # order_id null/empty/non-numeric (§8.2)
REASON_VALIDATION_ERROR = "validation_error"  # schema / field validation failed
REASON_LLM_ERROR = "llm_error"              # other provider/transport error after retries


def park(
    conn: Connection,
    *,
    user_id: UUID,
    source_table: str,
    source_row_id: int,
    reason: str,
    message_id: str | None = None,
    llm_raw: str | None = None,
) -> None:
    """Park a failed source row with a reason (idempotent).

    Args:
        conn: Live DB connection (inside the caller's transaction).
        user_id: Owner of the failed mail (FK → ``users``).
        source_table: The source scan table, e.g. ``swiggy_instamart_scan``.
        source_row_id: The source row's id.
        reason: One of the ``REASON_*`` constants (§4.4 taxonomy).
        message_id: Cross-ref back to the mail, when known.
        llm_raw: Raw model output for triage (stored for ``invalid_json`` / validation fails).
    """
    stmt = insert(parse_failures).values(
        user_id=user_id,
        source_table=source_table,
        source_row_id=source_row_id,
        message_id=message_id,
        reason=reason,
        llm_raw=llm_raw,
    )
    stmt = stmt.on_conflict_do_nothing(index_elements=["source_table", "source_row_id"])
    conn.execute(stmt)
