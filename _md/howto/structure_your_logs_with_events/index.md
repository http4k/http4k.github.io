# Structure your logs with Events


### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-jackson")
}
```

In order to leverage modern log aggregation platforms, we should move away from logging arbitrary strings into the StdOut of our applications, and move towards [Structured Logging](https://www.thoughtworks.com/radar/techniques/structured-logging) instead, which allows us to treat logs as data which can be mined to give us better observability of our systems. This also encourages the move for developers to think about which events happening in your apps are actually important and what data is appropriate to be attached to each one.

**http4k** supports Structured Logging using a simple yet powerful concept - an `Event` is simply a marker interface that can be attached to any class, which we then send to an instance of `Events` (a "sink" for sending `Event` instances to). As with the `HttpHandler`, `Events` is just a typealias of `(Event) -> Unit`, and similarly to the `HttpHandler`, an Event can be transformed or decorated with metadata using an `EventFilter` (modelled as `(Events) -> Events`)).

Support for leveraging auto "object to JSON" transformational capabilities is included for the libraries that have it (eg. Jackson and GSON). This allows custom `Json` instances to be used (for instance) to avoid PII information being spat out to log aggregation platforms where they could be masked using the configuration of the JSON renderer.

Attaching metadata to an `Event` results in (compactified) JSON similar to this:
```json
{
  "event": {
    "uri": "/path1",
    "status": 200,
    "duration": 16
  },
  "metadata": {
    "timestamp": "2019-11-05T17:32:27.297448Z", 
    "name":"IncomingHttpRequest",
    "traces": {
      "traceId": "e35304c95b704c7d",
      "spanId": "0e46f7b3cb5bcf2e",
      "parentSpanId": null,
      "samplingDecision": "1"
    },
    "requestCount": 1234
  }
}
```

In harmony with the [ethos](/overview/) of **http4k** there is no need to bring in a custom logging library such as SL4J, although they would be very simple to integrate if required by implementing a custom `Events` instance.

The example below shows a simple application that outputs structured logs to StdOut which can be analysed by an aggregator, along with the attachment of extra `Event` metadata via a custom `EventFilter`.
 
### Code





```kotlin
package content.howto.structure_your_logs_with_events

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.Event
import org.http4k.events.EventFilter
import org.http4k.events.EventFilters
import org.http4k.events.plus
import org.http4k.events.then
import org.http4k.filter.ResponseFilters
import org.http4k.format.Jackson

fun main() {
    // Stack filters for Events in the same way as HttpHandlers to
    // transform or add metadata to the Events.
    // We use AutoMarshallingEvents (here with Jackson) to
    // handle the final serialisation process.
    val events =
        EventFilters.AddTimestamp()
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddZipkinTraces())
            .then(AddRequestCount())
            .then(AutoMarshallingEvents(Jackson))

    val app: HttpHandler = { _: Request -> Response(OK).body("hello") }

    val appWithEvents =
        ResponseFilters.ReportHttpTransaction {
            // to "emit" an event, just invoke() the Events!
            events(
                IncomingHttpRequest(
                    uri = it.request.uri,
                    status = it.response.status.code,
                    duration = it.duration.toMillis()
                )
            )
        }.then(app)

    appWithEvents(Request(GET, "/path1"))
    appWithEvents(Request(GET, "/path2"))
}

// this is our custom event which will be printed in a structured way
data class IncomingHttpRequest(val uri: Uri, val status: Int, val duration: Long) : Event

// here is a new EventFilter that adds custom metadata to the emitted events
fun AddRequestCount(): EventFilter {
    var requestCount = 0
    return EventFilter { next ->
        {
            next(it + ("requestCount" to requestCount++))
        }
    }
}

```



