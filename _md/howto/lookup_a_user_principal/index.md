# Lookup a user principal



### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

When authorising requests, it is common to need to store some credentials or a user principal object to be accessible by a further Filter or the eventual HttpHandler.

This can be easily achieved by combining the typesafe `RequestKey` functionality with one of the built-in authorisation Filters:

### Code





```kotlin
package content.howto.lookup_a_user_principal

import org.http4k.core.Credentials
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ServerFilters.BearerAuth
import org.http4k.lens.RequestKey

fun main() {

    val credentials = RequestKey.required<Credentials>("credentials")

    val app = BearerAuth(credentials) {
        if (it == "42") Credentials("user", "pass") else null
    }.then { Response(OK).body(credentials(it).toString()) }

    println(app(Request(GET, "/").header("Authorization", "Bearer 41")))
    println(app(Request(GET, "/").header("Authorization", "Bearer 42")))
}

```



