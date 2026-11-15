
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8091

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:8091/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
