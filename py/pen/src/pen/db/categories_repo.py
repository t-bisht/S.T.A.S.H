"""Repository for the two-level item taxonomy (``item_categories`` + ``item_subcategories``).

Builds the per-pass :class:`CategoryMaps` that the Swiggy parser needs (spec §9). The seeded
``05`` rows are the single source of truth — NOT the hardcoded prototype dict. Rebuilt fresh
each pass so a taxonomy edit (new leaf, rename) is picked up without a restart.

The LLM only ever emits a *subcategory* leaf; the main category is derived in code via
``SUB_TO_MAIN`` (``subcategory_name`` is globally unique, so sub→main is a function and the
rollup can never drift — spec §9). Both IDs are resolved here and frozen onto the stored row.
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.engine import Connection

from pen.db.models import item_categories, item_subcategories

__all__ = ["CategoryMaps", "load"]


@dataclass(frozen=True, slots=True)
class CategoryMaps:
    """Per-pass taxonomy lookups derived from the DB seed (spec §9).

    Attributes:
        sub_to_main: ``subcategory_name → category_name`` (derived; the rollup).
        valid_subcategories: All valid leaf names — the only values the model may emit.
        name_to_ids: ``subcategory_name → (subcategory_id, category_id)`` — the IDs frozen onto
            each stored item row at parse time.
        category_block: Grouped ``"{main}: {sub, …}"`` text injected into the prompt as context
            to help the model place borderline items.
    """

    sub_to_main: dict[str, str]
    valid_subcategories: list[str]
    name_to_ids: dict[str, tuple[int, int]]
    category_block: str

    def ids_for(self, subcategory_name: str) -> tuple[int, int]:
        """Return ``(subcategory_id, category_id)`` for a leaf name.

        Raises:
            KeyError: If the name is not a valid leaf — callers coerce off-list leaves to
                ``"Other"`` *before* resolving (spec §8.2), so this should never fire in the
                normal path.
        """
        return self.name_to_ids[subcategory_name]


def load(conn: Connection) -> CategoryMaps:
    """Build :class:`CategoryMaps` from the current taxonomy seed.

    Args:
        conn: Live DB connection (read).

    Returns:
        A fully-populated :class:`CategoryMaps`.

    Raises:
        RuntimeError: If the taxonomy is empty (``05`` not applied) — a pass must not start
            against an empty taxonomy (it would park every row), so this surfaces as a FAILED
            run, not silent parking.
    """
    stmt = (
        select(
            item_subcategories.c.subcategory_id,
            item_subcategories.c.subcategory_name,
            item_categories.c.category_id,
            item_categories.c.category_name,
        )
        .select_from(
            item_subcategories.join(
                item_categories,
                item_subcategories.c.category_id == item_categories.c.category_id,
            )
        )
        .order_by(item_categories.c.category_name, item_subcategories.c.subcategory_name)
    )

    sub_to_main: dict[str, str] = {}
    name_to_ids: dict[str, tuple[int, int]] = {}
    grouped: dict[str, list[str]] = {}

    for row in conn.execute(stmt):
        sub_to_main[row.subcategory_name] = row.category_name
        name_to_ids[row.subcategory_name] = (row.subcategory_id, row.category_id)
        grouped.setdefault(row.category_name, []).append(row.subcategory_name)

    if not name_to_ids:
        raise RuntimeError(
            "item taxonomy is empty — apply 05_item_categories.sql before running pen"
        )

    category_block = "\n".join(
        f"{main}: {', '.join(subs)}" for main, subs in grouped.items()
    )
    return CategoryMaps(
        sub_to_main=sub_to_main,
        valid_subcategories=list(name_to_ids.keys()),
        name_to_ids=name_to_ids,
        category_block=category_block,
    )
