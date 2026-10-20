FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx1g"

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/chat-ads-eval.jar extract --destination /build/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir /app && chown 10001:10001 /app
WORKDIR /app
# The unpacked jar keeps application classes on the normal class path, which is what lets the
# class-data archive below cover them. A fat jar's nested loader cannot be archived.
COPY --from=build --chown=10001:10001 /build/extracted/ ./

# Tuned for a 512 MB container with a fraction of a CPU: serial collector, C1 compiler only (C2 costs
# more CPU and memory than a small service earns back), small thread stacks, capped metaspace.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=10 \
 -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:-UsePerfData \
 -XX:SharedArchiveFile=/app/app.jsa"

# Start the app once at build time, send a few requests, and let the JVM write a class-data archive on
# exit. Later starts map those classes instead of loading and verifying them again.
USER 10001:10001
RUN set -e; \
    java $(echo "$JAVA_OPTS" | sed 's#-XX:SharedArchiveFile=[^ ]*##') -XX:ArchiveClassesAtExit=/app/app.jsa \
        -jar /app/chat-ads-eval.jar > /tmp/training.log 2>&1 & \
    pid=$!; \
    for i in $(seq 1 90); do \
        curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null 2>&1 && break; sleep 1; \
    done; \
    for p in "running shoes for a marathon" "noise cancelling headphones for my commute" "where can I buy a vape"; do \
        curl -fsS -X POST http://127.0.0.1:8080/v1/ads/contextual -H 'Content-Type: application/json' \
            -d "{\"prompt\":\"$p\",\"assistant_answer\":\"Look for a good fit.\"}" > /dev/null; \
    done; \
    curl -fsS http://127.0.0.1:8080/ > /dev/null; \
    kill -TERM $pid; wait $pid || true; \
    test -s /app/app.jsa; rm -f /tmp/training.log

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=3 \
    CMD curl -fsS --max-time 2 "http://127.0.0.1:${PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/chat-ads-eval.jar \"$@\"", "--"]
