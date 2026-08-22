# AWS: IoT Data Plane


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.58.0.0"))

    implementation("org.http4k:http4k-connect-amazon-iotdataplane")
    implementation("org.http4k:http4k-connect-amazon-iotdataplane-fake")
}
```


The IoT Data Plane connector covers the messaging and Thing Shadow APIs - what devices and applications use to talk
to each other through IoT Core. It provides the following Actions:

     *  DeleteConnection
     *  DeleteThingShadow
     *  GetRetainedMessage
     *  GetThingShadow
     *  ListNamedShadowsForThing
     *  ListRetainedMessages
     *  Publish
     *  UpdateThingShadow

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

Unlike most AWS services, the IoT data endpoint is account-specific (eg. `https://xxxxxxxx-ats.iot.<region>.amazonaws.com`),
so it cannot be derived from the Region and is passed to the client explicitly.

Shadow documents are opaque JSON as far as the API is concerned, so they are sent as raw bytes and returned as an
`InputStream` for the caller to parse with the JSON library of their choosing.

`Publish` exposes the full set of MQTT5 options - `qos`, `retain`, `contentType`, `payloadFormatIndicator`,
`messageExpiry`, `responseTopic`, `correlationData` and `userProperties`.

The Fake records every published message for test assertions, stores Thing Shadows, and serves the retained
messages that its retained publishes create.

The rest of IoT Core lives in sibling modules: the cloud-side control plane for Jobs and Streams in
[IoT Core](/ecosystem/connect/reference/amazon/iot/), and the device side of Jobs in [IoT Jobs Data Plane](/ecosystem/connect/reference/amazon/iotjobsdataplane/).

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.iotdataplane

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.iotdataplane.FakeIotDataPlane
import org.http4k.connect.amazon.iotdataplane.Http
import org.http4k.connect.amazon.iotdataplane.IotDataPlane
import org.http4k.connect.amazon.iotdataplane.getThingShadow
import org.http4k.connect.amazon.iotdataplane.model.PayloadFormatIndicator.UTF8_DATA
import org.http4k.connect.amazon.iotdataplane.model.ThingName
import org.http4k.connect.amazon.iotdataplane.model.TopicName
import org.http4k.connect.amazon.iotdataplane.publish
import org.http4k.connect.amazon.iotdataplane.updateThingShadow
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.filter.debug
import java.io.InputStream

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")
    val topic = TopicName.of("http4k/example/topic")
    val thing = ThingName.of("http4k-example-thing")

    // unlike other AWS services, the IoT data endpoint is account-specific
    val endpoint = Uri.of("https://000000000-ats.iot.us-east-1.amazonaws.com")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeIotDataPlane()

    // create a client
    val client = IotDataPlane.Http(endpoint, region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val published: Result<Unit, RemoteFailure> = client.publish(topic, """{"message":"hello"}""".toByteArray())

    // ... and the MQTT5 options are all available
    client.publish(
        topic = topic,
        payload = """{"message":"hello again"}""".toByteArray(),
        qos = 1,
        contentType = "application/json",
        payloadFormatIndicator = UTF8_DATA,
        userProperties = listOf("source" to "http4k")
    )

    // shadow documents are opaque JSON, so are sent as bytes and returned as a stream
    client.updateThingShadow(thing, """{"state":{"reported":{"on":true}}}""".toByteArray())

    val shadow: Result<InputStream, RemoteFailure> = client.getThingShadow(thing)
}

```



### Default Fake port: 45592

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.iotdataplane

import org.http4k.chaos.start
import org.http4k.connect.amazon.iotdataplane.FakeIotDataPlane

val iotDataPlane = FakeIotDataPlane().start()

```



