FROM openjdk:alpine

ENV MAVEN_VERSION 3.3.9
ENV MAVEN_ARCHIVE http://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz

RUN apk --update add --no-cache bash wget curl tar git && \
    wget ${MAVEN_ARCHIVE} && \
    tar -xf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /usr/local && \
    mv /usr/local/apache-maven-${MAVEN_VERSION} /usr/local/maven && \
    rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

ENV MAVEN_HOME /usr/local/maven
ENV PATH ${PATH}:${MAVEN_HOME}/bin

COPY . /usr/src/haikudepotserver
WORKDIR /usr/src/haikudepotserver

# Build application
RUN mvn compile

ENV HDS_PSIDENTIFIER haikudepotserver-x86_64-3D84A80C-38A4-40BC-9AEE-86C3CA986517
ENV HDS_HOME /usr/src/haikudepotserver
ENV HDS_PORT 8080
ENV HDS_CONFIGBASE /etc/haikudepotserver

CMD ["java", "-Dfile.encoding=UTF-8", "-Dcommand.identifier=${HDS_PSIDENTIFIER}",
	"-Dlogback.configurationFile=${HDS_CONFIGBASE}/logback.xml", "-Duser.timezone=GMT0",
	"-Xms256m", "-Xmx320m", "-Djava.net.preferIPv4Stack=true", "-Djava.awt.headless=true",
	"-Dconfig.properties=file://${HDS_CONFIGBASE}/config.properties",
	"-jar ${HDS_HOME}/jetty-runner.jar", "--jar ${HDS_HOME}/postgresql.jar",
	"--port ${HDS_PORT}", "${HDS_HOME}/haikudepotserver-webapp.war"]
