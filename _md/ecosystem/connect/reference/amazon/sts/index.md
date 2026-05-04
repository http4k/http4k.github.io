# AWS: Security Token Service


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-sts")
    implementation("org.http4k:http4k-connect-amazon-sts-fake")
}
```


The STS connector provides the following Actions:

     *  AssumeRole
     *  AssumeRoleWithWebIdentity

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.sts

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.ARN
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.core.model.RoleSessionName
import org.http4k.connect.amazon.sts.FakeSTS
import org.http4k.connect.amazon.sts.Http
import org.http4k.connect.amazon.sts.STS
import org.http4k.connect.amazon.sts.action.AssumedRole
import org.http4k.connect.amazon.sts.assumeRole
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")
    val roleArn = ARN.of("arn:aws:sts:us-east-1:000000000001:role:myrole")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeSTS()

    // create a client
    val client = STS.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val assumeRoleResult: Result<AssumedRole, RemoteFailure> = client.assumeRole(roleArn, RoleSessionName.of("sessionId"))
    println(assumeRoleResult)
}

```



### Default Fake port: 20434

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.sts

import org.http4k.chaos.start
import org.http4k.connect.amazon.sts.FakeSTS

val sts = FakeSTS().start()

```



