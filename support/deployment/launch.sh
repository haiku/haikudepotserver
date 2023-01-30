#!/bin/bash

# launch file for the haikudepotserver system.
. "$(dirname $0)/launchenv.sh"

# source secrets file if provided, otherwise from env
if [ -f "/secrets/hds_secrets" ]; then
	echo "Sourcing secrets from /secrets/hds_secrets..."
	. "/secrets/hds_secrets"
fi

"${JAVA_BIN}" \
"-Dfile.encoding=UTF-8" \
"-Duser.timezone=GMT0" \
"-Xms320m" \
"-Xmx512m" \
"-Djava.net.preferIPv4Stack=true" \
"-Djava.awt.headless=true" \
"-Dconfig.properties=file://${HDS_ROOT}/config.properties" \
"-Dhvif2png.path=${HDS_HVIF2PNG_PATH}" \
"-Djdbc.url=${HDS_JDBC_URL}" \
"-Djdbc.username=${HDS_JDBC_USERNAME}" \
"-Djdbc.password=${HDS_JDBC_PASSWORD}" \
"-Dsmtp.host=${HDS_SMTP_HOST}" \
"-Dserver.port=${HDS_PORT}" \
"-Dmanagement.server.port=${HDS_ACTUATOR_PORT}" \
"-Dauthentication.jws.issuer=${HDS_AUTHENTICATION_JWS_ISSUER}" \
"-jar" "${HDS_ROOT}/app.jar"
