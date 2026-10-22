"""The "parse" stage — prompt → LLM → validate → post-process (spec §8.1–8.3).

Owns the source-specific intelligence. The flow for one candidate row:

1. build the prompt (:mod:`pen.handlers.swiggy.prompt`) from the mail body + taxonomy context,
2. call the injected :class:`~pen.llm.base.LLMProvider` (``ctx.llm``) — provider-agnostic,
3. validate the JSON (spec §8.2): ``order_id`` MUST be present + numeric; off-list subcategory
   is coerced to ``"Other"`` (not a failure); structural/field problems raise
   :class:`SwiggyValidationError` with the right park reason,
4. post-process (spec §8.3): derive ``(sub_id, cat_id)`` from the taxonomy (main category is
   NEVER taken from the model); currency strings → floats; ``order_timestamp`` is the source
   row's ``received_at``, not the LLM's.

LLM transport/rate-limit errors propagate to the shared retry wrapper; validation errors are
permanent (parked). This function performs no DB writes.
"""

from __future__ import annotations

import json
import logging
import re

from pen.db import parse_failures_repo as failures
from pen.db.categories_repo import CategoryMaps
from pen.handlers.swiggy.prompt import SYSTEM_PROMPT, build_prompt
from pen.handlers.swiggy.schema import ParsedItem, ParsedOrder, ScanRow, SwiggyValidationError
from pen.llm.base import LLMProvider

__all__ = ["parse", "validate_and_build"]

_OTHER_SUBCATEGORY = "Other"
# A leading currency code/word ("Rs", "Rs.", "INR") to drop before parsing a money string.
_CURRENCY_WORD_RE = re.compile(r"(?i)^\s*(?:rs\.?|inr)\s*")
# Currency symbols, thousands separators and whitespace to strip so "₹1,272.00" → "1272.00".
_CURRENCY_SYMBOL_RE = re.compile(r"[₹$€£,\s]")


def parse(
    llm: LLMProvider,
    cat_maps: CategoryMaps,
    row: ScanRow,
    log: logging.LoggerAdapter | logging.Logger,
) -> ParsedOrder:
    """Parse one candidate mail into a :class:`ParsedOrder` (no DB writes).

    Args:
        llm: The handler's own LLM provider (each handler may use a different one — spec §5).
        cat_maps: The per-pass taxonomy maps.
        row: The candidate mail.
        log: Per-row logger (carries user/message context).

    Returns:
        The validated, post-processed order.

    Raises:
        LLMRateLimited | LLMResponseInvalid | LLMError: From the provider (handled upstream).
        SwiggyValidationError: On a §8.2 validation failure (parked by the handler).
    """
    prompt = build_prompt(cat_maps.category_block, cat_maps.valid_subcategories, row.mail_body_text)
    raw = llm.complete_json(SYSTEM_PROMPT, prompt)
    return validate_and_build(raw, cat_maps, row, log)


def validate_and_build(
    raw: dict,
    cat_maps: CategoryMaps,
    row: ScanRow,
    log: logging.LoggerAdapter | logging.Logger,
) -> ParsedOrder:
    """Validate the model's JSON (spec §8.2) and post-process it (spec §8.3).

    Pure (no I/O) so it is directly unit-testable with a fixture dict.
    """
    raw_json = _safe_dumps(raw)

    order_id = _validate_order_id(raw.get("order_id"), raw_json)

    items_raw = raw.get("items")
    summary_raw = raw.get("summary")
    if not isinstance(items_raw, list):
        raise SwiggyValidationError(
            "'items' missing or not a list", reason=failures.REASON_VALIDATION_ERROR, llm_raw=raw_json
        )
    if not isinstance(summary_raw, dict):
        raise SwiggyValidationError(
            "'summary' missing or not an object",
            reason=failures.REASON_VALIDATION_ERROR,
            llm_raw=raw_json,
        )

    items = [_build_item(item, cat_maps, raw_json, log) for item in items_raw]

    return ParsedOrder(
        order_id=order_id,
        order_location=_opt_str(raw.get("order_location")),
        item_total_bill=_to_float(summary_raw.get("item_bill"), "summary.item_bill", raw_json),
        handling_fee=_to_float(summary_raw.get("handling_fee"), "summary.handling_fee", raw_json),
        delivery_fee=_to_float(
            summary_raw.get("delivery_partner_fee"), "summary.delivery_partner_fee", raw_json
        ),
        order_grand_total=_to_float(
            summary_raw.get("grand_total"), "summary.grand_total", raw_json
        ),
        items=items,
    )


def _validate_order_id(value: object, raw_json: str) -> str:
    """Enforce spec §8.2: ``order_id`` present, non-empty, numeric. Else park missing_order_id."""
    text = "" if value is None else str(value).strip()
    if not text or not text.isdigit():
        raise SwiggyValidationError(
            f"order_id missing/empty/non-numeric: {value!r}",
            reason=failures.REASON_MISSING_ORDER_ID,
            llm_raw=raw_json,
        )
    return text


def _build_item(
    item: object,
    cat_maps: CategoryMaps,
    raw_json: str,
    log: logging.LoggerAdapter | logging.Logger,
) -> ParsedItem:
    """Validate + post-process one line item (spec §8.2/§8.3)."""
    if not isinstance(item, dict):
        raise SwiggyValidationError(
            "item is not an object", reason=failures.REASON_VALIDATION_ERROR, llm_raw=raw_json
        )

    name = _opt_str(item.get("name"))
    if not name:
        raise SwiggyValidationError(
            "item missing 'name'", reason=failures.REASON_VALIDATION_ERROR, llm_raw=raw_json
        )

    subcategory = _coerce_subcategory(item.get("subcategory"), cat_maps, name, log)
    sub_id, cat_id = cat_maps.ids_for(subcategory)

    return ParsedItem(
        item_name=name,
        quantity=_to_float(item.get("quantity"), "item.quantity", raw_json),
        item_price=_to_float(item.get("total_price"), "item.total_price", raw_json),
        subcategory=subcategory,
        sub_category_id=sub_id,
        category_id=cat_id,
    )


def _coerce_subcategory(
    value: object,
    cat_maps: CategoryMaps,
    item_name: str,
    log: logging.LoggerAdapter | logging.Logger,
) -> str:
    """Coerce an off-list / missing subcategory to ``"Other"`` (spec §8.2 — warn, not fail)."""
    text = _opt_str(value)
    if text and text in cat_maps.sub_to_main:
        return text
    log.warning("off-list subcategory %r for item %r — coercing to 'Other'", value, item_name)
    return _OTHER_SUBCATEGORY


def _to_float(value: object, field_name: str, raw_json: str) -> float | None:
    """Coerce a currency/number value to float, or ``None`` when absent (spec §8.2).

    Already-numeric values pass through; strings are stripped of currency symbols/commas. A
    non-empty, non-numeric value is a validation failure (parked ``validation_error``).
    """
    if value is None:
        return None
    if isinstance(value, bool):  # guard: bool is an int subclass, never a money value
        raise SwiggyValidationError(
            f"{field_name} is a bool: {value!r}",
            reason=failures.REASON_VALIDATION_ERROR,
            llm_raw=raw_json,
        )
    if isinstance(value, (int, float)):
        return float(value)

    text = str(value).strip()
    if not text:
        return None
    # Drop a currency word/code prefix, then the symbols/separators — but keep any other junk
    # so a non-numeric value (e.g. "free", "12abc") fails loudly rather than coercing to null.
    cleaned = _CURRENCY_SYMBOL_RE.sub("", _CURRENCY_WORD_RE.sub("", text)).strip()
    if not cleaned:
        return None
    try:
        return float(cleaned)
    except ValueError as exc:
        raise SwiggyValidationError(
            f"{field_name} not numeric: {value!r}",
            reason=failures.REASON_VALIDATION_ERROR,
            llm_raw=raw_json,
        ) from exc


def _opt_str(value: object) -> str | None:
    """Return a trimmed string, or ``None`` for null/blank."""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _safe_dumps(raw: dict) -> str:
    """Serialise the model output for triage storage; never raises."""
    try:
        return json.dumps(raw, ensure_ascii=False)[:8000]
    except (TypeError, ValueError):
        return str(raw)[:8000]
