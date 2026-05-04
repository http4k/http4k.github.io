# Ops: OpenTelemetry



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-ops-opentelemetry")
}
```

### About

This module provides configurable Filters to provide distributed tracing and metrics for http4k apps, plugging into the awesome [OpenTelemetry](https://opentelemetry.io/) APIs.

`OpenTelemetry is a collection of tools, APIs, and SDKs. You use it to instrument, generate, collect, and export telemetry data (metrics, logs, and traces) for analysis in order to understand your software's performance and behavior.`

### Tracing 

OpenTelemetry provides a pluggable interface for tracing propagation, so you can easily switch between different implementations such as AWS X-Ray, B3 and Jaeger etc.





```kotlin
package content.ecosystem.http4k.reference.opentelemetry

import io.opentelemetry.context.propagation.ContextPropagators.create
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun main() {
    // configure OpenTelemetry using the Amazon XRAY tracing scheme
    val openTelemetry = OpenTelemetrySdk.builder()
        .setPropagators(create(AwsXrayPropagator.getInstance()))
        .buildAndRegisterGlobal()

    // this HttpHandler represents a 3rd party service, and will repeat the request body
    val repeater: HttpHandler = {
        println("REMOTE REQUEST WITH TRACING HEADERS: $it")
        Response(OK).body(it.bodyString() + it.bodyString())
    }

    // we will propagate the tracing headers using the tracer instance
    val repeaterClient = ClientFilters.OpenTelemetryTracing(openTelemetry).then(repeater)

    // this is the server app which will add tracing spans to incoming requests
    val app = ServerFilters.OpenTelemetryTracing(openTelemetry)
        .then(routes("/echo/{name}" bind GET to {
            val remoteResponse = repeaterClient(
                Request(POST, "http://aRemoteServer/endpoint")
                    .body(it.path("name")!!)
            )
            Response(OK).body(remoteResponse.bodyString())
        }))

    println("RETURNED TO CALLER: " + app(Request(GET, "http://localhost:8080/echo/david")))
}

```



### Metrics 

Both Server and Client filters are available for recording request counts and latency, optionally overriding values for the metric names, descriptions and request identification.





```kotlin
package content.ecosystem.http4k.reference.opentelemetry

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.http4k.client.ApacheClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.OpenTelemetryMetrics
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes

fun main() {
    // test only: this sets up the metrics provider to something we can read
    val inMemoryMetricReader = InMemoryMetricReader.create()

    OpenTelemetrySdk.builder()
        .setMeterProvider(
            SdkMeterProvider.builder()
                .registerMetricReader(inMemoryMetricReader)
                .build()
        )
        .buildAndRegisterGlobal()

    val server = routes("/metrics" bind GET to { Response(OK) })

    // apply metrics filters to a server...
    val app = ServerFilters.OpenTelemetryMetrics.RequestCounter()
        .then(ServerFilters.OpenTelemetryMetrics.RequestTimer())
        .then(server)

    // ... or to a client
    val client =
        ClientFilters.OpenTelemetryMetrics.RequestCounter()
            .then(ClientFilters.OpenTelemetryMetrics.RequestTimer())
            .then(ApacheClient())

    // make some calls
    repeat(5) {
        app(Request(GET, "/metrics"))
        client(Request(GET, "https://http4k.org"))
    }

    // see some results
    inMemoryMetricReader.collectAllMetrics().forEach {
        println("metric: " + it.name + ", value: " +
            (it.longSumData.points.takeIf { it.isNotEmpty() } ?: it.doubleSumData.points)
        )
    }
}

```



