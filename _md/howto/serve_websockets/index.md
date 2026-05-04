# Serve Websocket


### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-undertow")
    implementation("org.http4k:http4k-client-websocket")
    implementation("org.http4k:http4k-format-jackson")
}
```

**http4k** provides Websocket support using a simple, consistent, typesafe, and testable API on supported server backends (see above). Websocket communication consists of 4 main concepts:

1. `WsHandler` - represented as a typealias: `WsHandler =  (Request) -> WsConsumer`. This is responsible for matching an HTTP request to a websocket.
1. `WsConsumer` - represented as a typealias: `WsConsumer = (WebSocket) -> Unit`. This function is called on connection of a websocket and allow the API user to react to events coming from the connected websocket.
1. `WsMessage` - a message which is sent or received on a websocket. This message can take advantage of the typesafety accorded to other entities in http4k by using the Lens API. Just like the [**http4k**](https://github.com/http4k/http4k) HTTP message model, WsMessages are immutable data classes.
1. `WsFilter` - represented as a interface: `WsFilter = (WsConsumer) -> WsConsumer`. This allows for the decoration of `WsConsumers` to add pre or post matching behaviour in the same way as a standard `Filter`.

### Websocket as a Function
The simplest possible Websocket can be mounted as a `WsConsumer` function onto a server with:




```kotlin
package content.howto.serve_websockets

import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage

val server = { ws: Websocket -> ws.send(WsMessage("hello")) }.asServer(Jetty(9000)).start()

```



### Mixing HTTP and Websocket services 
Both Websockets and Http handlers in **http4k** are routed using a similar path-based API. We combine them into a single `PolyHandler` which can handle both `http://` and `ws://`, and then convert to a Server as usual:





```kotlin
package content.howto.serve_websockets

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.routing.bindHttp
import org.http4k.routing.poly
import org.http4k.routing.routes
import org.http4k.routing.websocket.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse

fun main() {
    val namePath = Path.of("name")

    val ws = websockets(
        "/{name}" bind { req: Request ->
            WsResponse { ws: Websocket ->
                val name = namePath(req)
                ws.send(WsMessage("hello $name"))
                ws.onMessage {
                    ws.send(WsMessage("$name is responding"))
                }
                ws.onClose { println("$name is closing") }
            }
        }
    )
    val http = routes("all:{.+}" bindHttp GET to { _: Request ->
        Response(OK).body("hiya world")
    })

    poly(http, ws).asServer(Jetty(9000)).start()
}

```



### Auto-marshalling Websockets messages 
Using the standard Lens API, we can auto-convert Websocket messages on and off the wire. This example uses the Jackson for the marshalling:





```kotlin
package content.howto.serve_websockets

import org.http4k.client.WebsocketClient
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.format.Jackson.auto
import org.http4k.routing.websocket.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse

data class Person(val name: String, val age: Int)

fun main() {

    // a lens that will marshall the Person object on and off the wire
    val personLens = WsMessage.auto<Person>().toLens()

    val server = websockets(
        "/ageMe" bind { req: Request ->
            WsResponse { ws: Websocket ->
                ws.onMessage {
                    val person = personLens(it)
                    ws.send(personLens.create(person.copy(age = person.age + 10)))
                    ws.close()
                }
            }
        }
    ).asServer(Jetty(8000)).start()

    val client = WebsocketClient.blocking(Uri.of("ws://localhost:8000/ageMe"))

    // send a message in "native form" - we could also use the Lens here to auto-marshall
    client.send(WsMessage("""{ "name":"bob", "age": 25 }"""))

    // read all of the messages from the socket until it is closed (by the server).
    // we expect to get one message back before the stream is closed.
    client.received().toList().forEach(::println)

    server.stop()
}

```



### Testing Websockets 
**http4k** provides Websockets that are both typesafe (via the Lens API), and testable. Both `WsHandlers` and `PolyHandlers` are convertible to a `WsClient` which provides a synchronous API for testing reactions to Websocket events in an offline environment.

In the below example, we have gone one step further - defining a contract test case and then providing 2 implementations of it - one for unit-testing (in memory), one using a server. [**http4k**](https://github.com/http4k/http4k) provides clients with an identical interface for both cases, meaning it's possible reuse the same test logic:





```kotlin
package content.howto.serve_websockets

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
import org.http4k.websocket.WsFilter
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.http4k.websocket.then
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

val namePath = Path.of("name")

// a filter allows us to intercept the call to the websocket and do logging etc...
val sayHello = WsFilter { next ->
    {
        println("Hello from the websocket!")
        next(it)
    }
}

// here is our websocket app - it uses dynamic path binding and lenses
val testApp: WsHandler = sayHello.then(
    websockets(
        "/{name}" bind { req: Request ->
            WsResponse { ws ->
                val name = namePath(req)
                ws.send(WsMessage("hello $name"))
            }
        }
    )
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
    override fun client() = testApp.testWsClient(Request(GET, "/bob"))
}

// a integration test version of the contract -
// it starts a server and connects to the websocket over the network
class WebsocketServerTest : WebsocketContract() {
    override fun client() =
        WebsocketClient.blocking(Uri.of("ws://localhost:${server.port()}/bob"))

    private val server = testApp.asServer(Undertow(54632))

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



