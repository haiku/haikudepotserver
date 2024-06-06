# Security

This section aims to outline the security-system employed.  The system does not use sessions and therefore exposes its web services in a stateless manner.  This implies that each request-response cycle is considered
in isolation.

Note that if you are using Haiku in a virtualized environment such as [VirtualBox](https://www.virtualbox.org/), you need to ensure that the virtualization system exposes UTC-relative time to the Haiku guest operating system, that Haiku is configured (see "Time Preferences") to use a GMT hardware clock and that your time-zone is set.

## Authentication

In a production environment, transport to and from the application server MUST use the `https` protocol in order to ensure that the payloads are not transmitted over networks in the clear.  The payloads will contain sensitive authentication data.

Authentication of invocations for REST APIs use "token bearer authentication". The application does not support cookie-based sessions.  If the authentication fails then the request will continue unauthenticated; there is no "challenge" necessarily returned back to the caller. A special API exists to provide a means to test a username and password authentication for the purposes of a login panel or similar; `/__api/v2/user/authenticate-user`. This endpoint will return a token if the authentication was successful.

### Token Bearer

This is a system of authentication that relies on a token being supplied with a client API invocation to the server.  The client will first use the API `/__api/v2/user/authenticate-user` to initially authenticate and obtain a token. This token can then be used to authenticate further API calls by including it in an HTTP header. An (abbreviated) example of the header might be; `Authorization: Bearer eyJhbGciOiJIUzI.eyJleHAiOjE0MDM5NDY.1ifnDTLvX3A`.

The format and structure of the token conforms with the `json web token` standard.  These tokens are signed using a secret, will expire after a certain length of time and are specific to a given deployment environment.  The deployment environment is identified by configuring an `issuer`.  See [the configuration chapter](config.md) for further details on configuration of these values.

As the token has an expiry, an API method `/__api/v2/user/renew-token` exists to obtain a fresh token before the existing token expires.  The expiry can be read from the token; read about "json web tokens" to find out how they work.

The token bearer system of authentication has the advantage that although a user's password is supplied by the user to the client software for the initial authentication, subsequent requests do not need to convey the password.  In addition, the tokens expire and are unable to be employed after they have expired.

### Token Bearer on GET Queries

Certain HTTP requests may be authenticated using a URL query parameter.  This approach only applies when the URL path has a prefix `/__secured/` and when the URL is employed with the HTTP `GET` method.  The query parameter key is `hdsbtok` and the value
is a "json web token" as described above.

Be aware that use of this technique is likely result in the token being visible in the user's browser history.  The token is subject to expiry, but may be able to be utilized for some period after it has been employed in this way.

## Authorization

A user's ability to undertake some action is controlled by the authorization system.  In a given context, a user and a permission will be considered.  The enumeration &quot;Permission&quot; contains a list of the permissions that are enforced by the application.  Each permission is either with respect to the currently authenticated user or some target object.  Examples of some permissions and the type of target objects that they apply to are;

|Permission|Target|
|---|---|
|`USER_CHANGEPASSWORD`|A User|
|`PKG_EDITSCREENSHOT`|A Pkg|
|`REPOSITORY_LIST`|Current User|

There are inherent authorization rules that are encoded in the implementation of the method `AuthorizationService.check(..)` and there are also configurable rules that can be setup for certain permissions in relation to packages.  These are stored in the database via the entity `PermissionUserPkg`.  Note that a given `PermissionUserPkg` may have a null package associated with it and in this case, the rule applies to all packages.

As part of an API invocation, an HTTP status `401` or `403` response maybe returned should authorization checks fail.