# ---------- Etapa 1: compilar ----------
# Se usa la imagen de Maven (y no ./mvnw) para evitar problemas de permisos
# y saltos de linea CRLF del wrapper cuando el repo viene de Windows.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom: Docker cachea las dependencias mientras el pom no cambie
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: ejecutar ----------
# Solo el JRE + el jar: imagen mucho mas chica que la de build
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render asigna el puerto en la variable PORT; application.properties ya la lee.
# MaxRAMPercentage: el plan gratis de Render tiene 512 MB, que la JVM no se pase.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
