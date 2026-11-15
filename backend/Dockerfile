# syntax=docker/dockerfile:1.7
# Shared multi-stage build for every backend module. Build context: backend/ ; pass --build-arg MODULE=<module>.
FROM eclipse-temurin:25-jdk AS build
ARG MODULE
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY placehub-common placehub-common
COPY discovery-server discovery-server
COPY api-gateway api-gateway
COPY user-service user-service
COPY job-service job-service
COPY application-service application-service
COPY notification-service notification-service
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/${MODULE}-*.jar /build/app.jar

FROM eclipse-temurin:25-jre
ARG MODULE
LABEL org.opencontainers.image.title="placehub-${MODULE}"
# curl is used by the compose healthchecks (not present in temurin:25-jre).
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system placehub && useradd --system --gid placehub --no-create-home placehub
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER placehub
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
