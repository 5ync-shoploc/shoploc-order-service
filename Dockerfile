FROM eclipse-temurin:21-jre

RUN useradd -r -u 1001 appuser
WORKDIR /app

COPY target/*.jar app.jar

USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]