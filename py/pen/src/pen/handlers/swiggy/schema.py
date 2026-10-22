"""Swiggy DTOs + the handler-local validation error (spec §8).

``ScanRow`` is a candidate mail read from ``swiggy_instamart_scan``. ``ParsedOrder`` /
``ParsedItem`` are the validated, post-processed result (taxonomy IDs already resolved) that the
persister maps onto the shared retail records. :class:`SwiggyValidationError` carries the §4.4
park ``reason`` so the handler parks with the right code and the raw output for triage.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

__all__ = ["ParsedItem", "ParsedOrder", "ScanRow", "SwiggyValidationError"]


@dataclass(frozen=True, slots=True)
class ScanRow:
    """One unparsed candidate mail from ``swiggy_instamart_scan`` (spec §4.2).

    Attributes:
        id: ``swiggy_instamart_scan.id`` — the source row id (park key + writeback target).
        user_id: Owner of the mail.
        message_id: Gmail message id (cross-ref).
        received_at: When the mail arrived — becomes ``order_timestamp`` (spec §7/§8.3).
        mail_body_text: HTML-stripped clean text; the LLM input.
    """

    id: int
    user_id: UUID
    message_id: str
    received_at: datetime
    mail_body_text: str


@dataclass(frozen=True, slots=True)
class ParsedItem:
    """A validated, categorised line item (taxonomy IDs resolved in post-processing)."""

    item_name: str
    quantity: float | None
    item_price: float | None       # the LLM's per-line total_price
    subcategory: str               # a valid leaf (off-list already coerced to "Other")
    sub_category_id: int
    category_id: int


@dataclass(frozen=True, slots=True)
class ParsedOrder:
    """A validated, post-processed order ready for persistence (spec §8.3)."""

    order_id: str                  # guaranteed present + numeric by the validator (§8.2)
    order_location: str | None
    item_total_bill: float | None
    handling_fee: float | None
    delivery_fee: float | None     # LLM's delivery_partner_fee
    order_grand_total: float | None
    items: list[ParsedItem]


class SwiggyValidationError(Exception):
    """A post-LLM validation failure (spec §8.2). Carries the park reason + raw output.

    Attributes:
        reason: A ``parse_failures_repo.REASON_*`` code (``missing_order_id`` or
            ``validation_error``).
        llm_raw: The model's JSON (serialised) for triage in ``parse_failures.llm_raw``.
    """

    def __init__(self, message: str, *, reason: str, llm_raw: str | None = None) -> None:
        super().__init__(message)
        self.reason = reason
        self.llm_raw = llm_raw
