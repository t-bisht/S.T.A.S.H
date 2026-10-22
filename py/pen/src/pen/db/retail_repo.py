"""Repository for the ``online_retail_*`` spend sink (spec §7, §10.3–10.4).

The shared, source-agnostic write path: one parent order row + N child item rows. A handler's
persister maps its own parsed DTO onto the table-shaped :class:`OrderRecord` /
:class:`ItemRecord` here and calls these inside a single transaction (spec §7) — this module
knows nothing about Swiggy or the LLM, only the retail tables.

The parent insert is ``ON CONFLICT (user_id, order_id) DO NOTHING`` — a crash-recovery backstop
only; the primary idempotency guard is ``has_parsed`` on the source row (spec §7).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from uuid import UUID

from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.engine import Connection

from pen.db.models import online_retail_order_history, online_retail_purchase_history

__all__ = ["ItemRecord", "OrderRecord", "insert_items", "insert_order"]

# Money/quantity values are Optional floats from the LLM; SQLAlchemy adapts them to NUMERIC.
_Money = float | Decimal | None


@dataclass(frozen=True, slots=True)
class OrderRecord:
    """A table-shaped parent order row (``online_retail_order_history``)."""

    user_id: UUID
    order_id: str
    source_id: int
    item_total_bill: _Money
    handling_fee: _Money
    delivery_fee: _Money
    order_grand_total: _Money
    order_location: str | None
    order_timestamp: datetime


@dataclass(frozen=True, slots=True)
class ItemRecord:
    """A table-shaped child item row (``online_retail_purchase_history``)."""

    user_id: UUID
    order_id: str
    source_id: int
    category_id: int
    sub_category_id: int
    item_name: str
    quantity: _Money
    item_price: _Money


def insert_order(conn: Connection, order: OrderRecord) -> None:
    """Insert the parent order row; a conflicting ``(user_id, order_id)`` is left untouched.

    Args:
        conn: Live DB connection (inside the per-row transaction).
        order: The parent order to write.
    """
    stmt = insert(online_retail_order_history).values(
        user_id=order.user_id,
        order_id=order.order_id,
        source_id=order.source_id,
        item_total_bill=order.item_total_bill,
        handling_fee=order.handling_fee,
        delivery_fee=order.delivery_fee,
        order_grand_total=order.order_grand_total,
        order_location=order.order_location,
        order_timestamp=order.order_timestamp,
    )
    stmt = stmt.on_conflict_do_nothing(index_elements=["user_id", "order_id"])
    conn.execute(stmt)


def insert_items(conn: Connection, items: list[ItemRecord]) -> int:
    """Insert the child item rows.

    Args:
        conn: Live DB connection (inside the per-row transaction).
        items: The order's item rows. An empty list is a no-op.

    Returns:
        The number of item rows inserted.
    """
    if not items:
        return 0
    stmt = insert(online_retail_purchase_history).values(
        [
            {
                "user_id": item.user_id,
                "order_id": item.order_id,
                "source_id": item.source_id,
                "category_id": item.category_id,
                "sub_category_id": item.sub_category_id,
                "item_name": item.item_name,
                "quantity": item.quantity,
                "item_price": item.item_price,
            }
            for item in items
        ]
    )
    result = conn.execute(stmt)
    return result.rowcount if result.rowcount is not None else len(items)
