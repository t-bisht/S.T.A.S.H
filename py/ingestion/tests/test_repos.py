"""Tests for the repo SQL — compiled against the Postgres dialect, no live DB needed.

These assert the dedup/upsert SQL semantics the design depends on (``ON CONFLICT DO NOTHING``
for mail inserts, ``ON CONFLICT DO UPDATE`` for the watermark) and the id-diff short-circuits.
"""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

from sqlalchemy.dialects import postgresql

from ingestion.db import run_metadata_repo, swiggy_scan_repo
from ingestion.db.swiggy_scan_repo import SwiggyScanRow


class _FakeResult:
    def __init__(self, rows: list | None = None, rowcount: int = 0) -> None:
        self._rows = rows or []
        self.rowcount = rowcount

    def __iter__(self):
        return iter(self._rows)

    def first(self):
        return self._rows[0] if self._rows else None


class _CapturingConn:
    """Captures the statement passed to execute and returns a scripted result."""

    def __init__(self, result: _FakeResult | None = None) -> None:
        self.statements: list = []
        self._result = result or _FakeResult()

    def execute(self, statement):
        self.statements.append(statement)
        return self._result


def _compiled(statement) -> str:
    return str(statement.compile(dialect=postgresql.dialect())).upper()


def test_insert_new_empty_is_noop() -> None:
    conn = _CapturingConn()
    assert swiggy_scan_repo.insert_new(conn, []) == 0
    assert conn.statements == []  # no SQL issued for an empty batch


def test_insert_new_uses_on_conflict_do_nothing() -> None:
    conn = _CapturingConn(_FakeResult(rowcount=1))
    row = SwiggyScanRow(
        user_id=uuid4(),
        message_id="m1",
        received_at=datetime(2026, 10, 1, tzinfo=UTC),
        mail_body_text="hi",
    )
    inserted = swiggy_scan_repo.insert_new(conn, [row])

    assert inserted == 1
    sql = _compiled(conn.statements[0])
    assert "INSERT INTO SWIGGY_INSTAMART_SCAN" in sql
    assert "ON CONFLICT" in sql and "DO NOTHING" in sql


def test_existing_message_ids_empty_short_circuits() -> None:
    conn = _CapturingConn()
    assert swiggy_scan_repo.existing_message_ids(conn, uuid4(), []) == set()
    assert conn.statements == []  # no query for an empty candidate list


def test_upsert_success_advances_watermark_via_on_conflict_do_update() -> None:
    conn = _CapturingConn()
    run_metadata_repo.upsert_success(conn, uuid4(), 7, datetime(2026, 10, 1, tzinfo=UTC))

    sql = _compiled(conn.statements[0])
    assert "INSERT INTO SOURCE_SCAN_RUN_METADATA" in sql
    assert "ON CONFLICT" in sql and "DO UPDATE" in sql
    assert "LAST_SCAN_TIME" in sql  # watermark is part of the update on success


def test_upsert_failure_preserves_watermark() -> None:
    conn = _CapturingConn()
    run_metadata_repo.upsert_failure(conn, uuid4(), 7, "login_required")

    sql = _compiled(conn.statements[0])
    # On failure the ON CONFLICT ... DO UPDATE SET clause must NOT touch last_scan_time
    # (the watermark stays put so the next run retries the same window).
    _, _, set_clause = sql.partition("DO UPDATE SET")
    assert set_clause, "expected an ON CONFLICT DO UPDATE SET clause"
    assert "LAST_SCAN_TIME" not in set_clause
    assert "LAST_SCAN_STATUS" in set_clause
