# Security: Digest



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-security-digest")
}
```

### About

Support for integrating with servers secured by Digest authentication; useful for working with legacy
servers or IOT devices that don't typically support TLS.  For completeness, a Digest Provider has also been included for use with servers.

Digest authentication is useful for protecting credentials in transit when traffic isn't encrypted.
Instead of the client transmitting plain-text or encrypted credentials, it sends a hash of the credentials instead; this ensures
a man-in-the-middle can never intercept the credentials, despite the connection being insecure.

Despite being made redundant by TLS, digest authentication has a major disadvantage; it typically requires user credentials
to be accessible by the server.  In most other authentication mechanisms, the server can store a non-reversible hash, which reduces the severity of a database breach.

At it's most basic, the digest authentication flow works like this:

1. Client makes an HTTP call to a server protected by Digest authentication
2. The server responds with an `HTTP 401`, including a `Digest` challenge in the `WWW-Authenticate` header.
This header includes all the information the client needs to correctly generate a credentials `hash`
3. With the user-supplied credentials, the client converts them into `hash` and encodes them as a hexadecimal `digest`,
then transmits them to the server, along with the plaintext `username`
4. With the `username` given by the client, the server looks up the `password` for that user, generates the expected `hash`,
   and compares is to the one supplied by the client.  If they match, it grants the client access to the protected resource


### Example Provider 

This example has an integrated username/password store; you will want to come up with your own version, with credentials encrypted at rest.

The server accepts a path parameter, and parrots back the value provided by the client.





```kotlin
package content.ecosystem.http4k.reference.digest

import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DigestAuth
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val users = mapOf(
        "admin" to "password",
        "user" to "hunter2"
    )

    val routes = routes(
        "/hello/{name}" bind GET to { request ->
            val name = request.path("name")
            Response(OK).body("Hello $name")
        }
    )

    val authFilter = ServerFilters.DigestAuth(
        realm = "http4k",
        passwordLookup = { username -> users[username] })

    authFilter
        .then(routes)
        .asServer(SunHttp(8000))
        .start()
        .block()
}

```



### Example Client  

This example integrates with the provider above, sending a request with a value to be parroted back.





```kotlin
package content.ecosystem.http4k.reference.digest

import org.http4k.client.JavaHttpClient
import org.http4k.core.Credentials
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.DigestAuth

fun main() {
    val credentials = Credentials("admin", "password")

    val client = ClientFilters.DigestAuth(credentials)
        .then(JavaHttpClient())

    val request = Request(GET, "http://localhost:8000/hello/http4k")

    val response = client(request)
    println(response)
}

```



