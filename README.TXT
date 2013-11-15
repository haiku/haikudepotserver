Haiku Depot Server
~~~~~~~~~~~~~~~~~~

This collection of files represents the source code for the "Haiku Depot Server"; a web-services and HTML environment for working with Haiku packages.  This is a maven java project that primarily builds an application server.

---
REQUIREMENTS, BUILD AND SETUP

To build and use this software, you will require;

* Java JDK 1.6 or better
* Apache Maven 3.0.3 or better
* A Postgres 9.1 or better database server
* An internet connection

On a debian 7 host, the following packages can be installed;

  apt-get install default-jdk
  apt-get install maven
  apt-get install postgresql postgresql-client

The project consists of a number of modules.  The "haikudepotserver-webapp" is the application server module.  This module requires a database in order to function.  It is expecting to work with a Postgres database server.  Create a blank postgres database and ensure that you are able to access the database over an internet socket authenticating as some user.  You can leave the database devoid of schema objects for now because the application server will populate necessary schema objects on the first launch.

The following file in the "haikudepotserver-webapp" is a template configuration file for the application server;

  src/main/resources/local-sample.properties

Copy this to an adjacent file in the same directory called "local.properties".  You will need to edit properties starting with "jdbc..." in order to let the application server know how to access your postgres database.

The first build will take longer than 'normal' because it will need to download a number of dependencies from the internet in order to undertake the build.  Some downloads are related to web-resources.  These downloads are only indirectly managed by the maven build process.  For this reason, your first step should be to complete a command-line build by issuing the following command in the same directory as this file;

  mvn package

This step will ensure that the web-resources are populated.  You should now be able to either continue to use the project in the command line environment or you can switch to use a java IDE.

To start-up the application server for development purposes, issue the following command from the same top level of the project; the same directory as this file.

  mvn org.apache.tomcat.maven:tomcat7-maven-plugin:2.1:run

This may take some time to start-up; especially the first time.  Once it has started-up, it should be possible to connect to the application server using the following URL;

  http://localhost:8080/

There won't be any repositories or data loaded, and because of this, it is not possible to view any data.  Now a repository can be added to obtain packages from.  Open a SQL terminal and add a repository;

  INSERT INTO
    haikudepot.repository (
      id, active, create_timestamp, modify_timestamp,
      architecture_id, code, url)
    VALUES (
      nextval('haikudepot.repository_seq'), true, now(), now(),
      (SELECT id FROM haikudepot.architecture WHERE code='x86'), 'test', 'file:///tmp/repo.hpkr');

This artificial repository will obtain a file from the local file system's temporary directory.  You could, for example, take the test HPKR file supplied in the test resources of the "haikudepotserver-packagefile" module and place this at /tmp/repo.hpkr.  Now it should be possible to prompt the system to take-up the repository data by dispatching a URL of this form using a tool such as curl;

  curl "http://localhost:8080/importrepositorydata?code=test"

You should now refresh your browser and it ought to be possible to view the packages that have been imported from the test file.

---
API

The API for communicating with the server is described in the "haikudepotserver-api1" module.  This contains DTO model objects describing the objects to be used in API calls as well as interfaces that describe the API calls that are available.  The application server vends the API as JSON-RPC.  More information about JSON-RPC can be found here;

  http://www.jsonrpc.org/

This API is intended to be used for the single-page web application as well as a desktop application.

---
HPKR HANDLING

Haiku packages are described using HPK* files and these are described here;

  http://dev.haiku-os.org/wiki/PackageManagement

The "haikudepotserver-packagefile" module contains handling for HPKR files.  Given the requirements of the application server this handling is limited to read-only access.
