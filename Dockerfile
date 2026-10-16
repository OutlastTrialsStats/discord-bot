FROM eclipse-temurin:25-alpine AS build

WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts version.txt ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/build/libs/discord-bot-*.jar app.jar

RUN chown app:app app.jar
USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx256m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
