"""Shared framework contracts + reusable stage helpers (spec §2.2, §11).

The engine owns the orchestration tiers, the provider, the retry/park policy, and the
cross-handler repos. A handler package owns only *its* scan query, prompt, validation,
post-processing, and DTO→row mapping. These ``core`` modules are the seam between the two.
"""

from __future__ import annotations

__all__: list[str] = []
