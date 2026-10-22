"""Provider factory + JSON extraction + error typing (spec §5)."""

from __future__ import annotations

import pytest

from pen.config import ConfigError
from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid
from pen.llm.factory import build_provider
from pen.llm.openai_compatible import OpenAICompatibleProvider, _parse_json
from pen.parse_handler_config import LLMSettings


def _settings(provider: str = "groq", api_key_env: str = "GROQ_API_KEY") -> LLMSettings:
    return LLMSettings(
        provider=provider,
        base_url="https://api.groq.com/openai/v1",
        model="openai/gpt-oss-20b",
        api_key_env=api_key_env,
        reasoning_effort="low",
    )


_KEYS = {"GROQ_API_KEY": "secret-key", "OPENROUTER_API_KEY": "other-key"}


def test_factory_returns_openai_compatible_for_known_provider() -> None:
    provider = build_provider(_settings("groq"), _KEYS)
    assert isinstance(provider, OpenAICompatibleProvider)


def test_factory_still_builds_for_unknown_label() -> None:
    # Unknown label is cosmetic (base_url routes) — must not crash.
    provider = build_provider(_settings("some-new-compatible-host"), _KEYS)
    assert isinstance(provider, OpenAICompatibleProvider)


def test_factory_per_handler_key_lookup_by_env_name() -> None:
    # A handler on OpenRouter resolves ITS key by name — independent of Groq's.
    provider = build_provider(_settings("openrouter", "OPENROUTER_API_KEY"), _KEYS)
    assert isinstance(provider, OpenAICompatibleProvider)


def test_factory_missing_key_raises_config_error() -> None:
    with pytest.raises(ConfigError):
        build_provider(_settings("nvidia", "NVIDIA_API_KEY"), _KEYS)


def test_factory_keyless_endpoint_builds_without_a_key() -> None:
    # A local model needs no key (empty api_key_env) — must build fine.
    provider = build_provider(_settings("local", ""), {})
    assert isinstance(provider, OpenAICompatibleProvider)


def test_parse_json_plain() -> None:
    assert _parse_json('{"a": 1}') == {"a": 1}


def test_parse_json_strips_code_fence() -> None:
    assert _parse_json('```json\n{"a": 1}\n```') == {"a": 1}


def test_parse_json_slices_first_brace_span() -> None:
    assert _parse_json('here you go: {"a": 1} thanks') == {"a": 1}


def test_parse_json_invalid_raises_response_invalid_with_raw() -> None:
    with pytest.raises(LLMResponseInvalid) as exc:
        _parse_json("not json at all")
    assert exc.value.raw == "not json at all"


def test_parse_json_non_object_raises() -> None:
    # A bare JSON array is valid JSON but not the object schema we require.
    with pytest.raises(LLMResponseInvalid):
        _parse_json("[1, 2, 3]")


def test_exception_hierarchy() -> None:
    # retry.py relies on this: both are LLMError, but it catches them in the right order.
    assert issubclass(LLMRateLimited, LLMError)
    assert issubclass(LLMResponseInvalid, LLMError)
