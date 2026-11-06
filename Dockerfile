FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -Dmaven.test.skip=true dependency:go-offline

COPY src/main/ src/main/
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/seat-reservation-*.jar application.jar

FROM eclipse-temurin:25-jre-noble

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
