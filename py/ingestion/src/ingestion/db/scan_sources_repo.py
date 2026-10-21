"""Repository for the ``scan_sources`` table — the single source of truth for scan config.

The scheduler reads enabled sources fresh on every poll so new or edited sources (cadence,
lookback, enable/disable) are picked up without a restart. See spec §4.
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.engine import Connection

from ingestion.db.models import scan_sources

__all__ = ["ScanSource", "list_enabled"]


@dataclass(frozen=True, slots=True)
class ScanSource:
    """A configured scan source.

    Attributes:
        scan_source_id: Surrogate PK; used only as the FK in the other tables. Never hardcoded.
        scan_source_name: Stable registry key matched to a code handler (e.g. ``swiggy_instamart``).
        scan_init_range_seconds: First-run lookback window, in seconds.
        scan_frequency_seconds: Cadence; a user is due when ``now - last_scan_time >= this``.
    """

    scan_source_id: int
    scan_source_name: str
    scan_init_range_seconds: int
    scan_frequency_seconds: int


def list_enabled(conn: Connection) -> list[ScanSource]:
    """Return all enabled scan sources.

    Args:
        conn: Live DB connection (read).

    Returns:
        Enabled sources as :class:`ScanSource` records, ordered by name for stable iteration.
    """
    stmt = (
        select(
            scan_sources.c.scan_source_id,
            scan_sources.c.scan_source_name,
            scan_sources.c.scan_init_range_seconds,
            scan_sources.c.scan_frequency_seconds,
        )
        .where(scan_sources.c.enabled.is_(True))
        .order_by(scan_sources.c.scan_source_name)
    )
    return [
        ScanSource(
            scan_source_id=row.scan_source_id,
            scan_source_name=row.scan_source_name,
            scan_init_range_seconds=int(row.scan_init_range_seconds),
            scan_frequency_seconds=int(row.scan_frequency_seconds),
        )
        for row in conn.execute(stmt)
    ]
