# API: CloudEvents


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-api-cloudevents")
}
```

The [Cloud Events](https://cloudevents.io/) spec defines a common format for Events produced by Cloud services.

http4k provides simple pluggability into the CloudEvents Java SDKs and custom event format libraries via the Lens system - making it trivial to both receive or send CloudEvents in the standard way.

### Example 

In this example we are using the Jackson JSONFormat which is included by default with the `http4k-api-cloudevents` module. If you want to also use the lenses to access typed EventData, you will also need this in your Gradle file:

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    // to access the lenses in the Jackson module
    implementation("org.http4k:http4k-format-jackson")
}
```

#### Code





```kotlin
package content.ecosystem.http4k.reference.cloud_events

import io.cloudevents.core.builder.CloudEventBuilder
import io.cloudevents.core.builder.withSource
import io.cloudevents.core.provider.EventFormatProvider
import io.cloudevents.http4k.cloudEventsFormat
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.filter.debug
import org.http4k.format.Jackson
import org.http4k.format.Jackson.withData
import org.http4k.format.cloudEventDataLens
import org.http4k.lens.cloudEvent
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.time.OffsetDateTime
import java.util.UUID

fun main() {
    // Events formats must be registered into a singleton provided by the CloudEvents SDK.
    // Here we are using the format with any http4k Jackson mappings.
    EventFormatProvider.getInstance().registerFormat(Jackson.cloudEventsFormat())

    // We use one lens to get the event envelope and another to get the typed data from the Event
    val eventLens = Body.cloudEvent().toLens()
    val dataLens = Jackson.cloudEventDataLens<MyCloudEventData>()

    val app = CatchLensFailure()
        .then(routes(
            "/foo/bar" bind POST to {
                // Our app uses lenses in the normal way to extract the event from the request..
                val cloudEvent = eventLens(it)

                // ... and then the typed event data from the event envelope
                val eventData = dataLens(cloudEvent)

                println("Event: $cloudEvent")
                println("Event Data: $eventData")

                Response(OK)
            }
        )).debug()

    // Create the base CloudEvent without the data...
    // then inject the data into the Event - this sets the content type of the event
    val cloudEvent = CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(Uri.of("localhost"))
        .withTime(OffsetDateTime.now())
        .withType("myEventType")
        // this is a custom extension function from Jackson (needs to be imported)
        .withData(MyCloudEventData(10, Uri.of("foobar")))
        .build()

    // ...lastly inject the event into the request and send it to the server
    app(Request(POST, "/foo/bar").with(eventLens of cloudEvent))
}

// define a custom event which will be sent/received in the "data" field of the CloudEvent
data class MyCloudEventData(val value: Int, val uri: Uri)

```



