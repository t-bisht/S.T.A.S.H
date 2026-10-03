# user_engine

User profile + login management service. Spring Boot 3.3 on Java 21. Port 8083.

## Run

```bash
./gradlew :user_engine:bootRun
./gradlew :user_engine:bootJar     # fat JAR → build/libs/user-engine-*.jar
./gradlew :user_engine:test
```

## Health

`GET http://localhost:8083/actuator/health`
