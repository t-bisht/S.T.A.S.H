# admin_engine — multi-stage Spring Boot image.
#
# Prereq (from repo root):
#   cd web_admin && npm run build            # then copy dist/ -> src/main/resources/static/ (§12.1)
#   cd java && ./docker_build.sh admin_engine
# Output: admin_engine/build/libs/admin-engine-0.1.0-SNAPSHOT.jar
# Build context: java/admin_engine/ (see .dockerignore)

FROM eclipse-temurin:21-jre-alpine AS layertools
WORKDIR /extract
COPY build/libs/admin-engine-*.jar app.jar
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

EXPOSE 7084

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:7084/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
