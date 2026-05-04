# Server-as-a-Function


This example is the simplest possible "server" implementation. Note that we are not spinning up a server-backend here - but the entire application(!) is testable by firing HTTP requests at it as if it were.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Code





```kotlin
package content.howto.server_as_a_function

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

fun main() {

    val app: HttpHandler = { request: Request ->
        Response(Status.OK).body("Hello, ${request.query("name")}!")
    }

    val request = Request(Method.GET, "/").query("name", "John Doe")

    val response = app(request)

    println(response)
}

```



