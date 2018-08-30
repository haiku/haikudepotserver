# launch file for the haikudepotserver system.

. "$(dirname $0)/launchenv.sh"
. "/secrets/hds_secrets"

"${JAVA_BIN}" \
"-Dfile.encoding=UTF-8" \
"-Dlogback.configurationFile=${DS_ROOT}/logback.xml" \
"-Duser.timezone=GMT0" \
"-Xms256m" \
"-Xmx320m" \
"-Djava.net.preferIPv4Stack=true" \
"-Djava.awt.headless=true" \
"-Dconfig.properties=file://${HDS_ROOT}/config.properties" \
"-Dhvif2png.path=${HDS_HVIF2PNG_PATH}" \
"-Djdbc.url=${HDS_JDBC_URL}" \
"-Djdbc.username=${HDS_JDBC_USERNAME}" \
"-Djdbc.password=${HDS_JDBC_PASSWORD}" \
"-Dsmtp.host=${HDS_SMTP_HOST}" \
"-Dauthentication.jws.issuer=${HDS_AUTHENTICATION_JWS_ISSUER}" \
"-jar" "${HDS_ROOT}/${JETTY_JAR}" \
"--jar" "${HDS_ROOT}/${PG_JAR}" \
"--port" "${HDS_PORT}" \
"${HDS_ROOT}/${HDS_WAR}" \

