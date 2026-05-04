# Arrange Filters into stacks


http4k Filters are just decorator functions for HttpHandlers and process requests by applying the following process:

1. Receive the Request
2. Modify it
3. Pass it to the next HttpHandler in the chain
4. Receive the Response
5. Modify it
6. Return it to the caller

We can reason that we can combine filters together to form chains, or "Stacks" of processing logic - moving from the most generic to the most specific. But the ordering
of the filters is important in order that we have the information at the point in the stack when we need it. For example - if we want to record all HTTP traffic, we much ensure that we 
do this after any exception handling has occurred (so that we can record the 5XX properly). Experience has shown that there is a general formula to be used when constructing stacks.

### Serverside

A typical stack looks like:

1. Debugging/Tracing <-- to ensure that we see all traffic
2. Reporting & metrics capture <-- to record accurately what we sent back
3. Catch unexpected exceptions <-- to ensure that all responses are handled by the application instead of the runtime
4. Catching expected exceptions <-- for instance LensFailures which are converted to 400s
5. Routing <-- if we want to route traffic based on the request (eg. `routes()`)
6. HttpHandlers <-- to process the traffic

### Clientside

The client-side is similar, but simpler:

1. Debugging/Tracing <-- to ensure that we see all traffic
2. Reporting & metrics capture <-- to record accurately what we sent back
3. Routing <-- if we want to route traffic based on the request (eg. `reverseProxy()`)
4. Http client <-- to process the traffic

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

#### Code





```kotlin
package content.howto.arrange_filters_into_stacks

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.http4k.client.OkHttp
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.NoOp
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.core.then
import org.http4k.events.Event.Companion.Error
import org.http4k.events.Events
import org.http4k.events.HttpEvent.Incoming
import org.http4k.events.HttpEvent.Outgoing
import org.http4k.filter.ClientFilters
import org.http4k.filter.ClientFilters.RequestTracing
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ResponseFilters.ReportHttpTransaction
import org.http4k.filter.ServerFilters
import org.http4k.filter.ServerFilters.CatchAll
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.routing.bind
import org.http4k.routing.routes

fun IncomingStack(debug: Boolean, events: Events, registry: MeterRegistry, http: HttpHandler): HttpHandler =
    RequestTracing()
        .then(if(debug) DebuggingFilters.PrintRequestAndResponse(System.out) else Filter.NoOp)
        .then(ReportHttpTransaction { events(Incoming(it)) })
        .then(CatchAll {
            events(Error("Uncaught", it))
            Response(SERVICE_UNAVAILABLE)
        })
        .then(CatchLensFailure())
        .then(ServerFilters.MicrometerMetrics.RequestTimer(registry))
        .then(ServerFilters.MicrometerMetrics.RequestCounter(registry))
        .then(http)

fun OutgoingHttpStack(debug: Boolean, events: Events, registry: MeterRegistry, http: HttpHandler) =
    RequestTracing()
        .then(if(debug) DebuggingFilters.PrintRequestAndResponse(System.out) else Filter.NoOp)
        .then(ReportHttpTransaction { events(Outgoing(it)) })
        .then(ClientFilters.MicrometerMetrics.RequestTimer(registry))
        .then(ClientFilters.MicrometerMetrics.RequestCounter(registry))
        .then(http)

fun App(debug: Boolean): HttpHandler {
    val events: Events = ::println
    val registry = SimpleMeterRegistry()

    val outgoing = OutgoingHttpStack(debug, events, registry, OkHttp())

    val endpoints = routes("/" bind outgoing)

    return IncomingStack(debug, events, registry, endpoints)
}

fun main() {
    val debug = true

    val app = App(debug)

    // this just proxies the request to the internet
    app(Request(GET, "https://http4k.org"))
}

```



