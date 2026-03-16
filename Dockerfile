FROM eclipse-temurin:25-alpine AS build

WORKDIR /build

RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/discord-bot-*.jar app.jar

RUN chown app:app app.jar
USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "app.jar"]
