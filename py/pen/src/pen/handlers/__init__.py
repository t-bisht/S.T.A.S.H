"""Parse handlers — one self-contained package per source (spec §3).

Everything for a source (scan query, prompt, validation, post-processing, DTO→row mapping)
lives under its own ``handlers/<src>/`` package. Adding a source touches nothing shared except
one line in ``core.registry`` and a ``scan_sources`` row.
"""

from __future__ import annotations

__all__: list[str] = []
