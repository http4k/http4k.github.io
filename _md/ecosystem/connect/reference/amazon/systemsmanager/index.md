# AWS: Systems Manager


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-systemsmanager")
    implementation("org.http4k:http4k-connect-amazon-systemsmanager-fake")
}
```


The Systems Manager connector provides the following Actions:

     *  DeleteParameter
     *  GetParameter
     *  PutParameter

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.systemsmanager

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.systemsmanager.FakeSystemsManager
import org.http4k.connect.amazon.systemsmanager.Http
import org.http4k.connect.amazon.systemsmanager.SystemsManager
import org.http4k.connect.amazon.systemsmanager.action.PutParameterResult
import org.http4k.connect.amazon.systemsmanager.getParameter
import org.http4k.connect.amazon.systemsmanager.model.ParameterType
import org.http4k.connect.amazon.systemsmanager.model.SSMParameterName
import org.http4k.connect.amazon.systemsmanager.putParameter
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    val paramName = SSMParameterName.of("name")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeSystemsManager()

    // create a client
    val client =
        SystemsManager.Http(Region.of("us-east-1"), { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val putParameterResult: Result<PutParameterResult, RemoteFailure> =
        client.putParameter(paramName, "value", ParameterType.String)
    println(putParameterResult)

    // get the parameter back again
    println(client.getParameter(paramName))
}

```



The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Default Fake port: 42551

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.systemsmanager

import org.http4k.chaos.start
import org.http4k.connect.amazon.secretsmanager.FakeSecretsManager

val secretsManager = FakeSecretsManager().start()

```



