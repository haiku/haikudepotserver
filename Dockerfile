# =====================================
# Copyright 2022-2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This `Dockerfile` expresses a multi-stage build of the
# HaikuDepotServer system.  It has a build stage and then
# a deployable stage.

# -------------------------------------
# Base image with the Eclipse Temurin repository setup.

FROM debian:12.5-slim as base

RUN apt-get update && \
    apt-get -y install wget apt-transport-https gnupg locales

RUN mkdir /adoptium

RUN wget -O - "https://packages.adoptium.net/artifactory/api/gpg/key/public" | gpg --dearmour | dd of=/adoptium/apt-keyring.gpg
COPY support/deployment/deb-adoptium-sources-template.txt /adoptium/deb-adoptium-sources-template.txt
RUN OS_VERSION_CODENAME="$(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release)" && \
    cat /adoptium/deb-adoptium-sources-template.txt | sed -e "s/SUITE/${OS_VERSION_CODENAME}/g" | dd of=/etc/apt/sources.list.d/adoptium.sources

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# -------------------------------------
# Assemble the build image with the dependencies

FROM base as build

RUN apt-get update && \
    apt-get -y install temurin-21-jdk && \
    apt-get -y install wget python3 fontconfig fonts-dejavu-core lsb-release gnupg2 && \
    apt-get -y install postgresql postgresql-contrib

# copy the source into the build machine
RUN mkdir /hds-src

COPY ./mvnw /hds-src/mvnw
COPY ./.mvn/wrapper/maven-wrapper.properties /hds-src/.mvn/wrapper/maven-wrapper.properties

COPY ./pom.xml /hds-src/pom.xml
COPY ./haikudepotserver-parent/pom.xml /hds-src/haikudepotserver-parent/pom.xml
COPY ./haikudepotserver-driversettings/pom.xml /hds-src/haikudepotserver-driversettings/pom.xml
COPY ./haikudepotserver-core-test/pom.xml /hds-src/haikudepotserver-core-test/pom.xml
COPY ./haikudepotserver-api1/pom.xml /hds-src/haikudepotserver-api1/pom.xml
COPY ./haikudepotserver-api2/pom.xml /hds-src/haikudepotserver-api2/pom.xml
COPY ./haikudepotserver-packagefile/pom.xml /hds-src/haikudepotserver-packagefile/pom.xml
COPY ./haikudepotserver-spa1/pom.xml /hds-src/haikudepotserver-spa1/pom.xml
COPY ./haikudepotserver-webapp/pom.xml /hds-src/haikudepotserver-webapp/pom.xml
COPY ./haikudepotserver-core/pom.xml /hds-src/haikudepotserver-core/pom.xml

WORKDIR /hds-src

RUN ./mvnw clean org.apache.maven.plugins:maven-dependency-plugin:3.6.1:go-offline

COPY ./haikudepotserver-spa1/package.json /hds-src/haikudepotserver-spa1/package.json

# capture the NodeJS dependencies into the image
RUN ./mvnw -f haikudepotserver-spa1 com.github.eirslett:frontend-maven-plugin:install-node-and-npm
RUN ./mvnw -f haikudepotserver-spa1 com.github.eirslett:frontend-maven-plugin:npm -Pfrontend.npm.arguments=ci

# copy the rest of the source
COPY ./haikudepotserver-parent /hds-src/haikudepotserver-parent
COPY ./haikudepotserver-driversettings /hds-src/haikudepotserver-driversettings
COPY ./haikudepotserver-core-test /hds-src/haikudepotserver-core-test
COPY ./haikudepotserver-api2 /hds-src/haikudepotserver-api2
COPY ./haikudepotserver-packagefile /hds-src/haikudepotserver-packagefile
COPY ./haikudepotserver-webapp /hds-src/haikudepotserver-webapp
COPY ./haikudepotserver-spa1 /hds-src/haikudepotserver-spa1
COPY ./haikudepotserver-api1 /hds-src/haikudepotserver-api1
COPY ./haikudepotserver-core /hds-src/haikudepotserver-core

# This will cause the integration tests to be run against a child process
# of maven.
ENV TEST_DATABASE_TYPE "START_LOCAL_DATABASE"

# perform the build of the application.
RUN ./mvnw clean install

# -------------------------------------
# Create the container that will eventually run HDS

FROM base AS runtime

RUN apt-get update && \
    apt-get -y install temurin-21-jre && \
    apt-get -y install optipng libpng16-16 curl fontconfig fonts-dejavu-core

ENV HDS_B_HTTP_PORT=8080
ENV HDS_B_HTTP_ACTUATOR_PORT=8081
ENV HDS_B_INSTALL_ROOT="/opt/haikudepotserver"
RUN mkdir ${HDS_B_INSTALL_ROOT}

COPY --from=build /hds-src/haikudepotserver-core/target/classes/build.properties ${HDS_B_INSTALL_ROOT}
COPY --from=build /hds-src/haikudepotserver-webapp/target/haikudepotserver-webapp-*.jar ${HDS_B_INSTALL_ROOT}/app.jar

ENV HDS_B_HVIF2PNG_VERSION="hvif2png-hrev57235-linux-x86_64"
ENV HDS_B_INSTALL_HVIF2PNG_PATH "${HDS_B_INSTALL_ROOT}/hvif2png-hrev57235/bin/hvif2png.sh"

COPY ./support/deployment/config.properties ${HDS_B_INSTALL_ROOT}
ADD ./support/deployment/${HDS_B_HVIF2PNG_VERSION}.tgz ${HDS_B_INSTALL_ROOT}

CMD \
    java \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=GMT0 \
    -Xms320m \
    -Xmx512m \
    -Djava.net.preferIPv4Stack=true \
    -Djava.awt.headless=true \
    -Dconfig.properties=file://${HDS_B_INSTALL_ROOT}/config.properties \
    -Dhds.hvif2png.path=${HDS_B_INSTALL_HVIF2PNG_PATH} \
    -Dserver.port=${HDS_B_HTTP_PORT} \
    -Dmanagement.server.port=${HDS_B_HTTP_ACTUATOR_PORT} \
    -jar \
    ${HDS_B_INSTALL_ROOT}/app.jar

HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:${HDS_B_HTTP_ACTUATOR_PORT}/actuator/health
EXPOSE ${HDS_B_HTTP_PORT} ${HDS_B_HTTP_ACTUATOR_PORT}
