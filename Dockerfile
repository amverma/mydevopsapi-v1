FROM frolvlad/alpine-oraclejdk8:slim
EXPOSE 9090
RUN mkdir -p /app/
ADD build/libs/mydevopsapi-v1-0.0.1-SNAPSHOT.jar /app/mydevopsapi-v1.jar
ENTRYPOINT ["java", "-jar", "/app/mydevopsapi-v1.jar"]