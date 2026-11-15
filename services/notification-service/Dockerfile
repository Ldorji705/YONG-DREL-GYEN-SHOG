FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8099

ENTRYPOINT ["java", "-jar", "app.jar"]
