# APIs

## General

This information applies to all areas of the API for the system.  Some of the APIs are "REST" in nature. Other APIs are RPC-over-HTTP in nature.  An older API exists which is presented in JSON-RPC.

### Clients

A client of this system's API may be a desktop application (eg; Haiku Depot) or may be logic in a web page where the web page is using some java-script to call back to the server. It is also possible for other applications to also interact with HDS using its API.

### API version

The structured API has a version 2 using an RPC-over-REST approach. An earlier API version 1 was using JSON-RPC.

### Object references

When objects such as packages or users are referred to in the API, the database primary key is *not* used. Instead, either a natural or artificial identifier is employed. For example, in the case of a package, the package's name may be used. In the case of a screen-shot, a GUID (unique identifier) will be used to identify a particular screenshot.

### Reference data

Reference data means data in the application that is generally invariant. Examples include the mime types, natural language, url types and so on. In these cases, an API will generally provide for access to the list of reference data. The client is expected to obtain such reference data as necessary and cache reference data locally.

### Date and time data

The system has only a concept of a &quot;moment in time&quot; which is called a timestamp.  The timestamp is typically communicated as the number of milliseconds elapsed since the epoc represented as a 64bit integer.  The timestamp communicated via the API is always relative to UTC.

### Invocations and transport

The term "invocation" refers to a request-response cycle from the client software into the application server over the HTTP protocol.  Each API invocation is made in a *stateless* manner in that each invocation is not dependent on the prior invocation.

### Security

See the [security](security.md) chapter for details on how to authenticate API requests as well as how API requests are authorized.

### If-Modified-Since header

This header is only observed in some APIs where large quantities of data are being downloaded or where the data is computationally expensive to assemble.  Examples of these situations include the assembly of a tar-ball of packages' icons or the download of bulk package data.

In order to prevent clients from downloading and processing data they already have, the client
may add a `If-Modified-Since` header to the initial request.  The form of this header
is RFC-1123 compliant and looks like `Sat, 3 Dec 2016 23:48:05 GMT`.  Taking the example of
downloading a tar-ball of icon data; if the icon data
has not been modified since the `If-Modified-Since` header time, then the API will return a
`304 (Not Modified)` response.

## RPC-over-HTTP API

This API is achieved by sending JSON payloads over HTTP.  The request is expressed as JSON and the response is also expressed as JSON.  The request is a JSON object with key-value pairs of data.  The response has the following shape;

```
{
  "result": {
    "code": "beam",
    "name": "Beam Email Program"
  }
}
```

In the event that there is an error, this is expressed in the response as follows;

```
{
  "error": {
    "code": 12345,
    "message": "Something has gone wrong",
    "data": [
      { "key": "something", "value": "Feet" }
    ]
  }
}
```

One can find the definitions for the APIs in Open-API format in the source
code in the module `haikudepotserver-api2`.

### Error codes

A set of known RPC error codes are agreed between the client and server.  See the RPC
specification for known error codes used for transport-related issues such as invalid parameters.
Application-specific error codes are documented in the java source at
`org.haiku.haikudepotserver.api1.support.Constants`.

Some errors such as the validation error (code `-32800`) may also carry additional data that provides
detail as to the nature of the error that has arisen.  Java logic for assembling the error
payloads can be found at `org.haiku.haikudepotserver.api1.support.ErrorResolverImpl`.

### Generating API Client for Python

The RPC-over-REST API is expressed as Open-API specifications. These specifications can be used
to generate code that represents a client. To generate a client for the Python language, issue
the following commands from the top level of the project;

```
./mvnw --projects haikudepotserver-api2 package -Ppython
```

The resulting code can be found at
`haikudepotserver-api2/target/generated-sources/openapi/python-client`.

### Background Jobs

Some requests are instant and some take some time to complete. An example of an instant API is
fetching the details of a user. An example of a request that may take some time is producing an
archive of all the icons for all packages in the system.

For the long-run requests the HDS API system provides an asynchronous interface with a generalized
pattern of interaction. This section covers how to use this pattern. The pattern generally follows
the following steps;

1. Create the job using a POST request to a specific job-creation API. An example would be a POST request to `/__api/v2/pkg-job/queue-pkg-icon-export-archive-job`. This API will return a job code.
2. Poll until complete by sending a series of POST requests to `/__api/v2/job/get-job` with the job code and wait for the job status to be `FINISHED` or `FAILED` or `CANCELLED`.
3. The `get-job` response has some data codes under `generatedData` which you can use to obtain the data.
4. GET `/__secured/jobdata/{datacode}/download` to obtain the data. Note the client will need to handle redirects.

Details about the specific API call signatures for any API with paths `/__api/...` can be obtained from the Open-API specification in the `haikudepotserver-api2` module.

Some jobs may require data to be provided. An example of this might be where a tar-ball of screenshots is uploaded to be imported. In this case, HDS provides an API to upload the data which will then return a data code. Where required, the data code can be used with the API to start a job. To access the data upload function, invoke a POST request to `/__secured/jobdata`. The API call will return the data code in a response header `X-HaikuDepotServer-DataGuid`.

See an example using the Python language at `support/client-tools/run_pkg_localization_import.py`.

## Schema

Separate from the RPC-over-HTTP API described in Open-API, some of the data transfer objects (DTO) in the system are generated from JSON Schema. This means that a software artifact is stored in the project to describe the schema and the Java objects that are used in the running software are generated from the schema document. In the Java environment this is done using maven plugins, but it is also possible in the C++ environment too.

The HaikuDepot build system will use the schema to generate C++ objects. You can find this in the `build` directory of the HaikuDepot C++ source. The schema are trans-coded to C++ using Python scripts. The schema files are copied manually from HDS to the HaikuDepot source repositories.

## Rest API

REST API is generally required where data is inappropriate to encode as RPC. This tends to be situations where the data is binary in nature or where data is large in size.  An example of this is where a package icon needs to be uploaded or where bulk packages' data should be returned.

In these examples, `${BASE_URL}` represents the web-location of the application. In production this would be `https://depot-haiku-os.org`.

### Entry point

This API will provide the web application's HTML user interface.

```
curl -X GET "${BASE_URL}/
```

### Trigger Import Repository Data

This API provides a mechanism by which an external client is able to trigger the application to start importing package-related data from a remote repository. This API is provided as REST because the client is likely to be scripted using a scripting language and REST is the most appropriate protocol to employ in this situation. This invocation will trigger the import process, but the import process will execute in a background thread in the application server and will not block the client.

In order to prevent the possibility of this API causing undue load on the application server, it will coalesce overlapping requests. This means that if a succession of requests come in requesting that the repository &quot;xyz&quot; is imported then onle the first one will be honoured and only after the first one is completed with another be accepted.

It is possible for an administrator to configure a password on the Repository.  If this is the case then this API will require a Basic authentication header to be sent to authenticate the request with the configured password.  The username provided in the Basic authentication header is ignored.

```
curl -X --user ":{repo-password}" "${BASE_URL}/__repository/{repositorycode}/import"
curl -X --user ":{repo-password}" "${BASE_URL}/__repository/{repositorycode}/source/{repositorysourcecode}/import"
```

The API will accept HTTP Basic authentication where the password has been set for the repository.

The response will return HTTP status `200` when the job was accepted, `401` when the password was rejected and `404` when the repository or repository source was unable to be found.

### Get package icon

This API is able to provide the icon for a package.  If there is no icon stored then this method will provide a fall-back image if the `f` query parameter is configured to `true` -- otherwise it will return a `404` HTTP status code. Providing a fallback image may not be possible in all cases. The request will return a `Last-Modified` header at second resolution. The timestamps of this header will correlate to the `modifyTimestamp` that is provided in API responses such as `GetPkResult` and `SearchPkgsResult`. The path includes a `mediatype-extension` which can have one of the following values;

- `png`
- `hvif`

A query parameter `s` is possible which may be 16 or 32 to indicate the size in the case of PNG. The returned `Content-Type` header for `hvif` is `application/x-vnd.haiku-icon`. `HEAD` method can be used with this API as well as `GET`.

```
curl -X GET "${BASE_URL}/__pkgicon/{pkgname}.{mediatype-extension}?f=true&s=32
```

An HTTP status code `200` indicates success, `415` indicates the mediatype-extension or size is invalid, `400` indicates the package name is invalid and `404` indicates the package or an icon was not able to be found.

### Get all package icons as an archive

This API is able to generate a &quot;tar-ball&quot; containing all the icon data together with some meta-data about the icons.  Note that the data will be compressed using gzip compression.  After making this call, the client may be redirected to a different URL to actually access the data.  Clients should not make assumptions about the form of the redirected URL.

This API supports the [`If-Modified-Since`](#if-modified-since-header) header.

The tar-ball contains a file `info.json` which has a field `dataModifyTimestamp` that can be used to form the `If-Modified-Since`.  Otherwise, the file contains each entry in the form `hicn/{package-name}/{icon-leaf}.{extension}`. Possible values for the extension are;

- `png`
- `hvif`

The icon-leaf may be `icon` in the case of HVIF data or in the case of a bitmap icon, the icon-leaf will be the size of the image; for example 16, 32 or 64.

```
curl -X GET "${BASE_URL}/__pkgicon/all.tar.gz"
```

The HTTP status `302` indicates a redirect to the actual data payload, `304` indicates that there was no change since the provided `If-Modified-Since` header.

### Get all package versions details

This API is able to provide all of the packages' details sufficient for running the desktop application.

This API supports the [`If-Modified-Since`](#if-modified-since-header) header.

The resultant data transfer objects (DTO) used in this API are available as a json schema within the source.

```
curl -X GET "${BASE_URL}/__pkg/all-{repository-source-code}-{naturalLanguageCode}.json.gz"
```

The HTTP status `302` indicates a redirect to the actual data payload, `304` indicates that there was no change since the provided `If-Modified-Since` header.

### Get reference data

This API is able to generate a JSON payload containing reference data for selected pieces of information in the system such as Countries, Natural Languages and Package Categories.  This can then be used in applications such as HaikuDepot in order to support provision of choices for the user in drop-down lists etc...

This API supports the [`If-Modified-Since`](#if-modified-since-header) header.

The resultant data transfer objects (DTO) used in this API are available as a json schema within the source.

```
curl -X GET "${BASE_URL}/__reference/all-{natural-language-code}.json.gz
```

The HTTP status `302` indicates a redirect to the actual data payload, `304` indicates that there was no change since the provided `If-Modified-Since` header.

### Get all repositories' details

This API is able to generate a large JSON payload containing details regarding each active repository.  Note that the path contains a natural language code such as `de` but at the time of writing there is no support for localizing this information.

This API supports the [`If-Modified-Since`](#if-modified-since-header) header.

The resultant data transfer objects (DTO) used in this API are available as a json schema
within the source.

```
curl -X GET "${BASE_URL}/__repository/all-{natural-language-code}.json.gz
```

The HTTP status `302` indicates a redirect to the actual data payload, `304` indicates that there was no change since the provided `If-Modified-Since` header.

### Get screenshot image

This API is able to produce an image for a screenshot.  The screenshot is identified in the path by its code.  The response will return a `Last-Modified` header at second resolution. Requests for screenshot image should be accompanied by a target width `tw` and height `th`.
These values must be within a range of 1..1500.  The image will maintain its aspect ratio as it is scaled to fit within the supplied target width and height.

```
curl -X GET "${BASE_URL}/__pkgscreenshot/{screenshot-code}.png?tw=640&th=480
```

An HTTP status of `200` indicates success, `415` indicates the width or height is invalid, `400` indicates the screenshot code was not supplied and `404` that the screenshot was not found for the code supplied.

### Get raw screenshot image

This API is able to provide the *raw* screenshot data.

```
curl -X GET "${BASE_URL}/__pkgscreenshot/{screenshot-code}/raw
```

HTTP status code `200` indicates success and `404` indicates that the screenshot was not found.

### Add screenshot

This API is able to add an image as a screenshot for the nominated package.  The screenshot will be ordered last.  The payload of the `POST` must be a PNG image that is a maximum of 1500x1500 pixels and a maximum of 2MB in size.

```
curl -X POST -H "Content-Type: image/png" --data-binary @file.png \
"${BASE_URL}/__pkgscreenshot/{pkgname}/add?format=png"
```

The HTTP status code `200` indicates that the screenshot was uploaded. In this case a header `X-HaikuDepotServer-ScreenshotCode` will also be returned which contains the screenshot's code.

The HTTP status code `415` indicates that the size of the image was too large or the format was not supplied, `404` indicates that the package was unable to be found and `400` indicates that the package name was not supplied.

## Actuator

SpringBoot actuator is an API, in the case of HDS exposed on a **different port**, which allows for observability and maintenance. In addition to any standard actuator endpoints, HDS also offers a maintenance service. It is possible to send HTTP `POST` requests to the endpoint to trigger the daily and hourly maintenance within the application.

```
curl -X POST -H "Content-Type: application/json" \
--data '{"type":"HOURLY"}' "${BASE_URL}/actuator/hdsmaintenance"
```

Possible values for `type` are;

- `HOURLY`
- `DAILY`