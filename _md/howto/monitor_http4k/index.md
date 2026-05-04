# Monitor http4k


Measuring performance of application estate is crucial in today's microservice world - it is crucial that dev-ops enabled teams can monitor, react and scale dynamically to changes in the runtime environment. However, because of the plethora of monitoring tools on the market, and because [**http4k**](https://github.com/http4k/http4k) is a toolkit and not a complete "batteries included" framework, it provides a number of integration points to enable monitoring systems to be plugged in as required. Additionally, it is envisaged that users will probably want to provide their own implementations of the [**http4k**](https://github.com/http4k/http4k) `ServerConfig` classes (`Jetty`, `Undertow` etc..) so that tweaking and tuning to their exact requirements is accessible, instead of [**http4k**](https://github.com/http4k/http4k) attempting to provide some generic configuration API to achieve it.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-ops-micrometer")
}
```
 
### Metrics (Micrometer) 

[**http4k**](https://github.com/http4k/http4k) provides module support for monitoring application endpoints using the [**micrometer**](http://micrometer.io/) metrics abstraction library, which currently enables support for libraries such as Graphite, StatsD, Prometheus and Netflix Atlas. This also provides drop-in classes to record stats such as JVM performance, GC and thread usage.





```kotlin
package content.howto.monitor_http4k

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.http4k.client.ApacheClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes

fun main() {

    // this is a micrometer registry used mostly for testing - substitute the correct implementation.
    val registry = SimpleMeterRegistry()

    val server = routes("/metrics/{name}" bind GET to { Response(OK) })

    // apply filters to a server...
    val app = ServerFilters.MicrometerMetrics.RequestCounter(registry)
        .then(ServerFilters.MicrometerMetrics.RequestTimer(registry))
        .then(server)

    // ... or to a client
    val client = ClientFilters.MicrometerMetrics.RequestCounter(registry)
        .then(ClientFilters.MicrometerMetrics.RequestTimer(registry))
        .then(ApacheClient())

    // make some calls
    (0..10).forEach {
        app(Request(GET, "/metrics/$it"))
        client(Request(GET, "https://http4k.org"))
    }

    // see some results
    registry.forEachMeter { println("${it.id} ${it.measure().joinToString(",")}") }
}

```



### Metrics (other APIs) 

Alternatively, it's very easy to use a standard `Filter` to report on stats:





```kotlin
package content.howto.monitor_http4k

import org.http4k.core.HttpHandler
import org.http4k.core.HttpTransaction
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.HttpTransactionLabeler
import org.http4k.filter.ResponseFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.time.Duration

fun main() {

    val app = routes("foo/{name}" bind { _: Request -> Response(OK) })

    fun metricConsumer(name: String, time: Duration) = println("$name ${time.toMillis()}ms")

    // this is a general use filter for reporting on http transactions
    val standardFilter = ResponseFilters.ReportHttpTransaction { tx: HttpTransaction ->
        metricConsumer("txLabels are: ${tx.labels}", tx.duration)
        metricConsumer("uri is: ${tx.request.uri}", tx.duration)
    }

    val addCustomLabels: HttpTransactionLabeler = { tx: HttpTransaction ->
        tx.label("status", tx.response.status.code.toString())
    }

    val withCustomLabels = ResponseFilters.ReportHttpTransaction(
        transactionLabeler = addCustomLabels
    ) { tx: HttpTransaction ->
        // send metrics to some custom system here...
        println("custom txLabels are: ${tx.labels} ${tx.duration}")
    }

    // this filter provides an anonymous identifier of the route
    val identifiedRouteFilter =
        ResponseFilters.ReportRouteLatency { requestGroup: String, duration: Duration ->
            metricConsumer("requestGroup is: $requestGroup", duration)
        }

    val monitoredApp: HttpHandler = standardFilter
        .then(withCustomLabels)
        .then(identifiedRouteFilter)
        .then(app)

    monitoredApp(Request(GET, "/foo/bob"))

//    prints...
//    requestGroup is: GET.foo_{name}.2xx.200 7ms
//    custom txLabels are: {routingGroup=foo/{name}, status=200} PT0.05S
//        txLabels are: {routingGroup=foo/{name}} 51ms
//    uri is: /foo/bob 51ms
}

```



### Logging 
This is trivial to achieve by using a Filter:
 




```kotlin
package content.howto.monitor_http4k

import org.http4k.core.HttpHandler
import org.http4k.core.HttpTransaction
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ResponseFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.time.Clock

fun main() {

    val app = routes("/{name}" bind { _: Request -> Response(OK) })

    fun logger(message: String) = println("${Clock.systemUTC().instant()} $message")

    val audit = ResponseFilters.ReportHttpTransaction { tx: HttpTransaction ->
        logger("my call to ${tx.request.uri} returned ${tx.response.status} and took ${tx.duration.toMillis()}")
    }

    val monitoredApp: HttpHandler = audit.then(app)

    monitoredApp(Request(GET, "/foo"))

//    prints...
//    2017-12-04T08:38:27.499Z my call to /foo returned 200 OK and took 5
}

```



### Distributed tracing 
This allows a chain of application calls to be tied together and is generally done through the setting of HTTP headers on each call. [**http4k**](https://github.com/http4k/http4k) supports the [OpenZipkin](https://zipkin.io/) standard for achieving this and provides both Server-side and Client-side `Filters` for this purpose. This example shows a chain of two proxies and an endpoint - run it to observe the changes to the tracing headers as the request flows through the system:





```kotlin
package content.howto.monitor_http4k

import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.HttpMessage
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {

    fun HttpMessage.logHeader(name: String) = "\n\t\t$name=${header(name)}"
    fun HttpMessage.traces() = logHeader("x-b3-traceid") +
        logHeader("x-b3-spanid") +
        logHeader("x-b3-parentspanid")

    fun audit(name: String) = ResponseFilters.ReportHttpTransaction { tx ->
        println("$name: ${tx.request.uri}\n\trequest:${tx.request.traces()}\n\tresponse:${tx.response.traces()}")
    }

    // a simple proxy to another app
    fun proxy(name: String, port: Int): HttpHandler {
        val proxyClient = ClientFilters.RequestTracing()
            .then(ClientFilters.SetHostFrom(Uri.of("http://localhost:$port")))
            .then(audit("$name-client"))
            .then(ApacheClient())

        return ServerFilters.RequestTracing().then(audit("$name-server"))
            .then { proxyClient(Request(GET, it.uri)) }
    }

    // provides a simple ping
    fun ping(): HttpHandler = ServerFilters.RequestTracing().then(audit("ping-server"))
        .then { Response(OK).body("pong") }

    val proxy1 = proxy("proxy1", 8001).asServer(SunHttp(8000)).start()
    val proxy2 = proxy("proxy2", 8002).asServer(SunHttp(8001)).start()
    val server3 = ping().asServer(SunHttp(8002)).start()

    audit("client").then(ApacheClient())(Request(GET, "http://localhost:8000/ping"))

    proxy1.stop()
    proxy2.stop()
    server3.stop()
}

```



### Debugging 
Easily wrap an `HttpHandler` in a debugging filter to check out what is going on under the covers:
 




```kotlin
package content.howto.monitor_http4k

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters

fun main() {

    val app = { _: Request -> Response(OK).body("hello there you look nice today") }

    val debuggedApp = DebuggingFilters.PrintRequestAndResponse().then(app)

    debuggedApp(Request(GET, "/foobar").header("Accepted", "my-great-content/type"))
}

```



