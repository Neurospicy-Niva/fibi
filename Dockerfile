FROM icu.neurospicy/fibi-base:latest
EXPOSE 8080
ARG JAR_FILE=build/libs/fibi-0.0.1-SNAPSHOT.jar
ADD ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","-Xmx2048m","/app.jar"]