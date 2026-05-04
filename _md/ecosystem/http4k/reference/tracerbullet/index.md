# Testing: TracerBullet



### Installation (Gradle)

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-testing-tracerbullet")
}
```

The http4k TracerBullet module brings together a lot of http4k innovations to help you to self-document your system and
let's you answer questions about your system with your tests apart from the obvious one - "Does it work?".

The essential idea is that the application uses the `Events`  interface to send interesting information to a collector
during the running of tests through a JUnit extension. At the end of the test run, the `TracerBullet` collates these
Events using a distributed tracing mechanism (X-B3 headers) and a set of `Tracers` to create a tree of what exactly
happened during the test - these events can be anything, but good examples are:

1. HTTP traffic in and out of the application
2. Database calls
3. Business events

Once we have the "Trace tree", we can use it to extract information about our applications and then use `Renderers` to
use it to document HOW our system behaves as well as if it works. Examples of these renders are:

1. PUML/Mermaid Sequence or Interaction diagrams
2. Analysis of the number of outbound calls that a particular application is making.
3. Eliminating duplicate database calls or inefficient logic.
4. When testing multiple apps together using the Server-as-a-Function/Hexagonal techniques, we can also see the entire
   timeline of every call, so we can also determine maximum hop distance.

And here is an example of a multi-service test and the type of visual documentation that is created:

<img class="imageMid" src="./trace_diagram_success.png" alt="trace diagram success">

Additionally, in the case of the test failing, the extension still auto-generates the trace diagram for the traffic 
which did occur - this provides excellent feedback as to where the system went wrong:

<img class="imageMid" src="./trace_diagram_failure.png" alt="trace diagram failure">

One of the best things about the TracerBullet plugin is that it fits in seamlessly with the rest of the http4k stack.
Once your applications have been designed to send data to the Events stream, just install the plugin and the diagramming
comes for free!

#### Code

Here's an example of making this work - note the use of the filters to use RequestTracing on the Events and the servers
and clients. This is required to make the TracerBullet work.





```kotlin
package content.ecosystem.http4k.reference.tracerbullet

import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.events.EventFilters.AddServiceName
import org.http4k.events.EventFilters.AddZipkinTraces
import org.http4k.events.Events
import org.http4k.events.HttpEvent.Incoming
import org.http4k.events.HttpEvent.Outgoing
import org.http4k.events.and
import org.http4k.events.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ClientFilters.ResetRequestTracing
import org.http4k.filter.ResponseFilters.ReportHttpTransaction
import org.http4k.filter.ServerFilters.RequestTracing
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.bind
import org.http4k.routing.reverseProxy
import org.http4k.routing.routes
import org.http4k.tracing.Actor
import org.http4k.tracing.ActorResolver
import org.http4k.tracing.ActorType
import org.http4k.tracing.TraceRenderPersistence
import org.http4k.tracing.junit.TracerBulletEvents
import org.http4k.tracing.persistence.FileSystem
import org.http4k.tracing.renderer.PumlSequenceDiagram
import org.http4k.tracing.tracer.HttpTracer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

// standardised events stack which records the service name and adds tracing
fun TraceEvents(actorName: String) = AddZipkinTraces().then(AddServiceName(actorName))

// standardised client filter stack which adds tracing and records traffic events
fun ClientStack(events: Events) = ClientFilters.RequestTracing()
    .then(ReportHttpTransaction { events(Outgoing(it)) })

// standardised server filter stack which adds tracing and records traffic events
fun ServerStack(events: Events) =
    RequestTracing().then(ReportHttpTransaction { events(Incoming(it)) })

// Our "User" object who will send a request to our system
class User(rawEvents: Events, rawHttp: HttpHandler) {
    private val events = TraceEvents("user").then(rawEvents)

    // as the user is the initiator of requests, we need to reset the tracing for each call.
    private val http = ResetRequestTracing().then(ClientStack(events)).then(rawHttp)

    fun initiateCall() = http(Request(GET, "http://internal1/int1"))
}

// the first internal app
fun Internal1(rawEvents: Events, rawHttp: HttpHandler): HttpHandler {
    val events = TraceEvents("internal1").then(rawEvents).and(rawEvents)
    val http = ClientStack(events).then(rawHttp)

    return ServerStack(events)
        .then(
            routes("/int1" bind { _: Request ->
                val first = http(Request(GET, "http://external1/ext1"))
                when {
                    first.status.successful -> http(Request(GET, "http://internal2/int2"))
                    else -> first
                }
            })
        )
}

// the second internal app
fun Internal2(rawEvents: Events, rawHttp: HttpHandler): HttpHandler {
    val events = TraceEvents("internal2").then(rawEvents)
    val http = ClientStack(events).then(rawHttp)

    return ServerStack(events)
        .then(
            routes("/int2" bind { _: Request ->
                http(Request(GET, "http://external2/ext2"))
            })
        )
}

// an external fake system
fun FakeExternal1(): HttpHandler = { Response(OK) }

// another external fake system
fun FakeExternal2(): HttpHandler = { Response(OK) }

private val actor = ActorResolver {
    Actor(it.metadata["service"].toString(), ActorType.System)
}

/**
 * Our test will capture the traffic and render it to the console
 */
class RenderingTest {
    @RegisterExtension
    // this events implementation will automatically capture the HTTP traffic
    val events = TracerBulletEvents(
        listOf(HttpTracer(actor)), // A tracer to capture HTTP calls
        listOf(PumlSequenceDiagram), // Render the HTTP traffic as a PUML diagram
        TraceRenderPersistence.FileSystem(File(".")) // Store the result
    )

    @Test
    fun `render successful trace`() {
        // compose our application(s) together
        val internalApp = Internal1(
            events,
            reverseProxy(
                "external1" to FakeExternal1(),
                "internal2" to Internal2(events, FakeExternal2())
            )
        )

        // make a request to the composed stack
        assertThat(User(events, internalApp).initiateCall(), hasStatus(OK))
    }

    @Test
    @Disabled("Remove this to run the failing test!")
    fun `render failure trace`() {
        // compose our application(s) together
        val internalApp = Internal1(
            events,
            reverseProxy(
                "external1" to { _: Request -> Response(INTERNAL_SERVER_ERROR) },
                "internal2" to Internal2(events, FakeExternal2())
            )
        )

        // make a request to the composed stack
        assertThat(User(events, internalApp).initiateCall(), hasStatus(OK))
    }
}

```



An extended example of this technique can be found at the [this repository](https://github.com/http4k/exploring-the-testing-hyperpyramid).

[http4k]: https://http4k.org

