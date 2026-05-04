# Security: OAuth



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-security-oauth")
}
```

### About

Support for using integrating with external OAuth2 providers for authentication purposes and to provide access to external APIs of entities such as Auth0, Google etc. 


Specifically, http4k supports the popular OAuth2 `Authorization Code` and `Refresh Token` Grants.

### Authorization Code Grant

This flow requires user interaction; it uses a callback mechanism that plays out like this:

1. App developer (you!) creates an application on the OAuth provider and receives a `Client Id` and a `Client Secret`. You also provide a "callback" URL to the provider which will be used later.
2. When accessing a protected resource, your app checks for an `AccessToken` from the user (via cookie or similar)
3. If the user has no token, the app redirects the user browser back to the OAuth provider site, along with the "state" of the user - containing a generated `CrossSiteRequestForgeryToken` (CSRF - which is also stored by the app) and the original URI the user was trying to access.
4. The user logs in on the OAuth provider site, which generates a code that is returned as a query parameter in a redirect back to the registered callback URL in your app, along with the CSRF token.
5. Your app checks the content of the CSRF token to determine that the redirect is genuine, then sends the received code back to the OAuth provider in exchange for a valid `AccessToken`. This completes the flow
6. The `AccessToken` can then be used to access various services from the OAuth provider APIs.

There is a single user-defined interface, `OAuthPersistence`, required to implement to enable this flow. This interface is required to provide the custom way in which your application will store and retrieve the `CSRF` and `AccessToken` for a request. A common way to do this is through Cookies, but the values should definitely be encrypted. http4k only provides an insecure version of this class that you can use for testing. In order to remain provider-agnostic, the AccessToken object also contains the entirety of the (typically JSON) token response from the provider, which may include other fields depending on the types of scope for which your application is authorised by the user.

To enable OAuth integration, construct a configured instance of `OAuthProvider`. This provides 3 things:

1. A filter to protect application resources
1. A callback HttpHandler for the OAuth provider to redirect the authenticated user to
1. A fully configured API client (which populated the Host on the URI) - this allows different
implementations of the provider to be used across environments.

#### Example provider 

Out of the box, http4k provides implementations for several OAuth providers.





```kotlin
package content.ecosystem.http4k.reference.oauth

import org.http4k.client.ApacheClient
import org.http4k.core.Credentials
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.InsecureCookieBasedOAuthPersistence
import org.http4k.security.OAuthProvider
import org.http4k.security.google
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {

    // set these before running this example
    val googleClientId = System.getenv("CLIENT_ID")
    val googleClientSecret = System.getenv("CLIENT_SECRET")

    val port = 9000

    // the callback uri which is configured in our OAuth provider
    val callbackUri = Uri.of("http://localhost:$port/callback")

    // this is a test implementation of the OAuthPersistence interface, which should be
    // implemented by application developers
    val oAuthPersistence = InsecureCookieBasedOAuthPersistence("Google")

    // pre-defined configuration exist for common OAuth providers
    val oauthProvider = OAuthProvider.google(
        ApacheClient(),
        Credentials(googleClientId, googleClientSecret),
        callbackUri,
        oAuthPersistence
    )

    // the 2 main points here are the callback handler and the authFilter, which protects the root resource
    val app: HttpHandler =
        routes(
            callbackUri.path bind GET to oauthProvider.callback,
            "/" bind GET to oauthProvider.authFilter.then { Response(OK).body("hello!") }
        )

    ServerFilters.CatchAll()
        .then(app)
        .asServer(SunHttp(port)).start().block()
}

// browse to: http://localhost:9000 - you'll be redirected to google for authentication

```



See the [how-to guides](/howto/use_a_custom_oauth_provider/) for a custom implementation.

### Refresh Token Grant

This flow can be performed "offline", i.e. once a user has given their consent via the `Authorization Code` grant, your server can continue to access protected resources on behalf of the user after the original `AccessToken` expires.

The flow plays out like this:

1. After the user performs the `Authorization Code` grant, your server will store the `RefreshToken`
2. The next time you need to access a protected resource on behalf of the user, your server retrieves the user's `RefreshToken`, then uses the preconfigured `Client Id` and `Client Secret` to exchange the `RefreshToken` for a new `AccessToken`.
3. [Optional] Your server can cache the refreshed `AccessToken` if several calls need to be made in a short period of time

#### Example Filter 

Http4k provides a filter that can be attached to your client to authorize requests to the OAuth resource server.





```kotlin
package content.ecosystem.http4k.reference.oauth

import org.http4k.client.JavaHttpClient
import org.http4k.core.Credentials
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.security.OAuthProviderConfig
import org.http4k.security.oauth.client.OAuthOffline
import org.http4k.security.oauth.core.RefreshToken

fun main() {
    // set these before running example
    val refreshToken = RefreshToken(System.getenv("REFRESH_TOKEN"))
    val clientCredentials =
        Credentials(System.getenv("CLIENT_ID"), System.getenv("CLIENT_SECRET"))
    val authServerBase = Uri.of(System.getenv("OAUTH_AUTH_SERVER_HOST"))
    val resourceServerHost = Uri.of(System.getenv("OAUTH_RESOURCE_SERVER_HOST"))

    // all the configuration we need to talk to our OAuth Auth Server (Google, Auth0, Okta, etc.)
    val config = OAuthProviderConfig(
        authBase = authServerBase,
        authPath = "/oauth2/authorize",
        tokenPath = "/oauth2/token",
        credentials = clientCredentials
    )

    // construct a client with a filter to authorize our requests to the OAuth Resource server
    val client = ClientFilters.SetHostFrom(resourceServerHost)
        .then(ClientFilters.OAuthOffline(config, refreshToken, JavaHttpClient()))
        .then(JavaHttpClient())

    // Make a request to the OAuth Resource server
    val request = Request(GET, "/v1/secure/resource")
    val response = client(request)
    println(response)
}

```



