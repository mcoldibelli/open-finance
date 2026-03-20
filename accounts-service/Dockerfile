FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config-server/pom.xml config-server/
COPY gateway-service/pom.xml gateway-service/
COPY accounts-service/pom.xml accounts-service/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl accounts-service

COPY accounts-service/src accounts-service/src
RUN ./mvnw package -pl accounts-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/accounts-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]