# Render Docker deployment for CarePoint
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/classes ./target/classes
COPY --from=build /build/target/dependency ./target/dependency
COPY src/main/resources/static ./src/main/resources/static
RUN mkdir -p /var/data
ENV PORT=8080
ENV DATABASE_PATH=/var/data/hospital_patients.db
EXPOSE 8080
CMD ["sh", "-c", "java -cp 'target/classes:target/dependency/*' com.hospital.Main"]
