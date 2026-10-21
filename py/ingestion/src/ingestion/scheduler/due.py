"""The pure 'is this (user, source) due for a scan?' predicate.

Kept free of I/O so it is trivially unit-testable (spec §4). A source is due for a user when
no successful scan has happened yet, or enough time has elapsed since the last one.
"""

from __future__ import annotations

from datetime import datetime

__all__ = ["is_due"]


def is_due(
    last_scan_time: datetime | None,
    scan_frequency_seconds: int,
    now: datetime,
) -> bool:
    """Return whether a scan is due.

    Args:
        last_scan_time: Watermark — the last *successful* scan start, or ``None`` if there has
            never been one (first run ⇒ always due).
        scan_frequency_seconds: Cadence in seconds from the source config.
        now: Current time (passed in for testability; must share tz-awareness with
            ``last_scan_time``).

    Returns:
        ``True`` if ``last_scan_time`` is ``None`` or at least ``scan_frequency_seconds`` have
        elapsed since it; ``False`` otherwise.
    """
    if last_scan_time is None:
        return True
    elapsed = (now - last_scan_time).total_seconds()
    return elapsed >= scan_frequency_seconds
