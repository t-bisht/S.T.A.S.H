# user_engine

User profile + login management service. Spring Boot 3.3 on Java 21. Port 7083.

## Run

```bash
./gradlew :user_engine:bootRun
./gradlew :user_engine:bootJar     # fat JAR → build/libs/user-engine-*.jar
./gradlew :user_engine:test
```

## Internal token endpoints (`/internal/**`)

Server-to-server only — for Stash backend engines (e.g. the Gmail scanner), **never** the SPA.
user_engine is the sole holder of the encrypted tokens and `STASH_TOKEN_ENC_KEY`; engines get a
plaintext Google access token from here instead of touching `user_oauth_accounts`.

**Auth** — every request must carry `X-Internal-Auth: $INTERNAL_SVC_TOKEN` (the inbound guard;
separate from `HIEMDALL_INTERNAL_TOKEN`, the secret user_engine sends *out* to Hiemdall).
Missing/blank/wrong → `401 unauthorized`. Startup fails if the secret is blank.

`{provider}` is a path segment for forward compatibility; v1 serves only `google` (exact,
lowercase). Any other value → `400 unsupported_provider`. All responses carry `Cache-Control:
no-store`; errors use `{ "error", "error_description" }`.

| Method & path | Purpose |
|---------------|---------|
| `GET /internal/users/{userId}/oauth/{provider}/access-token` | A token valid for at least `token-refresh.min-validity` (default `PT2M`). Fast path returns the stored token; if it is about to expire it is refreshed inline first. |
| `POST /internal/users/{userId}/oauth/{provider}/refresh` | Force a refresh after Gmail rejected the current token (401). Body ignored. |

Success body (`200`):

```json
{ "access_token": "ya29.…", "token_type": "Bearer", "expires_at": "2026-10-18T10:00:00Z" }
```

| Status | `error` | Meaning |
|--------|---------|---------|
| 400 | `invalid_request` | `userId` is not a UUID |
| 400 | `unsupported_provider` | `{provider}` is not `google` |
| 401 | `unauthorized` | `X-Internal-Auth` missing or invalid |
| 404 | `account_not_found` | user has no linked Google account |
| 409 | `login_required` | refresh token dead; the user's sessions were deleted — a fresh sign-in is required |
| 503 | `refresh_unavailable` | transient (upstream down, lock timeout, decrypt failure). Carries `Retry-After: 60` |

```bash
curl -s -H "X-Internal-Auth: $INTERNAL_SVC_TOKEN" \
  http://localhost:7083/internal/users/<userId>/oauth/google/access-token
curl -s -X POST -H "X-Internal-Auth: $INTERNAL_SVC_TOKEN" \
  http://localhost:7083/internal/users/<userId>/oauth/google/refresh
```

### Consumer contract (engines)

| Step | Engine behaviour |
|------|------------------|
| 1 | Get a token with `GET …/access-token` before each job (or reuse in memory until `expires_at`). |
| 2 | On Gmail 401, call `POST …/refresh` **at most once per job**, then retry the Gmail call once with the new token. |
| 3 | On 409 `login_required` from either endpoint: skip the job, log INFO with `userId`, no retry until a later scheduled run. |
| 4 | On 503: back off per `Retry-After`. |
| 5 | On 404: user has no linked Google account; skip, log INFO. |
| 6 | Never persist or log the token. Do not hold it past `expires_at`. |

## Health

`GET http://localhost:7083/actuator/health`

## Metrics

`GET http://localhost:7083/actuator/metrics` lists all meters. Token-refresh meters:

| Meter | Type | Tags |
|-------|------|------|
| `stash.token_refresh` | counter | `trigger` = `sweep` \| `on_demand` \| `access_token_fetch`; `outcome` = `refreshed` \| `skipped_fresh` \| `skipped_busy` \| `logged_out` \| `transient_failure` \| `config_error` |
| `stash.token_refresh.hiemdall` | timer | — |

```bash
curl -s localhost:7083/actuator/metrics/stash.token_refresh
curl -s "localhost:7083/actuator/metrics/stash.token_refresh?tag=trigger:sweep&tag=outcome:refreshed"
curl -s localhost:7083/actuator/metrics/stash.token_refresh.hiemdall
```

`stash.token_refresh` appears only after the first refresh attempt (404 before that). The endpoint is unauthenticated — do not expose port 7083 beyond localhost without securing `/actuator/**`.
