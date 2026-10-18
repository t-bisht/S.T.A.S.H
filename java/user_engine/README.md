# user_engine

User profile + login management service. Spring Boot 3.3 on Java 21. Port 7083.

## Run

```bash
./gradlew :user_engine:bootRun
./gradlew :user_engine:bootJar     # fat JAR → build/libs/user-engine-*.jar
./gradlew :user_engine:test
```

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
