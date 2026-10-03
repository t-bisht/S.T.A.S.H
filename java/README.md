# java — Stash backend services

Gradle multi-project. Shared build logic in `buildSrc/`, versions in
`gradle/libs.versions.toml`. Group id `org.tb.stash`. Java 21.

## Subprojects

- `user_engine` — user profile management + login (Google OAuth, session lifecycle). Port 8083.

## Build

```bash
cd java
./gradlew build                              # everything
./gradlew :user_engine:bootJar               # runnable fat JAR
./gradlew format                             # Spotless on all subprojects
```
