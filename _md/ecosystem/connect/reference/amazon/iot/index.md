# AWS: IoT Core


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.58.0.0"))

    implementation("org.http4k:http4k-connect-amazon-iot")
    implementation("org.http4k:http4k-connect-amazon-iot-fake")
}
```


The IoT Core connector covers the control plane - the cloud-side API for managing Jobs and Streams. It provides
the following Actions:

     *  CancelJob
     *  CreateJob
     *  CreateStream
     *  DeleteJob
     *  DeleteStream
     *  DescribeEndpoint
     *  DescribeJob
     *  DescribeJobExecution
     *  DescribeStream
     *  ListJobExecutionsForThing
     *  ListStreams
     *  UpdateStream

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

Jobs carry an inline JSON `document` - `documentSource` (an S3 link instead of an inline document) is not supported,
and neither are the rollout, retry, abort and scheduling configs. Streams are the mechanism devices use to pull files
over MQTT.

This module is the cloud half of a pair: the device half lives in
[IoT Jobs Data Plane](/ecosystem/connect/reference/amazon/iotjobsdataplane/), and the messaging and Thing Shadow APIs are in
[IoT Data Plane](/ecosystem/connect/reference/amazon/iotdataplane/). `FakeIot` takes a `Storage<StoredJob>`, so passing the same store to
`FakeIotJobsDataPlane` gives both sides of a Job a single state to work against.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.iot

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.ARN
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.iot.FakeIot
import org.http4k.connect.amazon.iot.Http
import org.http4k.connect.amazon.iot.Iot
import org.http4k.connect.amazon.iot.action.CreatedJob
import org.http4k.connect.amazon.iot.action.DescribedJob
import org.http4k.connect.amazon.iot.createJob
import org.http4k.connect.amazon.iot.describeJob
import org.http4k.connect.amazon.iot.model.JobId
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeIot()

    // create a client
    val client = Iot.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    val jobId = JobId.of("firmware-update-1")

    // all operations return a Result monad of the API type
    val created: Result<CreatedJob, RemoteFailure> = client.createJob(
        jobId = jobId,
        targets = listOf(ARN.of("arn:aws:iot:us-east-1:000000000000:thing/my-thing")),
        document = """{"operation":"firmware-update","url":"https://example.com/firmware.bin"}""",
        description = "Firmware update to 1.2.3"
    )

    val described: Result<DescribedJob, RemoteFailure> = client.describeJob(jobId)
}

```



### Default Fake port: 50270

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.iot

import org.http4k.chaos.start
import org.http4k.connect.amazon.iot.FakeIot

val iot = FakeIot().start()

```



