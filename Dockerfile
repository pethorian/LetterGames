# Imagem única parametrizada por módulo: docker build --build-arg MODULO=catalogo-service .
# O build é multi-módulo (-am) porque todo serviço depende de shared-contracts.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULO
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${MODULO}" -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
ARG MODULO
WORKDIR /app
COPY --from=build /src/${MODULO}/target/app.jar app.jar
RUN addgroup -S app && adduser -S app -G app
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
