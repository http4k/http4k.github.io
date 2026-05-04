# Client as a function


This example demonstrates using http4k as a client, to consume HTTP services. A client is just another HttpHandler.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Code





```kotlin
package content.howto.client_as_a_function

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request

fun main() {

    val request = Request(Method.GET, "https://xkcd.com/info.0.json")

    val client: HttpHandler = JavaHttpClient()

    println(client(request))
}

```



