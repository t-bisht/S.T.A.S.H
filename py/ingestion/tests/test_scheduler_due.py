"""Unit tests for the pure due-computation predicate."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from ingestion.scheduler.due import is_due

_NOW = datetime(2026, 10, 19, 12, 0, 0, tzinfo=UTC)


@pytest.mark.parametrize(
    ("last_scan_time", "frequency_seconds", "expected"),
    [
        # First run: no watermark → always due.
        (None, 86_400, True),
        # Exactly at the cadence boundary → due (>= comparison).
        (_NOW - timedelta(seconds=86_400), 86_400, True),
        # Past the cadence → due.
        (_NOW - timedelta(seconds=90_000), 86_400, True),
        # Well short of the cadence → not due.
        (_NOW - timedelta(seconds=100), 86_400, False),
        # Monthly cadence, scanned an hour ago → not due.
        (_NOW - timedelta(hours=1), 2_592_000, False),
    ],
)
def test_is_due(last_scan_time: datetime | None, frequency_seconds: int, expected: bool) -> None:
    assert is_due(last_scan_time, frequency_seconds, _NOW) is expected
