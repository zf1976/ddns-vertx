# Debian ---- Openj9-jlink
FROM adoptopenjdk:16-jdk-openj9 as jre-build
USER root
WORKDIR /vertx-ddns
ARG GRADLE=./gradle
ARG GRADLE_SETTINGS=./settings.gradle
ARG GRADLE_BUILD=./build.gradle
ARG GRADLEW=./gradlew
COPY $GRADLE ./gradle
COPY $GRADLEW ./gradlew
COPY ${GRADLE_SETTINGS} ./settings.gradle
COPY ${GRADLE_BUILD} ./build.gradle
# authorize
RUN chmod +x ./gradlew
# Cache dependencies
RUN ./gradlew resolveDependencies --scan --info
# Build project jar
ARG SOURCE_FILE=./src
COPY ${SOURCE_FILE} ./src
RUN ./gradlew shadowJar --info
# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules jdk.crypto.ec,java.base,java.compiler,java.logging,java.desktop,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.security.jgss,java.sql,java.xml,jdk.jdi,jdk.unsupported \
         --output /javaruntime

# Define your base image
FROM debian:stretch-slim
WORKDIR /vertx-ddns
USER root
MAINTAINER zf1976 <verticle@foxmail.com>
LABEL name=vertx-ddns
LABEL url=https://github.com/zf1976/vertx-ddns

ENV LANG C.UTF-8
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
ENV JAR_FILE=/vertx-ddns/build/libs/vertx-ddns-latest-all.jar

COPY --from=jre-build /javaruntime $JAVA_HOME
COPY --from=jre-build ${JAR_FILE} /vertx-ddns/vertx-ddns.jar

# Continue with your application deployment
RUN mkdir /root/logs
EXPOSE 	8080
ENV JVM_OPTS="-Xms128m -Xmx128m" \
    TZ=Asia/Shanghai

CMD exec java ${JVM_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /vertx-ddns/vertx-ddns.jar
