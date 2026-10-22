"""Repository for ``scan_sources`` — pen's shared source registry (spec §2.3).

pen reuses ingestion's ``scan_sources`` table rather than owning a ``parse_sources`` table. It
reads only the identity + enablement; ``scan_frequency_seconds`` is ingestion's cadence and is
deliberately ignored (pen's cadence is code-level, :mod:`pen.parse_handler_config`).
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.engine import Connection

from pen.db.models import scan_sources

__all__ = ["ScanSource", "list_enabled"]


@dataclass(frozen=True, slots=True)
class ScanSource:
    """A configured source pen may parse.

    Attributes:
        scan_source_id: Surrogate PK; the ``source_id`` provenance FK on retail rows and the
            ``parse_scan_run_metadata`` key. Never hardcoded.
        scan_source_name: Stable registry key → handler class in ``core.registry.HANDLERS``.
    """

    scan_source_id: int
    scan_source_name: str


def list_enabled(conn: Connection) -> list[ScanSource]:
    """Return all enabled scan sources, ordered by name for stable iteration.

    Args:
        conn: Live DB connection (read).

    Returns:
        Enabled sources as :class:`ScanSource` records.
    """
    stmt = (
        select(scan_sources.c.scan_source_id, scan_sources.c.scan_source_name)
        .where(scan_sources.c.enabled.is_(True))
        .order_by(scan_sources.c.scan_source_name)
    )
    return [
        ScanSource(scan_source_id=row.scan_source_id, scan_source_name=row.scan_source_name)
        for row in conn.execute(stmt)
    ]
