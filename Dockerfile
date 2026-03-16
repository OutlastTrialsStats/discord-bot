FROM eclipse-temurin:25-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=build /build/target/discord-bot-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
