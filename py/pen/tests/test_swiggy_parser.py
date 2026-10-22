"""Validator + post-processor (spec §8.2–8.3): order_id reject, sub→main→id, off-list→Other."""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from uuid import uuid4

import pytest

from pen.db import parse_failures_repo as failures
from pen.db.categories_repo import CategoryMaps
from pen.handlers.swiggy.parser import validate_and_build
from pen.handlers.swiggy.schema import ScanRow, SwiggyValidationError

log = logging.getLogger("test")


def _cat_maps() -> CategoryMaps:
    name_to_ids = {
        "Cooking Essentials": (11, 1),
        "Fresh Vegetables": (12, 1),
        "Other": (99, 10),
    }
    sub_to_main = {
        "Cooking Essentials": "Groceries",
        "Fresh Vegetables": "Groceries",
        "Other": "Other",
    }
    return CategoryMaps(
        sub_to_main=sub_to_main,
        valid_subcategories=list(sub_to_main),
        name_to_ids=name_to_ids,
        category_block="Groceries: Cooking Essentials, Fresh Vegetables\nOther: Other",
    )


def _row() -> ScanRow:
    return ScanRow(
        id=1,
        user_id=uuid4(),
        message_id="m1",
        received_at=datetime(2026, 10, 10, tzinfo=UTC),
        mail_body_text="body",
    )


def _valid_raw() -> dict:
    return {
        "order_id": "250392584970642",
        "order_location": "Sobha Sunflower",
        "items": [
            {"name": "Crystal Sugar", "quantity": 1, "total_price": "₹69.00",
             "subcategory": "Cooking Essentials"},
            {"name": "Ginger", "quantity": "2", "total_price": 88.0,
             "subcategory": "Fresh Vegetables"},
        ],
        "summary": {
            "item_bill": "₹1,456.00", "handling_fee": "₹12.00",
            "delivery_partner_fee": "₹0.00", "grand_total": "₹1468.00",
        },
    }


def test_valid_order_parses_and_resolves_ids() -> None:
    parsed = validate_and_build(_valid_raw(), _cat_maps(), _row(), log)
    assert parsed.order_id == "250392584970642"
    assert parsed.order_location == "Sobha Sunflower"
    assert parsed.item_total_bill == 1456.0
    assert parsed.handling_fee == 12.0
    assert parsed.delivery_fee == 0.0
    assert parsed.order_grand_total == 1468.0
    assert [i.item_name for i in parsed.items] == ["Crystal Sugar", "Ginger"]
    # currency stripped → float
    assert parsed.items[0].item_price == 69.0
    assert parsed.items[1].quantity == 2.0
    # sub → (sub_id, cat_id) resolved from the maps (main never from the model)
    assert (parsed.items[0].sub_category_id, parsed.items[0].category_id) == (11, 1)


@pytest.mark.parametrize("bad", [None, "", "  ", "ABC123", "12a"])
def test_order_id_missing_or_nonnumeric_parks_missing_order_id(bad) -> None:
    raw = _valid_raw()
    raw["order_id"] = bad
    with pytest.raises(SwiggyValidationError) as exc:
        validate_and_build(raw, _cat_maps(), _row(), log)
    assert exc.value.reason == failures.REASON_MISSING_ORDER_ID
    assert exc.value.llm_raw is not None  # raw kept for triage


def test_off_list_subcategory_coerced_to_other_not_a_failure() -> None:
    raw = _valid_raw()
    raw["items"][0]["subcategory"] = "Spaceship Parts"  # not a valid leaf
    parsed = validate_and_build(raw, _cat_maps(), _row(), log)
    assert parsed.items[0].subcategory == "Other"
    assert (parsed.items[0].sub_category_id, parsed.items[0].category_id) == (99, 10)


def test_missing_items_list_parks_validation_error() -> None:
    raw = _valid_raw()
    del raw["items"]
    with pytest.raises(SwiggyValidationError) as exc:
        validate_and_build(raw, _cat_maps(), _row(), log)
    assert exc.value.reason == failures.REASON_VALIDATION_ERROR


def test_item_without_name_parks_validation_error() -> None:
    raw = _valid_raw()
    raw["items"][0]["name"] = None
    with pytest.raises(SwiggyValidationError) as exc:
        validate_and_build(raw, _cat_maps(), _row(), log)
    assert exc.value.reason == failures.REASON_VALIDATION_ERROR


def test_non_numeric_price_parks_validation_error() -> None:
    raw = _valid_raw()
    raw["items"][0]["total_price"] = "free"
    with pytest.raises(SwiggyValidationError) as exc:
        validate_and_build(raw, _cat_maps(), _row(), log)
    assert exc.value.reason == failures.REASON_VALIDATION_ERROR


def test_null_numeric_fields_pass_as_none() -> None:
    raw = _valid_raw()
    raw["items"][0]["quantity"] = None
    raw["summary"]["handling_fee"] = None
    parsed = validate_and_build(raw, _cat_maps(), _row(), log)
    assert parsed.items[0].quantity is None
    assert parsed.handling_fee is None
