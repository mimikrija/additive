FROM node:24.13.0-bookworm-slim AS node

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY --from=node /usr/local/ /usr/local/
COPY gradlew build.gradle.kts settings.gradle.kts package.json package-lock.json ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN --mount=type=secret,id=build_ca \
    set -e; \
    if [ -f /run/secrets/build_ca ]; then \
        keytool -importcert -noprompt -cacerts -storepass changeit -alias additive-build-ca -file /run/secrets/build_ca; \
        export NODE_EXTRA_CA_CERTS=/run/secrets/build_ca; \
    fi; \
    ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/additive-*.jar /app/additive.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/additive.jar"]
