# AWS: AppRunner


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-apprunner")
    implementation("org.http4k:http4k-connect-amazon-apprunner-fake")
}
```

The AppRunner connector provides the following Actions:

     *  CreateService
     *  DeleteService
     *  ListServices

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.apprunner

import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.amazon.apprunner.AppRunner
import org.http4k.connect.amazon.apprunner.FakeAppRunner
import org.http4k.connect.amazon.apprunner.Http
import org.http4k.connect.amazon.apprunner.listServices
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.lambda.model.FunctionName
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    val fakeAppRunner = FakeAppRunner()

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else fakeAppRunner

    // create a client
    val client = AppRunner.Http(Region.of("us-east-1"), { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    println(client.listServices())
}

```



The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Default Fake port: 62628

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.apprunner

import org.http4k.chaos.start
import org.http4k.connect.amazon.apprunner.FakeAppRunner

val appRunner = FakeAppRunner().start()

```



