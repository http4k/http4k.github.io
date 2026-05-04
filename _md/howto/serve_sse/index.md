# Serve SSE


### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-undertow")
}
```

**http4k** provides SSE (Server Sent Events) support using a simple, consistent, typesafe, and testable API on supported
server backends (see above). SSE communication consists of 3 main concepts:

1. `SseHandler` - represented as a typealias: `SseHandler =  (Request) -> SseResponse`. This is responsible for matching
   an HTTP request to an SSE handler.
1. `SseConsumer` - represented as a typealias: `SseConsumer = (Sse) -> Unit`. This function is called on connection of a
   Sse and allow the API user to receive to events coming from the connected SSE handler.
1. `SseMessage` - a message which is sent from the SSE handler SseMessages are immutable data classes.
1. `SseFilter` - represented as a interface: `SseFilter = (SseHandler) -> SseHandler`. This allows for the decoration
   of `SseHandlers` to add pre or post matching behaviour in the same way as a standard `Filter`.

### SSE as a Function

The simplest possible SSE handler can be mounted as a `SseConsumer` function onto a server with:





```kotlin
package content.howto.serve_sse

import org.http4k.routing.sse
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.sse.Sse
import org.http4k.sse.SseMessage

val server = sse({ sse: Sse -> sse.send(SseMessage.Data("hello")) }).asServer(Undertow(9000)).start()

```



### Mixing HTTP and SSE services

Both SSE and Http handlers in **http4k** are routed using a similar path-based API. We combine them into a single
`PolyHandler`. SSE handlers react to HTTP traffic which send an `Accept` header with `text/event-stream` value:





```kotlin
import org.http4k.client.JavaHttpClient
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.accept
import org.http4k.routing.bindHttp
import org.http4k.routing.bindSse
import org.http4k.routing.poly
import org.http4k.routing.routes
import org.http4k.routing.sse
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.sse.Sse
import org.http4k.sse.SseFilter
import org.http4k.sse.SseMessage
import org.http4k.sse.SseResponse
import org.http4k.sse.then
import kotlin.concurrent.thread

fun main() {
    val namePath = Path.of("name")

    // a filter allows us to intercept the call to the sse and do logging etc...
    val sayHello = SseFilter { next ->
        {
            println("Hello from the sse!")
            next(it)
        }
    }

    val sse = sayHello.then(
        sse(
            // we can use bindSse to bind a normal sse route (this is an alias)
            "/hello/{name}" bindSse { req ->
                SseResponse { sse: Sse ->
                    val name = namePath(req)
                    thread {
                        repeat(10) {
                            sse.send(SseMessage.Data("hello $it"))
                            Thread.sleep(100)
                        }
                        sse.close()
                    }
                    sse.onClose { println("$name is closing") }
                }
            }
        )
    )

    // we can use bindHttp to bind a normal http route (this is an alias)
    val http = routes("{all:.+}" bindHttp GET to { req: Request ->
        Response(OK).body("hitting HTTP server: " + req.uri)
    })

    poly(http, sse).asServer(Undertow(9000)).start()

    val httpClient = JavaHttpClient()

    // send an sse request - we need the event stream accept content type for it to be picked up by the Sse handler
    httpClient(
        Request(GET, "http://localhost:9000/hello/bob")
            .accept(ContentType.TEXT_EVENT_STREAM)
    ).bodyString().let(::println)

    // if the SSE does not match it will fall back to HTTP
    httpClient(
        Request(GET, "http://localhost:9000/notbob")
            .accept(ContentType.TEXT_EVENT_STREAM)
    ).bodyString().let(::println)

    // without the accept header, it will just be a normal http request
    httpClient(
        Request(GET, "http://localhost:9000/hello/no_accept_header")
    ).bodyString().let(::println)
}

```



Note that if the accept header is not set, or the SSE cannot service the request, the HTTP server will be used as a
fallback.

### CORS protection for SSE

CORS (Cross-Origin Resource Sharing) is a security feature implemented by web browsers to prevent malicious websites
from making requests to a different domain than the one that served the web page. When using SSE, you should 
configure your server to allow cross-origin requests only from trusted domains.





```kotlin
package content.howto.serve_sse

import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.then
import org.http4k.filter.AnyOf
import org.http4k.filter.CorsAndRebindProtection
import org.http4k.filter.CorsPolicy
import org.http4k.filter.OriginPolicy
import org.http4k.filter.PolyFilters
import org.http4k.routing.poly
import org.http4k.routing.sse
import org.http4k.routing.sse.bind
import org.http4k.server.Helidon
import org.http4k.server.asServer
import org.http4k.sse.SseMessage

fun main() {
    val sseServer = poly(
        "/sse" bind sse { sse ->
            sse.send(SseMessage.Data("hello!"))
            sse.send(SseMessage.Data("world!"))
            sse.close()
        }
    )

    // Define a CORS policy to protect against cross-origin requests and DNS rebinding attacks
    val corsPolicy = CorsPolicy(
        OriginPolicy.AnyOf("foo.com", "localhost"),
        listOf("allowed-header"), listOf(GET, POST, DELETE)
    )

    PolyFilters.CorsAndRebindProtection(corsPolicy)
        .then(sseServer)
        .asServer(Helidon(3002)).start()
}

```





