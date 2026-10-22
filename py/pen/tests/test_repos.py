"""Repo-level logic that needs no live DB: taxonomy map building + reason constants."""

from __future__ import annotations

from types import SimpleNamespace

import pytest

from pen.db import categories_repo, parse_failures_repo
from pen.db.categories_repo import CategoryMaps


class _FakeResult:
    def __init__(self, rows):
        self._rows = rows

    def __iter__(self):
        return iter(self._rows)


class _FakeConn:
    def __init__(self, rows):
        self._rows = rows

    def execute(self, _stmt):
        return _FakeResult(self._rows)


def _rows():
    return [
        SimpleNamespace(subcategory_id=11, subcategory_name="Cooking Essentials",
                        category_id=1, category_name="Groceries"),
        SimpleNamespace(subcategory_id=12, subcategory_name="Fresh Vegetables",
                        category_id=1, category_name="Groceries"),
        SimpleNamespace(subcategory_id=99, subcategory_name="Other",
                        category_id=10, category_name="Other"),
    ]


def test_load_builds_sub_to_main_and_id_maps() -> None:
    maps = categories_repo.load(_FakeConn(_rows()))
    assert isinstance(maps, CategoryMaps)
    assert maps.sub_to_main["Cooking Essentials"] == "Groceries"
    assert maps.ids_for("Fresh Vegetables") == (12, 1)
    assert set(maps.valid_subcategories) == {"Cooking Essentials", "Fresh Vegetables", "Other"}


def test_load_builds_grouped_category_block() -> None:
    maps = categories_repo.load(_FakeConn(_rows()))
    assert "Groceries: Cooking Essentials, Fresh Vegetables" in maps.category_block
    assert "Other: Other" in maps.category_block


def test_load_empty_taxonomy_raises() -> None:
    with pytest.raises(RuntimeError):
        categories_repo.load(_FakeConn([]))


def test_reason_constants_match_spec_taxonomy() -> None:
    assert parse_failures_repo.REASON_RATE_LIMIT == "rate_limit"
    assert parse_failures_repo.REASON_INVALID_JSON == "invalid_json"
    assert parse_failures_repo.REASON_MISSING_ORDER_ID == "missing_order_id"
    assert parse_failures_repo.REASON_VALIDATION_ERROR == "validation_error"
    assert parse_failures_repo.REASON_LLM_ERROR == "llm_error"
