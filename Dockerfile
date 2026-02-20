# Сборка
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# Сборка (тесты при сборке образа пропущены — запускайте локально: mvn test)
ARG SKIP_TESTS=true
RUN mvn package -B -DskipTests=${SKIP_TESTS}

# Запуск
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /build/target/basketbot-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
