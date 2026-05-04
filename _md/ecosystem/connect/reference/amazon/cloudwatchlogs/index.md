# AWS: CloudWatchLogs


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-cloudwatchlogs")
    implementation("org.http4k:http4k-connect-amazon-cloudwatchlogs-fake")
}
```


The CloudWatchLogs connector provides the following Actions:

* CreateLogGroup
* CreateLogStream
* DeleteLogGroup
* DeleteLogStream
* FilterLogEvents
* PutLogEvents

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.cloudwatchlogs

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.cloudwatchlogs.CloudWatchLogs
import org.http4k.connect.amazon.cloudwatchlogs.FakeCloudWatchLogs
import org.http4k.connect.amazon.cloudwatchlogs.Http
import org.http4k.connect.amazon.cloudwatchlogs.action.PutLogEventsResponse
import org.http4k.connect.amazon.cloudwatchlogs.model.LogGroupName
import org.http4k.connect.amazon.cloudwatchlogs.model.LogStreamName
import org.http4k.connect.amazon.cloudwatchlogs.putLogEvents
import org.http4k.connect.amazon.core.model.Region
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeCloudWatchLogs()

// create a client
val cloudWatchLogs =
    CloudWatchLogs.Http(Region.US_EAST_1, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

val result: Result<PutLogEventsResponse, RemoteFailure> = cloudWatchLogs.putLogEvents(
    LogGroupName.of("foobar"),
    LogStreamName.of("stream"),
    emptyList()
)

fun main() {
    println(result)
}

```



### Default Fake port: 56514

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.cloudwatchlogs

import org.http4k.chaos.start
import org.http4k.connect.amazon.cloudwatchlogs.FakeCloudWatchLogs

val fakeCloudWatchLogs = FakeCloudWatchLogs().start()

```



