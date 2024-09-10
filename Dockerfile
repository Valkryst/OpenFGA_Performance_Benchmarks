# Build the project with Maven.
FROM maven:3.9.9-eclipse-temurin-22-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Run the application.
FROM eclipse-temurin:22.0.2_9-jdk

RUN mkdir -p /app/volumes/cacerts
COPY ./volumes/cacerts/tls.crt tls.crt
RUN keytool -import -trustcacerts -alias openfga -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -file tls.crt -noprompt

COPY --from=build /app/target/benchmarks.jar benchmarks.jar
ENTRYPOINT ["java", "-jar", "benchmarks.jar"]