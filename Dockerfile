# ---- build stage -----------------------------------------------------------
# Tests (and the 90% coverage gate) run in CI with `mvn verify`; the image build
# only packages the jar.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
# Copy the POM first so dependency downloads stay cached until it changes.
COPY pom.xml .
# Pre-fetch dependencies into their own cached layer.
RUN mvn -B -q dependency:go-offline
# Copy the sources and package the jar.
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime stage ---------------------------------------------------------
FROM eclipse-temurin:25-jre
# Run as an unprivileged user.
RUN useradd --system --uid 10001 app
WORKDIR /app
# Copy only the built jar from the build stage.
COPY --from=build /workspace/target/pokemon-rankings-*.jar app.jar
USER app
EXPOSE 8080
# Production profile by default: structured logs, API docs off.
ENV SPRING_PROFILES_ACTIVE=prod
# Size the heap to 75% of the container memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
