FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config-server/pom.xml config-server/
COPY gateway-service/pom.xml gateway-service/
COPY accounts-service/pom.xml accounts-service/
COPY reconciliation-service/pom.xml reconciliation-service/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl gateway-service

COPY gateway-service/src gateway-service/src
RUN ./mvnw package -pl gateway-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/gateway-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]