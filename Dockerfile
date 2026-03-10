# --- Build stage ---
FROM maven:3.9-eclipse-temurin-24 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn package -q -DskipTests \
    && mvn dependency:copy-dependencies -DoutputDirectory=target/libs -q

# --- Runtime stage ---
FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=build /app/target/raytracer-1.0-SNAPSHOT.jar app.jar
COPY --from=build /app/target/libs/ libs/
COPY src/main/resources/scenes/ scenes/

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-cp", "app.jar:libs/*", "Main"]
CMD ["scenes/scenes/scene4.scene"]
