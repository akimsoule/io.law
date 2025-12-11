# Dockerfile - io.law pour Hostinger KVM 8
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copier tous les fichiers projet
# Copier et installer le parent POM d'abord
COPY pom.xml .
RUN mvn install -N -B

# Copier tous les modules
COPY law-common law-common/
COPY law-fetch law-fetch/
COPY law-download law-download/
COPY law-tojson law-tojson/
COPY law-consolidate law-consolidate/
COPY law-fix law-fix/
COPY law-app law-app/

# Build projet complet (parent déjà dans repo local)
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
RUN useradd -m -u 1000 lawapp \
	&& mkdir -p /data /app/logs \
	&& chown -R lawapp:lawapp /data /app
WORKDIR /app
COPY --from=builder /build/law-app/target/law-app-1.0-SNAPSHOT.jar app.jar
COPY scripts/orchestrate.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
USER lawapp
ENV JAVA_OPTS="-Xmx4g -Xms1g -XX:+UseG1GC"
ENTRYPOINT ["/app/entrypoint.sh"]
