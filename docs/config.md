# Config

## Application

The application server is configured using a standard java properties or YAML file using SpringBoot's typical configuration patterns.

There are a number of possible keys which are described in a file `support/deployment/config.properties`.  Keys which are for HDS specifically are prefixed with `hds.`.  Keys which are for Spring Boot are prefixed with `spring.`.

To avoid the HDS application server from failing to health check connectivity to a mail service you may wish to configure the configuration property `management.health.mail.enabled` to `false`.

## Logging

The application logging uses the [SLF4J](http://www.slf4j.org/) logging framework. This is backed by the [Logback](http://logback.qos.ch/) logging system. When formatting log lines, it is possible to add additional meta data from the
application server. Presently supported;

|Key| Description |
|---|-------------|
|`authUserNickname`|The nickname of the presently authenticated user|
|`userAgent`|The User-Agent HTTP header related to the current request|
|`userAgentCode`|This is a short code to describe the user agent in brief|

