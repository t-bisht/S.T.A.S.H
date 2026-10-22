"""The Swiggy extraction prompt (spec §8.1, prototype cell 18).

Interpolates the per-pass ``category_block`` + ``VALID_SUBCATEGORIES`` (both from the DB
taxonomy, not a hardcoded dict) and the mail body. The model emits only a subcategory *leaf*;
the main category is derived in code (spec §9). Output schema is strict JSON — see the spec.
"""

from __future__ import annotations

from collections.abc import Sequence

__all__ = ["SYSTEM_PROMPT", "build_prompt"]

# Spec §5.4 — kept out of the provider so each handler frames its own task.
SYSTEM_PROMPT = "You are a precise information extraction and classification system."

# The strict output schema the model must return (spec §8.1).
_SCHEMA = """{
  "order_location": null,
  "order_id": null,
  "items": [
    { "name": null, "quantity": null, "total_price": null, "subcategory": null }
  ],
  "summary": {
    "item_bill": null,
    "handling_fee": null,
    "delivery_partner_fee": null,
    "grand_total": null
  }
}"""


def build_prompt(category_block: str, valid_subcategories: Sequence[str], email_text: str) -> str:
    """Build the user prompt for one mail.

    Args:
        category_block: Grouped ``"{main}: {sub, …}"`` context lines (from the taxonomy).
        valid_subcategories: The allowed leaf names — the ONLY values the model may emit.
        email_text: The HTML-stripped mail body.

    Returns:
        The fully-interpolated prompt string.
    """
    return f"""Extract the order items from the email and assign each item a subcategory.

Return ONLY valid JSON, exactly this structure:

{_SCHEMA}

Category reference (main: subcategories) — use ONLY to understand the groupings:
{category_block}

Rules:
- "subcategory" MUST be exactly one value from this allowed list:
  {list(valid_subcategories)}
- Do NOT output a main category. Only the subcategory leaf.
- If no subcategory fits, use "Other".
- Preserve product names as they appear in the email.
- Do not invent information.
- Remove currency symbol and return only the amount as a float.
- Extract quantity when available.
- Extract prices when available.
- Do not calculate totals.
- If a value is not present, use null.
- Extract the location from the "Deliver To" field.
- Do not invent items. Return JSON only.

EMAIL:
{email_text}
"""
