"""SQLAlchemy Core table definitions for the ingestion schema.

These :class:`~sqlalchemy.Table` objects are used only to *build* SQL expressions in the repo
modules — there is no ORM, no mapped classes, no ``create_all`` (the schema is owned by the
raw migration ``infra/self_hosted/pg-init/04_ingestion_scan.sql``). Column definitions here
mirror that DDL; they are the query-builder's view of the tables, not the source of truth.

The user foreign key is named ``user_id`` (referencing ``users(user_id)``) to match the
established STASH schema convention — see the migration's naming note.
"""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    Integer,
    MetaData,
    Table,
    Text,
)
from sqlalchemy.dialects.postgresql import TIMESTAMP, UUID

__all__ = [
    "METADATA",
    "scan_sources",
    "source_scan_run_metadata",
    "swiggy_instamart_scan",
    "user_scan_preferences",
]

METADATA = MetaData()

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

user_scan_preferences = Table(
    "user_scan_preferences",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("user_id", UUID(as_uuid=True), nullable=False),
    Column("scan_source_id", Integer, nullable=False),
    Column("enabled", Boolean, nullable=False),
    Column("created_at", TIMESTAMP(timezone=True)),
)

source_scan_run_metadata = Table(
    "source_scan_run_metadata",
    METADATA,
    Column("user_id", UUID(as_uuid=True), primary_key=True),
    Column("scan_source_id", Integer, primary_key=True),
    Column("last_scan_time", TIMESTAMP(timezone=True)),
    Column("last_scan_status", Text, nullable=False),
    Column("last_error", Text),
    Column("updated_at", TIMESTAMP(timezone=True)),
)

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
