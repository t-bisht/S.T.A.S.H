"""Shared DB layer for pen — SQLAlchemy Core engine, table definitions, and cross-handler repos.

Only *cross-handler* tables live here (``scan_sources``, categories, ``parse_scan_run_metadata``,
``parse_failures``, the ``online_retail_*`` sink, and the ingestion-owned scan tables pen reads /
writes back). A single source's own scan query and DTO→row mapping live in its handler package,
not here (spec §2.2).
"""

from __future__ import annotations

__all__: list[str] = []
