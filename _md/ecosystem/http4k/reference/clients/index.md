# Clients



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    
    // Java (for development only):
    implementation("org.http4k:http4k-core")
    
    // Apache v5 (Sync): 
    implementation("org.http4k:http4k-client-apache")
    
    // Apache v4 (Sync): 
    implementation("org.http4k:http4k-client-apache4")
    
    // Apache v5 (Async): 
    implementation("org.http4k:http4k-client-apache-async")
    
    // Apache v4 (Async): 
    implementation("org.http4k:http4k-client-apache4-async")
    
    // Fuel (Sync + Async): 
    implementation("org.http4k:http4k-client-fuel")
    
    // Helidon (Loom): 
    implementation("org.http4k:http4k-client-helidon")
    
    // Jetty (Sync + Async + WebSocket): 
    implementation("org.http4k:http4k-client-jetty")
    
    // OkHttp (Sync + Async): 
    implementation("org.http4k:http4k-client-okhttp")
    
    // Websocket: 
    implementation("org.http4k:http4k-client-websocket")
}
```

### HTTP
Supported HTTP client adapter APIs are wrapped to provide an `HttpHandler` interface in 1 LOC.
Since each client acts as an `HttpHandler`, it can be decorated with various `Filter` implementations, such as those available in `ClientFilters`.
This allows handling cross-cutting concerns independently of a specific client implementation, greatly facilitating testing.

Activate streaming mode by passing a `BodyMode` (default is non-streaming).

`ClientFilters` offers a collection of filters that can be applied to an `HttpHandler` to manage common cross-cutting concernsas a chain of filters. 
This chain allows for the easy configuration and management of complex processing sequences.
`ClientFilters` includes specific filters that enable frequently needed functionalities like authentication, caching, or compression with minimal configuration.

These examples are for the Apache HTTP client, but the API is similar for the others:

#### Code





```kotlin
package content.ecosystem.http4k.reference.clients

import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.http4k.client.ApacheAsyncClient
import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode
import org.http4k.core.Method.GET
import org.http4k.core.Request
import kotlin.concurrent.thread

fun main() {

    // standard client
    val client = ApacheClient()
    val request = Request(GET, "http://httpbin.org/get").query("location", "John Doe")
    val response = client(request)
    println("SYNC")
    println(response.status)
    println(response.bodyString())

    // streaming client
    val streamingClient = ApacheClient(responseBodyMode = BodyMode.Stream)
    val streamingRequest = Request(GET, "http://httpbin.org/stream/100")
    println("STREAM")
    println(streamingClient(streamingRequest).bodyString())

    // async supporting clients can be passed a callback...
    val asyncClient = ApacheAsyncClient()
    asyncClient(Request(GET, "http://httpbin.org/stream/5")) {
        println("ASYNC")
        println(it.status)
        println(it.bodyString())
    }

    // ... but must be closed
    thread {
        Thread.sleep(500)
        asyncClient.close()
    }

    // custom configured client
    val customClient = ApacheClient(
        client = HttpClients.custom().setDefaultRequestConfig(
            RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .build()
        )
            .build()
    )
}

```



Additionally, all HTTP client adapter modules allow for custom configuration of the relevant underlying client. Async-supporting clients implement the `AsyncHttpClient` interface can be passed a callback.

### Websocket
http4k supplies both blocking and non-blocking Websocket clients. The former is perfect for integration testing purposes, and as it uses the same interface `WsClient` as the in-memory test client (`WsHandler.testWsClient()`) it is simple to write unit tests which can then be reused as system tests by virtue of swapping out the client.

### Feature support

**http4k** provides support for the following Client backends:

| Server         | HTTP | WebSockets | Notes                 |  
|----------------|------|------------|-----------------------|
| Apache         | ✅    | ❌          |                       |
| Apache Async   | ✅    | ❌          |                       |
| Apache 4       | ✅    | ❌          | Legacy                |
| Apache 4 Async | ✅    | ❌          | Legacy                |
| Fuel           | ✅    | ❌          |                       |
| Helidon        | ✅    | ❌          |                       |
| Jetty          | ✅    | ✅          |                       |
| Java           | ✅    | ❌          |                       |
| Java 8         | ✅    | ❌          | Legacy, no cold start |
| OkHttp         | ✅    | ✅          |                       |
| Websocket      | ❌    | ✅          |                       |

#### Code





```kotlin
package content.ecosystem.http4k.reference.clients

import org.http4k.client.WebsocketClient
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.websocket.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse

fun main() {

    // a standard websocket app
    val server = websockets(
        "/bob" bind
            { _: Request ->
                WsResponse { ws ->
                    ws.send(WsMessage("bob"))
                    ws.onMessage {
                        println("server received: $it")
                        ws.send(it)
                    }
                }
            }
    ).asServer(Jetty(8000)).start()

    // blocking client - connection is done on construction
    val blockingClient =
        WebsocketClient.blocking(Uri.of("ws://localhost:8000/bob"))
    blockingClient.send(WsMessage("server sent on connection"))
    blockingClient.received().take(2)
        .forEach { println("blocking client received: $it") }
    blockingClient.close()

    // non-blocking client - exposes a Websocket interface for attaching listeners,
    // and connection is done on construction, but doesn't block - the (optional) handler
    // passed to the construction is called on connection.
    val nonBlockingClient =
        WebsocketClient.nonBlocking(Uri.of("ws://localhost:8000/bob")) {
            it.run {
                send(WsMessage("client sent on connection"))
            }
        }

    nonBlockingClient.onMessage {
        println("non-blocking client received:$it")
    }

    nonBlockingClient.onClose {
        println("non-blocking client closing")
    }

    Thread.sleep(100)

    server.stop()
}

```



#### Testing Websockets with offline and online clients 





```kotlin
package content.ecosystem.http4k.reference.clients

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.client.WebsocketClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.lens.Path
import org.http4k.routing.websocket.bind
import org.http4k.routing.websockets
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.testing.testWsClient
import org.http4k.websocket.WsClient
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

val namePath = Path.of("name")

// here is our websocket app - it uses dynamic path binding and lenses
val testApp: WsHandler = websockets(
    "/{name}" bind  { req: Request ->
        WsResponse { ws ->
            val name = namePath(req)
            ws.send(WsMessage("hello $name"))
        }
    }
)

// this is the abstract contract that defines the behaviour to be tested
abstract class WebsocketContract {
    // subclasses only have to supply a blocking WsClient
    abstract fun client(): WsClient

    @Test
    fun `echoes back connected name`() {
        assertThat(
            client().received().take(1).toList(),
            equalTo(listOf(WsMessage("hello bob")))
        )
    }
}

// a unit test version of the contract - it connects to the websocket in memory with no network
class WebsocketUnitTest : WebsocketContract() {
    override fun client() =
        testApp.testWsClient(Request(GET, "/bob"))
}

// a integration test version of the contract -
// it starts a server and connects to the websocket over the network
class WebsocketServerTest : WebsocketContract() {
    override fun client() = WebsocketClient.blocking(Uri.of("ws://localhost:8000/bob"))

    private val server = testApp.asServer(Undertow(8000))

    @BeforeEach
    fun before() {
        server.start()
    }

    @AfterEach
    fun after() {
        server.stop()
    }
}

```



