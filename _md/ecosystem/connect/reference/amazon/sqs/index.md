# AWS: Simple Queue Service


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-sqs")
    implementation("org.http4k:http4k-connect-amazon-sqs-fake")
}
```


The SQS connector provides the following Actions:

     *  CreateQueue
     *  DeleteMessage
     *  DeleteQueue
     *  GetQueueAttributes
     *  ListQueues
     *  ReceiveMessage
     *  SendMessage

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.sqs

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.valueOrNull
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.sqs.FakeSQS
import org.http4k.connect.amazon.sqs.Http
import org.http4k.connect.amazon.sqs.SQS
import org.http4k.connect.amazon.sqs.action.CreatedQueue
import org.http4k.connect.amazon.sqs.createQueue
import org.http4k.connect.amazon.sqs.model.QueueName
import org.http4k.connect.amazon.sqs.receiveMessage
import org.http4k.connect.amazon.sqs.sendMessage
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import org.http4k.core.Uri

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")
    val queueName = QueueName.of("myqueue")
    val queueUri = Uri.of("https://sqs.us-east-1.amazonaws.com/123456789012/myqueue")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeSQS()

    // create a client
    val client = SQS.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val createdQueueResult: Result<CreatedQueue, RemoteFailure> = client.createQueue(queueName, emptyMap(), emptyMap())
    println(createdQueueResult.valueOrNull()!!)

    // send a message
    println(client.sendMessage(queueUri, "hello"))

    // and receive it..
    println(client.receiveMessage(queueUri))
}

```



Note that the FakeSQS is only suitable for very simple scenarios (testing and deployment for single consumer only) and
does NOT implement real SQS semantics such as VisibilityTimeout or maximum number of retrieved messages (it delivers all
undeleted messages to each consumer). Fake SQS queues are, as such, all inherently FIFO queues.

### Default Fake port: 37391

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.sqs

import org.http4k.chaos.start
import org.http4k.connect.amazon.sqs.FakeSQS

val sqs = FakeSQS().start()

```



