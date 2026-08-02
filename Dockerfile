FROM eclipse-temurin:21-jdk-noble AS build

RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
ENV MAVEN_USER_HOME=/workspace/.cache/maven

COPY . .
RUN chmod +x mvnw \
    && ./mvnw -B -ntp \
       -f src/main/resources/demo-cases/checkout-coupons/project/pom.xml \
       test org.pitest:pitest-maven:1.25.4:mutationCoverage \
    && ./mvnw -B -ntp -Dtest=VerticalSliceIntegrationTests test \
    && ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jdk-noble

RUN groupadd --system --gid 10001 patchreceipt \
    && useradd --system --uid 10001 --gid patchreceipt \
       --home-dir /app --shell /usr/sbin/nologin patchreceipt

WORKDIR /app
ENV MAVEN_USER_HOME=/app/.cache/maven
ENV PATCHRECEIPT_RUNNER_OFFLINE=true
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

COPY --from=build --chown=patchreceipt:patchreceipt \
    /workspace/target/patch-receipt-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build --chown=patchreceipt:patchreceipt /workspace/mvnw /app/mvnw
COPY --from=build --chown=patchreceipt:patchreceipt /workspace/.mvn /app/.mvn
COPY --from=build --chown=patchreceipt:patchreceipt \
    /workspace/.cache/maven /app/.cache/maven

RUN mkdir -p /app/.patchreceipt-work \
    && chown -R patchreceipt:patchreceipt /app

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=35.0", "-XX:ActiveProcessorCount=2", "-jar", "/app/app.jar"]
