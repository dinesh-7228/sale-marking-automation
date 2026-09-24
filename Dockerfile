FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
COPY target/sale-marking-automation-1.0.0.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx768m"
EXPOSE 6161
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
