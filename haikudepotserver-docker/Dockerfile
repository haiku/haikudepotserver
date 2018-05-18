FROM ubuntu:16.04

ARG HDS_VERSION
ARG HDS_WAR_FILE
ARG JETTY_VERSION
ARG PG_VERSION

ENV JAVA_BIN "java"

ENV HDS_ROOT "/opt/haikudepotserver"
ENV HDS_WAR "haikudepotserver-webapp-${HDS_VERSION}.war"
ENV HDS_PORT 8080

ENV JETTY_JAR "jetty-runner-${JETTY_VERSION}.jar"
ENV JETTY_DOWNLOAD_URL "http://central.maven.org/maven2/org/eclipse/jetty/jetty-runner/${JETTY_VERSION}/${JETTY_JAR}"

ENV PG_JAR "postgresql-${PG_VERSION}.jar"
ENV PG_DOWNLOAD_URL "http://central.maven.org/maven2/org/postgresql/postgresql/${PG_VERSION}/${PG_JAR}"

ENV HVIF2PNG_VERSION "hvif2png-hrev51165-linux-x86_64"

ENV HDS_HVIF2PNG_PATH "${HDS_ROOT}/hvif2png-hrev51165/bin/hvif2png.sh"

RUN mkdir -p "${HDS_ROOT}"
RUN apt-get update && apt-get -y install optipng libpng12-0 curl openjdk-8-jdk fontconfig ttf-dejavu

COPY target/${HDS_WAR_FILE} ${HDS_ROOT}
COPY src/main/docker/config.properties ${HDS_ROOT}
COPY src/main/docker/logback.xml ${HDS_ROOT}
COPY src/main/docker/launch.sh ${HDS_ROOT}

ADD ${JETTY_DOWNLOAD_URL} ${HDS_ROOT}/${JETTY_JAR}
ADD src/main/docker/${HVIF2PNG_VERSION}.tgz ${HDS_ROOT}
ADD ${PG_DOWNLOAD_URL} ${HDS_ROOT}/${PG_JAR}

RUN echo "HDS_ROOT=${HDS_ROOT}" > ${HDS_ROOT}/launchenv.sh
RUN echo "JAVA_BIN=${JAVA_BIN}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "HDS_ROOT=${HDS_ROOT}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "HDS_HVIF2PNG_PATH=${HDS_HVIF2PNG_PATH}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "HDS_PORT=${HDS_PORT}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "HDS_WAR=${HDS_WAR}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "JETTY_JAR=${JETTY_JAR}" >> ${HDS_ROOT}/launchenv.sh
RUN echo "PG_JAR=${PG_JAR}" >> ${HDS_ROOT}/launchenv.sh

CMD [ "sh", "/opt/haikudepotserver/launch.sh" ]

EXPOSE ${HDS_PORT}
