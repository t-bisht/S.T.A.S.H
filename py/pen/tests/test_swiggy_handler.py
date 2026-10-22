"""Handler orchestration (spec §4.3–4.4): parse→persist, park on failure, retry, no batch-stop."""

from __future__ import annotations

import threading
from contextlib import contextmanager
from datetime import UTC, datetime
from uuid import uuid4

import pytest

from pen.core.context import ParseContext
from pen.db import parse_failures_repo as failures
from pen.db.categories_repo import CategoryMaps
from pen.handlers.swiggy import handler as handler_mod
from pen.handlers.swiggy import parser as parser_mod
from pen.handlers.swiggy import persister as persister_mod
from pen.handlers.swiggy import scanner as scanner_mod
from pen.handlers.swiggy.handler import SwiggyHandler
from pen.handlers.swiggy.schema import ParsedOrder, ScanRow, SwiggyValidationError
from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid

# Sentinel provider — parser.parse is monkeypatched in every test, so it is never called.
_LLM = object()


class _FakeConn:
    pass


class _FakeEngine:
    """Minimal engine: connect()/begin() yield a throwaway connection via a context manager."""

    @contextmanager
    def connect(self):
        yield _FakeConn()

    @contextmanager
    def begin(self):
        yield _FakeConn()


def _cat_maps() -> CategoryMaps:
    return CategoryMaps({}, [], {}, "")


def _row(row_id: int) -> ScanRow:
    return ScanRow(
        id=row_id,
        user_id=uuid4(),
        message_id=f"m{row_id}",
        received_at=datetime(2026, 10, 10, tzinfo=UTC),
        mail_body_text="body",
    )


def _ctx() -> ParseContext:
    import logging

    return ParseContext(
        scan_source_id=7,
        source_table="",
        engine=_FakeEngine(),
        cat_maps=_cat_maps(),
        run_id="run1",
        log=logging.getLogger("test"),
        stop_event=threading.Event(),
    )


def _parsed(order_id: str = "123") -> ParsedOrder:
    return ParsedOrder(
        order_id=order_id,
        order_location=None,
        item_total_bill=None,
        handling_fee=None,
        delivery_fee=None,
        order_grand_total=None,
        items=[],
    )


@pytest.fixture
def parks(monkeypatch):
    """Capture park() calls and skip the fast-pause so rate-limit retries don't sleep."""
    captured: list[dict] = []
    monkeypatch.setattr(
        failures, "park", lambda conn, **kw: captured.append(kw)
    )
    # Patch the symbol the handler imported too (it `from pen.db import parse_failures_repo`).
    monkeypatch.setattr(handler_mod.failures, "park", lambda conn, **kw: captured.append(kw))
    return captured


def test_parsed_rows_are_persisted_not_parked(monkeypatch, parks):
    rows = [_row(1), _row(2)]
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: rows)
    monkeypatch.setattr(parser_mod, "parse", lambda llm, cat_maps, row, log: _parsed(str(row.id)))

    persisted: list[str] = []
    monkeypatch.setattr(
        persister_mod, "persist", lambda conn, ctx, parsed, row: persisted.append(parsed.order_id)
    )

    summary = SwiggyHandler(_LLM).run(_ctx())

    assert summary.candidates == 2
    assert summary.parsed == 2
    assert summary.parked == 0
    assert persisted == ["1", "2"]
    assert parks == []


def test_validation_error_parks_with_reason_and_continues(monkeypatch, parks):
    rows = [_row(1), _row(2)]
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: rows)

    def fake_parse(llm, cat_maps, row, log):
        if row.id == 1:
            raise SwiggyValidationError("bad", reason=failures.REASON_MISSING_ORDER_ID, llm_raw="{}")
        return _parsed(str(row.id))

    monkeypatch.setattr(parser_mod, "parse", fake_parse)
    monkeypatch.setattr(persister_mod, "persist", lambda conn, ctx, parsed, row: None)

    summary = SwiggyHandler(_LLM).run(_ctx())

    # Row 1 parked; row 2 still parsed — no batch-stop.
    assert summary.parked == 1
    assert summary.parsed == 1
    assert parks[0]["reason"] == failures.REASON_MISSING_ORDER_ID
    assert summary.parked_by_reason == {failures.REASON_MISSING_ORDER_ID: 1}


def test_invalid_json_parks_invalid_json_with_raw(monkeypatch, parks):
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: [_row(1)])

    def fake_parse(llm, cat_maps, row, log):
        raise LLMResponseInvalid("nope", raw="garbage")

    monkeypatch.setattr(parser_mod, "parse", fake_parse)

    summary = SwiggyHandler(_LLM).run(_ctx())
    assert summary.parked_by_reason == {failures.REASON_INVALID_JSON: 1}
    assert parks[0]["llm_raw"] == "garbage"


def test_rate_limit_retries_then_parks_rate_limit(monkeypatch, parks):
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: [_row(1)])

    attempts = {"n": 0}

    def always_429(llm, cat_maps, row, log):
        attempts["n"] += 1
        raise LLMRateLimited("429")

    monkeypatch.setattr(parser_mod, "parse", always_429)

    # retries=2 with a 0s pause keeps the test instant while still exercising the retry loop.
    summary = SwiggyHandler(_LLM, retries=2, rate_limit_pause_seconds=0).run(_ctx())

    assert attempts["n"] == 2  # retried up to the limit
    assert summary.parked_by_reason == {failures.REASON_RATE_LIMIT: 1}


def test_transient_llm_error_parks_llm_error(monkeypatch, parks):
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: [_row(1)])

    def always_err(llm, cat_maps, row, log):
        raise LLMError("5xx")

    monkeypatch.setattr(parser_mod, "parse", always_err)

    summary = SwiggyHandler(_LLM, retries=2, rate_limit_pause_seconds=0).run(_ctx())
    assert summary.parked_by_reason == {failures.REASON_LLM_ERROR: 1}


def test_persist_failure_is_not_parked_and_retried_next_pass(monkeypatch, parks):
    monkeypatch.setattr(scanner_mod, "select_candidates", lambda conn, **kw: [_row(1)])
    monkeypatch.setattr(parser_mod, "parse", lambda llm, cat_maps, row, log: _parsed("1"))

    def boom(conn, ctx, parsed, row):
        raise RuntimeError("db down")

    monkeypatch.setattr(persister_mod, "persist", boom)

    summary = SwiggyHandler(_LLM).run(_ctx())
    assert summary.parsed == 0
    assert summary.persist_errors == 1
    assert parks == []  # a parseable row is never parked on a transient persist error
