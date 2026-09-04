FROM maven:3.9.6-eclipse-temurin-21 AS builder

ARG APP_VERSION
ARG BUILD_DATE

LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL maintainer="OrderHub Team"

WORKDIR /app

COPY pom.xml ./pom.xml
COPY reactive-notification-service/pom.xml ./reactive-notification-service/pom.xml

WORKDIR /app/reactive-notification-service

RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

COPY reactive-notification-service/src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION
ARG BUILD_DATE

LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL description="payment-service for OrderHub"
LABEL org.opencontainers.image.source="https://github.com/Oleborn/OrderHub"
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /app/reactive-notification-service/target/*.jar app.jar
EXPOSE 8199
USER spring:spring
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8099/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]