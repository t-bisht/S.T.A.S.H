"""Database access layer.

PostgreSQL via SQLAlchemy **Core** over psycopg v3 — no ORM. Tables are declared once in
:mod:`ingestion.db.models`; each table gets one repo module exposing explicit, hand-written
SQL expressions (see spec §9). Repos take a live :class:`sqlalchemy.Connection` and never open
or commit transactions themselves — the caller (the runner) owns the transaction boundary so a
handler's row inserts and its watermark advance commit together, atomically per user.
"""

from __future__ import annotations

__all__: list[str] = []
