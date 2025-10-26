FROM ghcr.io/graalvm/native-image-community:21 AS build
LABEL MAINTAINER="Tedo"
RUN microdnf install findutils
WORKDIR /app
COPY . .

RUN chmod +x gradlew
RUN ./gradlew clean build

FROM ghcr.io/graalvm/native-image-community:21
EXPOSE 9000
WORKDIR /app
COPY --from=build /app/build/libs/device-backend-0.0.1-SNAPSHOT.jar device_service.jar
ENTRYPOINT ["java","-jar","/app/device_service.jar"]