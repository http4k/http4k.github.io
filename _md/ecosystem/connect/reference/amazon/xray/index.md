# AWS: X-Ray


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.59.0.0"))

    implementation("org.http4k:http4k-connect-amazon-xray")
    implementation("org.http4k:http4k-connect-amazon-xray-fake")
}
```


The X-Ray connector covers the read APIs used to query traces which have already been recorded. It provides the
following Actions:

     *  BatchGetTraces
     *  GetTraceSummaries

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

Segment documents are returned as the raw JSON that X-Ray stores, so callers parse whichever fields they care about.
The root-cause structures of a trace summary are not modelled. As at the real service, `BatchGetTraces` accepts at most
5 trace ids per call.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.xray

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.map
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.xray.FakeXRay
import org.http4k.connect.amazon.xray.Http
import org.http4k.connect.amazon.xray.XRay
import org.http4k.connect.amazon.xray.action.TraceSummaries
import org.http4k.connect.amazon.xray.batchGetTraces
import org.http4k.connect.amazon.xray.getTraceSummaries
import org.http4k.connect.model.Timestamp
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import java.time.Instant

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeXRay()

    // create a client
    val client = XRay.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    val now = Instant.now()

    // all operations return a Result monad of the API type
    val summaries: Result<TraceSummaries, RemoteFailure> = client.getTraceSummaries(
        StartTime = Timestamp.of(now.minusSeconds(300)),
        EndTime = Timestamp.of(now),
        FilterExpression = """annotation.order_id = "my-order""""
    )

    // at most 5 trace ids can be fetched at a time
    summaries.map { client.batchGetTraces(it.TraceSummaries.take(5).map { summary -> summary.Id }) }
}

```



## # Fake

The Fake is backed by a `Storage<StoredTrace>`, so traces can be seeded directly into the store for tests to query.
It evaluates `annotation.<key> = "<value>"` filter expressions and refuses any other expression, rather than
silently answering with every trace in the window.

### Default Fake port: 51591

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.xray

import org.http4k.chaos.start
import org.http4k.connect.amazon.xray.FakeXRay

val xray = FakeXRay().start()

```



