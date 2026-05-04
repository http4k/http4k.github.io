# AWS: Firehose


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-firehose")
    implementation("org.http4k:http4k-connect-amazon-firehose-fake")
}
```


The Firehose connector provides the following Actions:
     *  CreateDeliveryStream
     *  DeleteDeliveryStream
     *  ListDeliveryStreams
     *  PutRecord
     *  PutRecordBatch

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a performance factor.

### Default Fake port: 30879

To start:




```kotlin
package content.ecosystem.connect.reference.amazon.firehose

import org.http4k.chaos.start
import org.http4k.connect.amazon.firehose.FakeFirehose

val firehose = FakeFirehose().start()

```



