"""The "persist" stage — map the parsed DTO → retail rows + source writeback, ONE tx (spec §7).

Called by the handler inside a single ``engine.begin()`` transaction, so the order row, its
item rows, and the source writeback commit atomically — no half-orders. A crash before the
writeback leaves ``has_parsed=false`` → the row is cleanly re-picked next pass, and the
``UNIQUE(user_id, order_id)`` backstop keeps the re-parse from duplicating the order.

Ownership: this maps the *Swiggy* DTO onto the *shared* retail records (``retail_repo``) and
writes back the *Swiggy* source row. The retail table SQL itself is shared and source-agnostic.
"""

from __future__ import annotations

from sqlalchemy import update
from sqlalchemy.engine import Connection

from pen.core.context import ParseContext
from pen.db import retail_repo
from pen.db.models import swiggy_instamart_scan
from pen.handlers.swiggy.schema import ParsedOrder, ScanRow

__all__ = ["persist"]


def persist(conn: Connection, ctx: ParseContext, parsed: ParsedOrder, row: ScanRow) -> None:
    """Write the order + items and mark the source row parsed, all in the caller's transaction.

    Args:
        conn: Live connection inside the per-row ``engine.begin()`` transaction.
        ctx: The pass context (provides ``scan_source_id`` → the retail ``source_id``).
        parsed: The validated, post-processed order.
        row: The source candidate (provides ``user_id``, the writeback target id, and
            ``received_at`` → ``order_timestamp``).
    """
    order = retail_repo.OrderRecord(
        user_id=row.user_id,
        order_id=parsed.order_id,
        source_id=ctx.scan_source_id,
        item_total_bill=parsed.item_total_bill,
        handling_fee=parsed.handling_fee,
        delivery_fee=parsed.delivery_fee,
        order_grand_total=parsed.order_grand_total,
        order_location=parsed.order_location,
        order_timestamp=row.received_at,  # spec §7/§8.3 — source time, NOT the LLM.
    )
    items = [
        retail_repo.ItemRecord(
            user_id=row.user_id,
            order_id=parsed.order_id,
            source_id=ctx.scan_source_id,
            category_id=item.category_id,
            sub_category_id=item.sub_category_id,
            item_name=item.item_name,
            quantity=item.quantity,
            item_price=item.item_price,
        )
        for item in parsed.items
    ]

    retail_repo.insert_order(conn, order)
    retail_repo.insert_items(conn, items)
    _writeback(conn, row.id, parsed.order_id)


def _writeback(conn: Connection, source_row_id: int, order_id: str) -> None:
    """Stamp the source row: ``order_id = :oid``, ``has_parsed = true`` (spec §10.5)."""
    stmt = (
        update(swiggy_instamart_scan)
        .where(swiggy_instamart_scan.c.id == source_row_id)
        .values(order_id=order_id, has_parsed=True)
    )
    conn.execute(stmt)
