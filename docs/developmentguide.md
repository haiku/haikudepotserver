# Development Guide

Many of the setup steps for development cross-over with the actual deployment and so this should be read along-side the deployment guide.  The prerequisites are largely the same;

* Java and Maven
* Postgres

The project consists of a number of modules.  The "haikudepotserver-webapp" is the application server module.  You should configure a **development** configuration file at the following location relative to the top-level of the source;

```
haikudepotserver-webapp/src/main/resources/local.properties
```

See the accompanying configuration document for details on the form of these configuration files.

Some of the project source-files (web resources) are downloaded from the internet on the first build.  Your first step should be to undertake a build by issuing this command from the top level of the project;

```
mvn package
```

To start-up the application server for development purposes, issue the following command from the same top level of the project;

```
mvn org.apache.tomcat.maven:tomcat7-maven-plugin:2.1:run
```

This may take some time to start-up; especially the first time.  Once it has started-up, it should be possible to connect to the application server using the following URL;

```
http://localhost:8080/
```

There won't be any repositories or data loaded, and because of this, it is not possible to view any data.  See the deployment guide for details on how to setup a repository and load it in.