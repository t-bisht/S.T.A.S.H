"""The Swiggy Instamart parse handler (spec §8).

``source_name = "swiggy_instamart"``; reads ``swiggy_instamart_scan``. Self-contained: its
scan query, prompt, validator, post-processor, DTO→row mapping, and sub-orchestration all live
here; nothing shared is edited to add or change it.
"""

from __future__ import annotations

from pen.handlers.swiggy.handler import SwiggyHandler

__all__ = ["SwiggyHandler"]
