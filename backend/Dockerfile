# Build with the same Java major version as CI and the application.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline -DskipTests
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
# Leave room for JVM native memory in a small container. PORT is read by Spring.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
USER app
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
