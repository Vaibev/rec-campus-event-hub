FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY . .

RUN javac -cp "lib/*:src" -d out src/DBConnection.java src/WebServer.java

EXPOSE 8080

CMD ["java", "-cp", "out:lib/*", "WebServer"]