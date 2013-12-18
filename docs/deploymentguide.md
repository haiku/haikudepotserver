# Deployment Guide

This document outlines the approximate steps to build and deploy the web application-server.  The application-server build process produces a  'stand-alone' java program.  This build artifact is a packaged 'jar' file.

## Warning; Root User

The default database installs a user with the nickname of 'root' with a known password of 'p4mphl3t'.  This password **must** be changed before the system is made available.

## Java and Maven

The application server runs in a java process.  For this reason, your deployment environment should have java 1.6 or better available.  On a [Debian](http://www.debian.org) 7.x host, it is possible to install java with;

```
apt-get install default-jdk
```

In order to build the application server, it is necessary to install [Apache Maven](http://maven.apache.org) version 3 or better.  On a [Debian](http://www.debian.org) 7.x host, it is possible to install maven with;

```
apt-get install maven
```

## Database

The actual database configuration for a production environment may differ from that described here.  These setup steps are simplistic as it is not possible to envisage all of the possible environmental factors involved in a production deployment.

The application server stores its operational data in a relational database.  The database _product_ that this project uses is [Postgres](http://www.postgres.org).  You should install Postgres version 9.1 or better.  This document does not cover platform installation of Postgres although it is possible to install the necessary Postgres database and tools on a [Debian](http://www.debian.org) 7.x host with;

```
apt-get install postgresql postgresql-client
```

### Setup Database and User for Development

By this point, the Postgres database _product_ is now installed on a UNIX-like computer system and is running as the UNIX user 'postgres'.  Create a new database with the following command;

```
sudo -u postgres createuser -P -E haikudepotserver
```

Now create the database;

```
sudo -u postgres createdb -O haikudepotserver haikudepotserver
```

### Check Login

You can check the login to the database works by opening a SQL terminal;

```
psql -h localhost -U haikudepotserver haikudepotserver
```

### Configure Schema Objects

The application server will populate and configure the schema objects itself as it first loads.  Subject to correct configuration, subsequent versions of the application server will then upgrade the database schema objects as necessary as the application server launches.

## Obtaining the Build Product

To build the application server, it is necessary to have an internet connection.  The build process may take some time to complete for the first build because many dependencies will need to be downloaded from the internet.

### Development / Snapshot Build

From the top level of the project, issue the following command to build all of the artifacts; `mvn clean && mvn package`.  This will produce a deployment artifact at;

```
haikudepotserver-webapp/target/haikudepotserver-webapp-1.0.1-SNAPSHOT-war-exec.jar
```

The actual filename may vary depending on the version being built.

## Launching

To launch the binary with 256 megabytes of heap memory, issue a command similar to;

```
java \
 -Xmx256m \
 -Dconfig.properties=/opt/haikudepotserver/config.properties \
 -jar haikudepotserver-webapp-1.2.3-war-exec.jar \
 -resetExtract \
 -extractDirectory /var/cache/haikudepotserver-webapp
```

The configuration properties file is documented separately.  You will need to create a configuration file for your deployment environment.

By default the logging will be streamed to stdout/stderr.  It is possible to configure this using a [logback](http://logback.qos.ch/) logging configuration file.

The binary runs as an [Apache Tomcat](http://tomcat.apache.org/) server.  There are a handful of other easily-accessed command line options which can be used to fine-tune the deployment.  These can be viewed by executing the binary as follows;

```
java \
 -jar haikudepotserver-webapp-1.2.3-war-exec.jar
 -h
```

## Setting Up Repositories

The application server will pull ".hpkr" files from remote repositories that contain information about the packages at that repository.  At the time of writing, it is necessary to configure the repositories by hand.  A repository can be added using a SQL shell.

In this ficticious example, an ".hpkr" file has been placed in the temporary directory.  The following SQL command can be executed to add a repository that pulls the ".hpkr" from the temporary directory.  In reality, the real repositories will have an internet-accessible URL.

```
INSERT INTO
  haikudepot.repository (
    id, active, create_timestamp, modify_timestamp,
    architecture_id, code, url)
  VALUES (
    nextval('haikudepot.repository_seq'), true, now(), now(),
    (SELECT id FROM haikudepot.architecture WHERE code='x86'),
    'test',
    'file:///tmp/repo.hpkr');
```

In order to prompt the system to import this ".hpkr" file and populate some repository data into the system, you can use the curl tool as follows;

```
curl "http://localhost:8080/importrepositorydata?code=test"
```

## Accessing the Web Environment

Once running, the web environment will be accessible from;

```
http://localhost:8081/
```