FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY backend/contracts/pom.xml backend/contracts/
COPY backend/control-plane/pom.xml backend/control-plane/
COPY backend/gateway/pom.xml backend/gateway/
COPY backend/worker/pom.xml backend/worker/
RUN chmod +x ./mvnw

COPY backend/contracts/src backend/contracts/src
COPY backend/control-plane/src backend/control-plane/src
RUN ./mvnw package -pl backend/control-plane -am -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/backend/control-plane/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
