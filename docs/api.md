# API

## General

This information applies to all areas of the API for the system.

### Clients

A client of this system's API may be a desktop application (eg; Haiku Depot) or may be logic in a web page where the web page is using some java-script to call back to the server.

### API Version
The application generally refers to "api 1" in anticipation that at some point there is the _possibility_ that a second, incompatible API may be required.

### Object References
When objects such as packages or users are referred to in the API, the database primary key is **not** used.  Instead either a natural or artifical identifier is employed.  For example, in the case of a package, the package's name may be used.  In the case of a screen-shot, a GUID (unique identifier) will be used to identify a particular screenshot.

### Reference Data
Reference data means data in the application that is generally invariant.  Examples include the mime types, natural language, url types and so on.  In these cases, an API will generally provide for access to the list of reference data.  The client is expected to obtain such reference data as necessary and cache reference data locally.

### Invocations and Transport
The term _invocation_ refers to a request-response cycle from the client software into the application server over the HTTP protocol.  Each API invocation is made in a _stateless_ manner in that each invocation is not dependent on the prior invocation.

### Authentication
Authentication of invocations uses [basic authentication](http://en.wikipedia.org/wiki/Basic_access_authentication) for both REST and JSON-RPC.  This procedure involves an HTTP header being included in each invocation that identifies the user and also provides their password.  The value of the header includes a base-64 encoded string of the username and password, separated by a colon.  This is an example;

```
Authorization: Basic dXNlcjpwYXNzd29yZA==
```

If the authentication fails then the request will continue unauthenticated; there is no 'challenge' returned back to the caller.  A special API exists to provide a means to test an authentication.

The application does not support cookie-based authentication.

## JSON-RPC API

Most API is vended as JSON-RPC(http://www.jsonrpc.org) encoded HTTP POST invocations.  The data transfer objects (DTOs) that describe the request and the response data as well as the APIs' interfaces exist in the "haikudepotserver-api" module in the java package "org.haikuos.haikudepotserver.api1".

The documentation and list of available methods can be obtained by viewing the java interfaces and model objects in that module.

### Exmaple; Get a Package

In this example, the client knows the _name_ of the package and would like to get the details of the package.  The java model objects that document the data required in request and the data that can be expected in the response can be found in the project;

* org.haikuos.haikudepotserver.api1.model.pkg.GetPkgRequest
* org.haikuos.haikudepotserver.api1.model.pkg.GetPkgResult

The method that is invoked can be found at;

```
org.haikuos.haikudepotserver.api1.PkgApi#getPkg(..)
```

You will notice at the top of this interface, there is an annotation that describes the path or "endpoint" for this API.  In this case it is "/api/v1/pkg".  Given a host and port, this can be extrapolated into a URL that can be used to invoke to this method;

```
http://localhost:8080/api/v1/pkg
```

The invocation is made using the HTTP protocol with the method POST.  The Content-Type HTTP header must be set to "application/json" for both the request and the response.  The request object would look something like this;

```
{
 "jsonrpc":"2.0",
 "id":4143431,
 "method":"getPkg",
 "params":[{
   "name":"apr",
   "architectureCode":"x86",
   "versionType":"NONE"
 }]
}
```

All going well, the following (abridged) form of response would be sent back to the client;

```
{
 "jsonrpc":"2.0",
 "id":4143431,
 "result":{
  "name":"apr",
  "hasIcon":true,
  "canEdit":false,
  "versions":[],
  "modifyTimestamp":12345678
 }
}
```

See the [JSON-RPC](http://www.jsonrpc.org) web site for examples of the response envelope format for the scenario in which an error has arisen in the invocation.

### Error Codes

A set of known JSON-RPC error codes are agreed between the client and server.  See the JSON-RPC 'specification' for known error codes used for transport-related issues such as invalid parameters.  Application-specific error codes are documented in the java source at;

* org.haikuos.haikudepotserver.api1.support.Constants

Some errors such as the validation error (code -32800) may also carry additional data that provides detail as to the nature of the error that has arisen.  Java logic for assembling the error payloads can be found at;

* org.haikuos.haikudepotserver.api1.support.ErrorResolverImpl

## REST API

REST API is generally required where data is inappropriate to encode as JSON-RPC.  This tends to be situations where the data is binary in nature.  An example of this is where a package icon needs to be uploaded.

### Entry Point
This API will provide the web application's HTML user interface.

* HTTP Method : GET
* Path : /
* Response _Content-Type_ : text/html

### Import Repository Data

This API provides a mechanism by which an external client is able to trigger the application to start importing package-related data from a remote repository.  This API is provided as REST because the client is likely to be scripted using a scripting language and REST is the most appropriate protocol to employ in this situation.  This invocation will trigger the import process, but the import process will execute in a background thread in the application server and will not block the client.

* HTTP Method : GET
* Path : /importrepositorydata
* Response _Content-Type_ : text/plain
* Query Parameters
	*  **code** : Identifies the repository from which data should be obtained
* Expected HTTP Status Codes
	* **200** : The import job was accepted
	* **400** : The code was not supplied in the request

An example URL is;

```
http://localhost:8080/importrepositorydata?code=haikuportsprod
```

### Get Package Icon

This API is able to provide the icon for a package.  If there is no icon stored then this method will provide a fall-back image.

* HTTP Method : GET
* Path : /pkgicon/<pkgname>.png
* Response _Content-Type_ : image/png
* Query Parameters
	* **size** : Either 16 or 32 for the number of pixels; other values are not allowed
* Expected HTTP Status Codes
	* **200** : The icon is provided in the response
	* **415** : The path did not include ".png" or the size is invalid
	* **400** : The package name was not supplied
	* **404** : The package was not found
	
An example URL is;

```
http://localhost:8080/pkgicon/apr.png?size=32
```

### Put Package Icon

This API is able to store an icon for a given size for a package.

* HTTP Method : PUT
* Path : /pkgicon/<pkgname>.png 	 
* Query Parameters
	* **size** : Either 16 or 32 for the number of pixels; other values are not allowed
* Expected HTTP Status Codes
	* **200** : The icon was stored
	* **415** : The path did not include ".png" or the size is invalid or the supplied data is not in PNG format or the size of the suppied data does not agree with the size specified in the query parameter.
	* **404** : The package identified in the path was not able to be found
	* **400** : The package name was not supplied
	
An example URL is;

```
http://localhost:8080/pkgicon/apr.png?size=32
```
   


