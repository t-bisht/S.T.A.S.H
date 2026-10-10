# user_engine — multi-stage Spring Boot image.
#
# Prereq: build the fat JAR first from the Java repo root (/Users/obake/codebase/S.T.A.S.H/java):
#   ./gradlew :user_engine:bootJar --no-daemon
# Output: user_engine/build/libs/user-engine-0.1.0-SNAPSHOT.jar
#
# Build context is this directory (java/user_engine/); the COPY below picks the
# JAR out of build/libs/.

FROM eclipse-temurin:21-jre-alpine AS layertools
WORKDIR /extract
COPY build/libs/user-engine-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup --system appgroup \
 && adduser  --system --ingroup appgroup --no-create-home appuser

COPY --from=layertools /extract/dependencies/          ./
COPY --from=layertools /extract/spring-boot-loader/    ./
COPY --from=layertools /extract/snapshot-dependencies/ ./
COPY --from=layertools /extract/application/           ./

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
USER appuser

EXPOSE 7083

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:7083/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
