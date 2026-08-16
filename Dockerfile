FROM gradle:8.14.3-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle shadowJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /home/gradle/project/build/libs/*-all.jar /app/app.jar
COPY --from=build /home/gradle/project/src/main/resources/db/migration /app/db/migration
RUN mkdir -p /app/secrets && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
