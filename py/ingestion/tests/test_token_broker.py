"""Unit tests for the token broker and the refresh-once-on-401 helper."""

from __future__ import annotations

from uuid import uuid4

import pytest

from ingestion.gmail.client import GmailUnauthorized
from ingestion.tokens.broker import UserEngineTokenBroker, with_gmail_token
from ingestion.tokens.exceptions import ReauthRequired, TokenError


class _FakeResponse:
    """Minimal stand-in for ``requests.Response``."""

    def __init__(self, status_code: int, body: dict | None = None) -> None:
        self.status_code = status_code
        self._body = body

    def json(self) -> dict:
        if self._body is None:
            raise ValueError("no JSON body")
        return self._body


class _FakeSession:
    """Returns a queued response per call and records the requests made."""

    def __init__(self, responses: list[_FakeResponse]) -> None:
        self._responses = list(responses)
        self.calls: list[tuple[str, str]] = []

    def request(self, method: str, url: str, headers: dict, timeout: int) -> _FakeResponse:
        self.calls.append((method, url))
        return self._responses.pop(0)


def _broker(responses: list[_FakeResponse]) -> tuple[UserEngineTokenBroker, _FakeSession]:
    session = _FakeSession(responses)
    broker = UserEngineTokenBroker("http://localhost:7083", "secret", session=session)
    return broker, session


def test_get_access_token_returns_token_on_200() -> None:
    broker, session = _broker([_FakeResponse(200, {"access_token": "abc", "token_type": "Bearer"})])
    user_id = uuid4()

    token = broker.get_access_token(user_id)

    assert token == "abc"
    method, url = session.calls[0]
    assert method == "GET"
    assert url == f"http://localhost:7083/internal/users/{user_id}/oauth/google/access-token"


def test_409_raises_reauth_required() -> None:
    broker, _ = _broker([_FakeResponse(409, {"error": "login_required"})])
    with pytest.raises(ReauthRequired) as exc:
        broker.get_access_token(uuid4())
    assert exc.value.code == "login_required"


def test_404_raises_token_error_with_account_not_found_code() -> None:
    broker, _ = _broker([_FakeResponse(404, {"error": "account_not_found"})])
    with pytest.raises(TokenError) as exc:
        broker.get_access_token(uuid4())
    assert exc.value.code == "account_not_found"
    assert not isinstance(exc.value, ReauthRequired)


def test_503_refresh_unavailable_is_token_error() -> None:
    broker, _ = _broker([_FakeResponse(503, {"error": "refresh_unavailable"})])
    with pytest.raises(TokenError) as exc:
        broker.refresh(uuid4())
    assert exc.value.code == "refresh_unavailable"


def test_200_without_access_token_is_invalid_response() -> None:
    broker, _ = _broker([_FakeResponse(200, {"token_type": "Bearer"})])
    with pytest.raises(TokenError) as exc:
        broker.get_access_token(uuid4())
    assert exc.value.code == "invalid_broker_response"


# --- with_gmail_token helper -------------------------------------------------


class _StubBroker:
    """Hands out scripted tokens and counts refreshes."""

    def __init__(self, *, access: str, refreshed: str | None = None) -> None:
        self._access = access
        self._refreshed = refreshed
        self.refresh_calls = 0

    def get_access_token(self, user_id):
        return self._access

    def refresh(self, user_id):
        self.refresh_calls += 1
        if self._refreshed is None:
            raise ReauthRequired()
        return self._refreshed


def test_with_gmail_token_passes_token_through_on_success() -> None:
    broker = _StubBroker(access="tok1")
    result = with_gmail_token(broker, uuid4(), lambda token: f"called:{token}")
    assert result == "called:tok1"
    assert broker.refresh_calls == 0


def test_with_gmail_token_refreshes_once_on_401_then_retries() -> None:
    broker = _StubBroker(access="stale", refreshed="fresh")
    seen: list[str] = []

    def call(token: str) -> str:
        seen.append(token)
        if token == "stale":
            raise GmailUnauthorized("401")
        return "ok"

    assert with_gmail_token(broker, uuid4(), call) == "ok"
    assert seen == ["stale", "fresh"]
    assert broker.refresh_calls == 1


def test_with_gmail_token_surfaces_reauth_when_refresh_token_dead() -> None:
    broker = _StubBroker(access="stale", refreshed=None)  # refresh() raises ReauthRequired

    def call(token: str) -> str:
        raise GmailUnauthorized("401")

    with pytest.raises(ReauthRequired):
        with_gmail_token(broker, uuid4(), call)
    assert broker.refresh_calls == 1
