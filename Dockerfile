# Сборка
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# Тесты (в т.ч. AdminApiLoginTest) запускаются внутри контейнера
RUN mvn package -B

# Запуск
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /build/target/basketbot-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
