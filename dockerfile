FROM openjdk:11-slim
VOLUME /tmp

ENV JAVA_OPTS="-Xms128m -Xmx512m"


ADD target/clinical-data-repository.jar clinical-data-repository.jar


ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/clinical-data-repository.jar"]


