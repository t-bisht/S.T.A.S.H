"""SQLAlchemy Core engine / connection factory (mirrors ingestion's).

A single :class:`~sqlalchemy.engine.Engine` (connection pool) is created from the configured
DSN and shared process-wide. Callers obtain short-lived connections via the engine's own
context managers:

- ``with engine.begin() as conn:`` — the per-parsed-row transaction (order + items + source
  writeback commit atomically, spec §7).
- ``with engine.connect() as conn:`` — read connections (candidate scan, taxonomy load).
"""

from __future__ import annotations

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine

__all__ = ["create_db_engine"]


def create_db_engine(database_url: str, *, pool_size: int = 5) -> Engine:
    """Create the shared SQLAlchemy engine for the pen process.

    Args:
        database_url: SQLAlchemy psycopg URL
            (``postgresql+psycopg://user:pass@host:port/dbname``).
        pool_size: Base connection-pool size. v1 is single-process and low-concurrency, so a
            small pool is plenty.

    Returns:
        A configured :class:`~sqlalchemy.engine.Engine`. ``pool_pre_ping`` is enabled so stale
        connections (e.g. after a DB restart) are detected and transparently replaced.
    """
    return create_engine(
        database_url,
        pool_size=pool_size,
        pool_pre_ping=True,
        future=True,
    )
