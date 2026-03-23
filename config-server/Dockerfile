FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config-server/pom.xml config-server/
COPY gateway-service/pom.xml gateway-service/
COPY accounts-service/pom.xml accounts-service/
COPY reconciliation-service/pom.xml reconciliation-service/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl config-server

COPY config-server/src config-server/src
RUN ./mvnw package -pl config-server -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/config-server/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]