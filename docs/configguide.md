# Configuration Guide

The application is configured using a standard java properties file.  The typical format has this form;

```
# Comment
key1=value
key2=value
```

There are a number of keys which are described below.

## Database

| Key | Description |Sample|
|:----|:------------|:-----|
| ```jdbc.driver```|This is the class name of the JDBC driver employed.|```org.postgresql.Driver```|
|```jdbc.url```|This is the JDBC connect URL to the main database|```jdbc:postgres://localhost:5432/haikudepotserver```|
|```jdbc.username```|Database user's username|-|
|```jdbc.password```|Database user's password|-|
|```flyway.migrate```|Set this to true if you would like database schema migrations to be applied automatically as necessary.  Generally this should be configured to true.|true|

## Web

| Key | Description |Sample|
|:----|:------------|:-----|
| ```webresourcegroup.separated```|Some web resources such as javascript file can be concatinated for efficiency.  Setting this configuration key to true will mean that such resources are delivered to browsers as separated files; less efficient, but easier to debug.|false|
