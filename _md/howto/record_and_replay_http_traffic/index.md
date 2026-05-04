# Record and replay HTTP traffic


A set of classes to provide simple recording/replaying of HTTP traffic. This is perfect for testing purposes, or in short lived, low traffic environments where no proper caches are available.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Caching HTTP Traffic 

Using `Filters` it's possible to record traffic and then return recorded content instead of making repeated calls. Note that the provided storage 
implementations DO NOT have any facility for Cache Control or eviction, or respect any response headers around caching. Requests are indexed in a way optimised for retrieval.

#### Code





```kotlin
package content.howto.record_and_replay_http_traffic

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.TrafficFilters
import org.http4k.traffic.ReadWriteCache

fun main() {

    // set up storage to cache a set of HTTP traffic.
    // Disk and Memory implementations are provided.
    val storage = ReadWriteCache.Disk()

    // wrap any HTTP Handler in a Recording Filter and play traffic through it
    val withCachedContent =
        TrafficFilters.ServeCachedFrom(storage)
            .then(TrafficFilters.RecordTo(storage))
            .then {
                Response(OK).body("hello world")
            }
    val aRequest = Request(GET, "http://localhost:8000/")
    println(withCachedContent(aRequest))

    // repeated requests are intercepted by the cache and
    // the responses provided without hitting the original handler
    println(withCachedContent(Request(GET, "http://localhost:8000/")))
}

```



### Recording Streams of HTTP Traffic 

Using `Filters` it's possible to record a stream traffic and then replay recorded content instead. Requests are indexed in a way optimised for iteration.

#### Code





```kotlin
package content.howto.record_and_replay_http_traffic

import org.http4k.client.ApacheClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.TrafficFilters
import org.http4k.traffic.ReadWriteStream
import org.http4k.traffic.Responder

fun main() {

    // set up storage to stash a stream of HTTP traffic.
    // Disk and Memory implementations are provided.
    val storage = ReadWriteStream.Memory()

    // wrap any HTTP Handler in a Recording Filter and play traffic through it
    val recording = TrafficFilters.RecordTo(storage).then { Response(OK).body("hello world") }
    recording(Request(GET, "http://localhost:8000/"))

    // now set up a responder
    val handler = Responder.from(storage)

    // the responder will replay the responses in order
    println(handler(Request(GET, "http://localhost:8000/")))

    // we can also replay a series of requests through a real HTTP client
    val client = ApacheClient()
    storage.requests().forEach { println(client(it)) }
}

```



### Concepts

The `org.http4k.traffic` package contains the interfaces which make up the core concepts for traffic capture and replay. These interfaces are:

- A `Sink` consumes request/response pairs for storage. 
- A `Source` provides lookup of pre-stored Response based on an HTTP Request.
- `Replay` instances provide streams of HTTP messages as they were received.
- A `ReadWriteCache` combines `Sink` and `Source` to provide cache-like storage.
- A `ReadWriteStream` combines `Sink` and `Replay` to provide a stream of traffic which can be replayed.

The API has been designed to be modular so API users can provide their own implementations (store in S3 etc..).

