"""Pluggable per-source scan handlers and their registry.

The framework (scheduler/runner) is source-agnostic: it owns the watermark read, token
acquisition, metadata writes and per-user isolation. A source handler owns only its Gmail
query, its id-diff + fetch-new, and its mail→row mapping. New sources cost a handler plus a
registry entry (code) and a ``scan_sources`` row plus enrolments (DB) — see spec §2, §10.
"""

from __future__ import annotations

__all__: list[str] = []
