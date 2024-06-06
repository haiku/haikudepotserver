# Local development

No considerations are made in these instructions and notes around system security.

For local development, say in an IDE system, HDS can be launched as a SpringBoot application.

## Preparation

1. Install [`hvif2png`](./hvif2png.md)
1. Install the necessary software for HDS
  - Java 21+
  - Postgres Database Server and Client 14+
  - Python 3
  - Docker
  - `optipng`
1. Setup the database server
  - Listening on a TCP/IP socket
  - Empty database
  - User with password to access the database
1. Copy the file `support/deployment/config.properties` to `haikudepotserver-webapp/src/main/resources/local.properties`. Modify at least the following configuration items to suit your environment;
  - `spring.datasource.url`
  - `spring.datasource.username`
  - `spring.datasource.password`
  - `spring.mail.host`
  - `hds.deployment.is-production`
  - `hds.base-url`
  - `hds.authentication.jws.issuer`
  - `hds.hvif2png.path`
  If you don't have an SMTP mail host this can be omitted for a development scenario unless you want to test those functions such as "forgot password".

## Run from Maven

Run the application server with;

```
./mvnw clean install
cd haikudepotserver-webapp
../mvnw \
spring-boot:run \
-Dfile.encoding=UTF-8 \
-Duser.timezone=GMT0 \
-Djava.awt.headless=true
```

The application can be accessed using a web browser on `http://localhost:8080` on the development host. Login as `root` with password `zimmer`.

## Run from the Intelli-J IDE

The [Intelli-J](https://www.jetbrains.com/idea/) IDE can be used to run the HDS SpringBoot application. Locate the file `src/main/java/org/haiku/haikudepotserver/Application.java` and choose the _Debug_ option on the context menu for this file.

The application can be accessed using a web browser on `http://localhost:8080` on the development host. Login as `root` with password `zimmer`.

## Run integration-tests

The HDS application has a number of integration tests. Run the integration tests with;

```
./mvnw clean verify
```

The tests will automatically launch a Postgres database to test against using Docker.