FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system agendapro && useradd --system --gid agendapro --home-dir /app agendapro
WORKDIR /app
COPY --from=build /build/target/agendapro-1.0.0.jar app.jar
USER agendapro
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
