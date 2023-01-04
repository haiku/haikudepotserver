# =====================================
# Copyright 2022-2023, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This `Dockerfile` expresses a multi-stage build of the
# HaikuDepotServer system.  It has a build stage and then
# a deployable stage.

# -------------------------------------
# Assemble the build image with the dependencies

FROM debian:11.6-slim as build

RUN apt-get update
RUN apt-get -y install wget python3 openjdk-17-jdk fontconfig fonts-dejavu-core lsb-release gnupg2
RUN apt-get -y install postgresql postgresql-contrib

# copy the source into the build machine
RUN mkdir /hds-src

COPY ./mvnw /hds-src/mvnw
COPY ./.mvn/wrapper/maven-wrapper.properties /hds-src/.mvn/wrapper/maven-wrapper.properties

COPY ./pom.xml /hds-src/pom.xml
COPY ./haikudepotserver-parent/pom.xml /hds-src/haikudepotserver-parent/pom.xml
COPY ./haikudepotserver-driversettings/pom.xml /hds-src/haikudepotserver-driversettings/pom.xml
COPY ./haikudepotserver-core-test/pom.xml /hds-src/haikudepotserver-core-test/pom.xml
COPY ./haikudepotserver-api2/pom.xml /hds-src/haikudepotserver-api2/pom.xml
COPY ./haikudepotserver-packagefile/pom.xml /hds-src/haikudepotserver-packagefile/pom.xml
COPY ./haikudepotserver-docs/pom.xml /hds-src/haikudepotserver-docs/pom.xml
COPY ./haikudepotserver-webapp/pom.xml /hds-src/haikudepotserver-webapp/pom.xml
COPY ./haikudepotserver-api1/pom.xml /hds-src/haikudepotserver-api1/pom.xml
COPY ./haikudepotserver-core/pom.xml /hds-src/haikudepotserver-core/pom.xml

WORKDIR /hds-src

# capture the dependencies into the image
RUN ./mvnw clean org.apache.maven.plugins:maven-dependency-plugin:3.3.0:go-offline

# copy the rest of the source
COPY ./haikudepotserver-parent /hds-src/haikudepotserver-parent
COPY ./haikudepotserver-driversettings /hds-src/haikudepotserver-driversettings
COPY ./haikudepotserver-core-test /hds-src/haikudepotserver-core-test
COPY ./haikudepotserver-api2 /hds-src/haikudepotserver-api2
COPY ./haikudepotserver-packagefile /hds-src/haikudepotserver-packagefile
COPY ./haikudepotserver-docs /hds-src/haikudepotserver-docs
COPY ./haikudepotserver-webapp /hds-src/haikudepotserver-webapp
COPY ./haikudepotserver-api1 /hds-src/haikudepotserver-api1
COPY ./haikudepotserver-core /hds-src/haikudepotserver-core

# This will cause the integration tests to be run against a child process
# of maven.
ENV TEST_DATABASE_TYPE "START_LOCAL_DATABASE"

# perform the build of the application.
RUN ./mvnw clean install

# -------------------------------------
# Create the container that will eventually run HDS

FROM debian:11.6-slim AS runtime

RUN apt-get update && apt-get -y install wget optipng libpng16-16 curl openjdk-17-jre fontconfig fonts-dejavu-core

ENV HDS_B_HTTP_PORT=8080
ENV HDS_B_INSTALL_ROOT="/opt/haikudepotserver"
ENV HDS_B_PROPBIN="/opt/haikudepotserver/prop.sh"
RUN mkdir ${HDS_B_INSTALL_ROOT}

COPY ./support/deployment/prop.sh ${HDS_B_PROPBIN}
RUN chmod 755 ${HDS_B_PROPBIN}

COPY --from=build /hds-src/haikudepotserver-core/target/classes/build.properties ${HDS_B_INSTALL_ROOT}
COPY --from=build /hds-src/haikudepotserver-webapp/target/haikudepotserver-webapp-*.jar ${HDS_B_INSTALL_ROOT}/app.jar

ENV HDS_B_HVIF2PNG_VERSION="hvif2png-hrev53013-linux-x86_64"
ENV HDS_B_INSTALL_HVIF2PNG_PATH "${HDS_B_INSTALL_ROOT}/hvif2png-hrev53013+1/bin/hvif2png.sh"

COPY ./support/deployment/config.properties ${HDS_B_INSTALL_ROOT}
ADD ./support/deployment/${HDS_B_HVIF2PNG_VERSION}.tgz ${HDS_B_INSTALL_ROOT}
COPY ./support/deployment/launch.sh ${HDS_B_INSTALL_ROOT}
RUN chmod 755 ${HDS_B_INSTALL_ROOT}/launch.sh

RUN echo "\
HDS_ROOT=${HDS_B_INSTALL_ROOT}\n\
JAVA_BIN=java\n\
HDS_HVIF2PNG_PATH=${HDS_B_INSTALL_HVIF2PNG_PATH}\n\
HDS_PORT=${HDS_B_HTTP_PORT}\n\
" >> ${HDS_B_INSTALL_ROOT}/launchenv.sh

CMD [ "/opt/haikudepotserver/launch.sh" ]

HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:8080/__metric/healthcheck
EXPOSE ${HDS_B_HTTP_PORT}
