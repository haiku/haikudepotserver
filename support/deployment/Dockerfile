# =====================================
# Copyright 2018, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This Dockerfile is used to build an image that is able to run an HDS server.
# Do not use this file from the master or other working branch, but instead
# use one from a release tag because this version of the file will have the
# correct versions inserted into it.  See the documentation for further
# details on how this deployment mechanism works.

FROM ubuntu:16.04

# These environment variables will be set using the release script.  It will
# copy the correct values from the poms into these variables so that the right
# versions of the artefacts are used in the Docker container.

ENV HDS_VERSION "1.0.110"
ENV JETTY_VERSION "9.4.11.v20180605"
ENV PG_VERSION "9.4.1212"

ENV JAVA_BIN "java"

ENV INSTALL_ROOT "/opt/haikudepotserver"
ENV INSTALL_HVIF2PNG_PATH "${INSTALL_ROOT}/hvif2png-hrev51165/bin/hvif2png.sh"

ENV HDS_WAR "haikudepotserver-webapp-${HDS_VERSION}.war"
ENV HDS_WAR_DOWNLOAD_URL "https://github.com/haiku/haikudepotserver/releases/download/haikudepotserver-${HDS_VERSION}/${HDS_WAR}"
ENV HDS_PORT 8080

ENV JETTY_JAR "jetty-runner-${JETTY_VERSION}.jar"
ENV JETTY_DOWNLOAD_URL "http://central.maven.org/maven2/org/eclipse/jetty/jetty-runner/${JETTY_VERSION}/${JETTY_JAR}"

ENV PG_JAR "postgresql-${PG_VERSION}.jar"
ENV PG_DOWNLOAD_URL "http://central.maven.org/maven2/org/postgresql/postgresql/${PG_VERSION}/${PG_JAR}"

ENV HVIF2PNG_VERSION "hvif2png-hrev51165-linux-x86_64"

RUN mkdir -p "${INSTALL_ROOT}"
RUN apt-get update && apt-get -y install optipng libpng12-0 curl openjdk-8-jdk fontconfig ttf-dejavu

COPY config.properties ${INSTALL_ROOT}
COPY logback.xml ${INSTALL_ROOT}
COPY launch.sh ${INSTALL_ROOT}

ADD ${JETTY_DOWNLOAD_URL} ${INSTALL_ROOT}/${JETTY_JAR}
ADD ${HVIF2PNG_VERSION}.tgz ${INSTALL_ROOT}
ADD ${PG_DOWNLOAD_URL} ${INSTALL_ROOT}/${PG_JAR}
ADD ${HDS_WAR_DOWNLOAD_URL} ${INSTALL_ROOT}/${HDS_WAR}

RUN echo "HDS_ROOT=${INSTALL_ROOT}" > ${INSTALL_ROOT}/launchenv.sh
RUN echo "JAVA_BIN=${JAVA_BIN}" >> ${INSTALL_ROOT}/launchenv.sh
RUN echo "HDS_HVIF2PNG_PATH=${INSTALL_HVIF2PNG_PATH}" >> ${INSTALL_ROOT}/launchenv.sh
RUN echo "HDS_PORT=${HDS_PORT}" >> ${INSTALL_ROOT}/launchenv.sh
RUN echo "HDS_WAR=${HDS_WAR}" >> ${INSTALL_ROOT}/launchenv.sh
RUN echo "JETTY_JAR=${JETTY_JAR}" >> ${INSTALL_ROOT}/launchenv.sh
RUN echo "PG_JAR=${PG_JAR}" >> ${INSTALL_ROOT}/launchenv.sh

CMD [ "sh", "/opt/haikudepotserver/launch.sh" ]

HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:8080/__metric/healthcheck
EXPOSE ${HDS_PORT}
