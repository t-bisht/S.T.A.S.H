"""Unit tests for the Swiggy Instamart handler, the id-diff flow, and html_to_text."""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

import pytest

from ingestion.db import swiggy_scan_repo
from ingestion.db.swiggy_scan_repo import SwiggyScanRow
from ingestion.gmail.client import ParsedMessage, html_to_text
from ingestion.logging_setup import run_context_logger
from ingestion.sources.base import ScanContext
from ingestion.sources.swiggy_instamart.handler import SwiggyInstamartHandler


class _FakeGmail:
    """Fake Gmail client returning scripted ids and messages; records fetched ids."""

    def __init__(self, ids: list[str], messages: dict[str, ParsedMessage]) -> None:
        self._ids = ids
        self._messages = messages
        self.last_query: str | None = None
        self.fetched: list[str] = []

    def search_all_ids(self, token: str, query: str) -> list[str]:
        self.last_query = query
        return list(self._ids)

    def fetch_message(self, token: str, message_id: str) -> ParsedMessage:
        self.fetched.append(message_id)
        return self._messages[message_id]


class _StubBroker:
    def get_access_token(self, user_id):
        return "tok"

    def refresh(self, user_id):
        return "tok"


def _ctx(logger_adapter, since: datetime) -> ScanContext:
    return ScanContext(
        user_id=uuid4(),
        scan_source_id=7,
        since=since,
        broker=_StubBroker(),
        db=object(),  # repos are monkeypatched, so the connection is never used.
        run_id="run1234",
        log=logger_adapter,
    )


@pytest.fixture
def rlog():
    import logging

    return run_context_logger(
        logging.getLogger("test"), run_id="r", user_id="u", scan_source_name="swiggy_instamart"
    )


def _msg(message_id: str) -> ParsedMessage:
    return ParsedMessage(
        message_id=message_id,
        received_at=datetime(2026, 10, 10, tzinfo=UTC),
        subject="your instamart order",
        from_addr="noreply@swiggy.in",
        body_text=f"body-{message_id}",
    )


def test_scan_fetches_only_new_ids_and_persists(monkeypatch, rlog) -> None:
    # Search returns three ids; one is already stored, so only two are fetched/stored.
    ids = ["a", "b", "c"]
    gmail = _FakeGmail(ids, {"a": _msg("a"), "b": _msg("b"), "c": _msg("c")})

    captured: dict[str, list[SwiggyScanRow]] = {}
    monkeypatch.setattr(swiggy_scan_repo, "existing_message_ids", lambda conn, uid, mids: {"b"})

    def fake_insert(conn, rows):
        captured["rows"] = rows
        return len(rows)

    monkeypatch.setattr(swiggy_scan_repo, "insert_new", fake_insert)

    handler = SwiggyInstamartHandler(gmail, skew_seconds=300)
    result = handler.scan(_ctx(rlog, since=datetime(2026, 9, 1, tzinfo=UTC)))

    assert gmail.fetched == ["a", "c"]  # "b" skipped by the id-diff
    assert result.matched == 3
    assert result.fetched == 2
    assert result.stored == 2
    stored_ids = {row.message_id for row in captured["rows"]}
    assert stored_ids == {"a", "c"}
    assert all(isinstance(row, SwiggyScanRow) for row in captured["rows"])


def test_build_query_uses_epoch_after_minus_skew(monkeypatch, rlog) -> None:
    gmail = _FakeGmail([], {})
    monkeypatch.setattr(swiggy_scan_repo, "existing_message_ids", lambda conn, uid, mids: set())
    monkeypatch.setattr(swiggy_scan_repo, "insert_new", lambda conn, rows: 0)

    since = datetime(2026, 10, 1, 0, 0, 0, tzinfo=UTC)
    handler = SwiggyInstamartHandler(gmail, skew_seconds=300)
    handler.scan(_ctx(rlog, since=since))

    expected_epoch = int(since.timestamp()) - 300
    assert gmail.last_query == f'subject:"your instamart order" after:{expected_epoch}'


@pytest.mark.parametrize(
    ("html", "expected"),
    [
        ("<p>Order&nbsp;total: ₹499</p>", "Order total: ₹499"),
        ("<html><head><style>.x{}</style></head><body><p>Hi</p></body></html>", "Hi"),
        ("", ""),
        ("line1\n\n\n   line2  ", "line1\nline2"),
    ],
)
def test_html_to_text(html: str, expected: str) -> None:
    assert html_to_text(html) == expected
