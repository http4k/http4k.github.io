# AWS: CloudFront


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-cloudfront")
    implementation("org.http4k:http4k-connect-amazon-cloudfront-fake")
}
```


The CloudFront connector provides the following Actions:

     *  CreateInvalidation

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.cloudfront

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.cloudfront.CloudFront
import org.http4k.connect.amazon.cloudfront.FakeCloudFront
import org.http4k.connect.amazon.cloudfront.Http
import org.http4k.connect.amazon.cloudfront.createInvalidation
import org.http4k.connect.amazon.cloudfront.model.DistributionId
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import kotlin.random.Random.Default.nextInt

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeCloudFront()

    // create a client
    val client =
        CloudFront.Http({ AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val result: Result<Unit, RemoteFailure> = client
        .createInvalidation(DistributionId.of("a-distribution-id"), "/path")
}

```



### Default Fake port: 15420

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.cloudfront

import org.http4k.chaos.start
import org.http4k.connect.amazon.cloudfront.FakeCloudFront

val cloudFront = FakeCloudFront().start()

```



