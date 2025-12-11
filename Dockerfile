FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/adjudicator-client-1.0.4.jar app.jar
COPY agent.env agent.env

ENTRYPOINT ["java", "-jar", "app.jar"]
