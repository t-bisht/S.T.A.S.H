"""SQLAlchemy Core table definitions for every table pen touches.

As in ingestion, these :class:`~sqlalchemy.Table` objects only *build* SQL in the repo modules
— there is no ORM, no ``create_all``. The schema is owned by raw migrations:

- ``04_ingestion_scan.sql`` — ``scan_sources`` (registry), ``swiggy_instamart_scan`` (read +
  writeback).
- ``05_item_categories.sql`` — ``item_categories`` / ``item_subcategories`` (taxonomy).
- ``06_online_retail.sql`` — ``parse_scan_run_metadata``, ``parse_failures``, and the
  ``online_retail_*`` sink (pen-owned).

Column definitions here mirror that DDL; they are the query-builder's view, not the source of
truth. The user FK column is ``user_id`` → ``users(user_id)`` (STASH convention).
"""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    Integer,
    MetaData,
    Numeric,
    Table,
    Text,
)
from sqlalchemy.dialects.postgresql import TIMESTAMP, UUID

__all__ = [
    "METADATA",
    "item_categories",
    "item_subcategories",
    "online_retail_order_history",
    "online_retail_purchase_history",
    "parse_failures",
    "parse_scan_run_metadata",
    "scan_sources",
    "swiggy_instamart_scan",
]

METADATA = MetaData()

# ─── read-only / shared registries (owned by 04 / 05) ────────────────────────
scan_sources = Table(
    "scan_sources",
    METADATA,
    Column("scan_source_id", Integer, primary_key=True),
    Column("scan_source_name", Text, nullable=False, unique=True),
    Column("description", Text),
    Column("scan_init_range_seconds", BigInteger, nullable=False),
    Column("scan_frequency_seconds", BigInteger, nullable=False),
    Column("enabled", Boolean, nullable=False),
    Column("created_at", TIMESTAMP(timezone=True)),
    Column("updated_at", TIMESTAMP(timezone=True)),
)

item_categories = Table(
    "item_categories",
    METADATA,
    Column("category_id", Integer, primary_key=True),
    Column("category_name", Text, nullable=False, unique=True),
    Column("created_at", TIMESTAMP(timezone=True)),
)

item_subcategories = Table(
    "item_subcategories",
    METADATA,
    Column("subcategory_id", Integer, primary_key=True),
    Column("category_id", Integer, nullable=False),
    Column("subcategory_name", Text, nullable=False, unique=True),
    Column("created_at", TIMESTAMP(timezone=True)),
)

# ─── ingestion-owned source table: pen reads candidates + writes back on success ─────────────
swiggy_instamart_scan = Table(
    "swiggy_instamart_scan",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("user_id", UUID(as_uuid=True), nullable=False),
    Column("message_id", Text, nullable=False),
    Column("received_at", TIMESTAMP(timezone=True), nullable=False),
    Column("scan_time", TIMESTAMP(timezone=True)),
    Column("mail_body_text", Text, nullable=False),
    Column("order_id", Text),
    Column("has_parsed", Boolean, nullable=False),
)

# ─── pen-owned tables (06) ───────────────────────────────────────────────────
parse_scan_run_metadata = Table(
    "parse_scan_run_metadata",
    METADATA,
    Column("scan_source_id", Integer, primary_key=True),
    Column("last_run_time", TIMESTAMP(timezone=True)),
    Column("last_status", Text, nullable=False),
    Column("last_error", Text),
    Column("updated_at", TIMESTAMP(timezone=True)),
)

parse_failures = Table(
    "parse_failures",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("user_id", UUID(as_uuid=True), nullable=False),
    Column("source_table", Text, nullable=False),
    Column("source_row_id", Integer, nullable=False),
    Column("message_id", Text),
    Column("reason", Text, nullable=False),
    Column("llm_raw", Text),
    Column("failed_at", TIMESTAMP(timezone=True)),
)

online_retail_order_history = Table(
    "online_retail_order_history",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("user_id", UUID(as_uuid=True), nullable=False),
    Column("order_id", Text, nullable=False),
    Column("source_id", Integer, nullable=False),
    Column("item_total_bill", Numeric(12, 2)),
    Column("handling_fee", Numeric(12, 2)),
    Column("delivery_fee", Numeric(12, 2)),
    Column("order_grand_total", Numeric(12, 2)),
    Column("order_location", Text),
    Column("order_timestamp", TIMESTAMP(timezone=True), nullable=False),
    Column("created_at", TIMESTAMP(timezone=True)),
)

online_retail_purchase_history = Table(
    "online_retail_purchase_history",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("user_id", UUID(as_uuid=True), nullable=False),
    Column("order_id", Text, nullable=False),
    Column("source_id", Integer, nullable=False),
    Column("category_id", Integer, nullable=False),
    Column("sub_category_id", Integer, nullable=False),
    Column("item_name", Text, nullable=False),
    Column("quantity", Numeric(10, 2)),
    Column("item_price", Numeric(12, 2)),
    Column("created_at", TIMESTAMP(timezone=True)),
)
