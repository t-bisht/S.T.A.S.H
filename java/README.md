# java — Stash backend services

Gradle multi-project. Shared build logic in `buildSrc/`, versions in
`gradle/libs.versions.toml`. Group id `org.tb.stash`. Java 21.

## Subprojects

- `user_engine` — user profile management + login (Google OAuth, session lifecycle). Port 7083.
- `admin_engine` — admin console + product-catalog management (static-credential auth, catalog
  review/PATCH with cascade, taxonomy add, audit log). Serves the `web_admin` bundle. Port 7084
  (loopback-only in compose).

## Build

```bash
cd java
./gradlew build                              # everything
./gradlew :user_engine:bootJar               # runnable fat JAR
./gradlew format                             # Spotless on all subprojects
```

## Key Generation 

- Command to generate 
  - (STASH_TOKEN_ENC_KEY) openssl rand -base64 32