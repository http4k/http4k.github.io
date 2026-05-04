# AWS: Secrets Manager


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-secretsmanager")
    implementation("org.http4k:http4k-connect-amazon-secretsmanager-fake")
}
```


The Secrets Manager connector provides the following Actions:

     *  CreateSecret
     *  DeleteSecret
     *  GetSecretValue
     *  ListSecrets
     *  PutSecretValue
     *  UpdateSecret

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.secretsmanager

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.valueOrNull
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.secretsmanager.FakeSecretsManager
import org.http4k.connect.amazon.secretsmanager.Http
import org.http4k.connect.amazon.secretsmanager.SecretsManager
import org.http4k.connect.amazon.secretsmanager.action.CreatedSecret
import org.http4k.connect.amazon.secretsmanager.createSecret
import org.http4k.connect.amazon.secretsmanager.getSecretValue
import org.http4k.connect.amazon.secretsmanager.model.SecretId
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import java.util.UUID

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeSecretsManager()

    // create a client
    val client =
        SecretsManager.Http(Region.of("us-east-1"), { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    val secretId = SecretId.of("a-secret-id")

    // all operations return a Result monad of the API type
    val createdSecretResult: Result<CreatedSecret, RemoteFailure> =
        client.createSecret(secretId.value, UUID.randomUUID(), "value")
    println(createdSecretResult.valueOrNull())

    // get the secret value back
    println(client.getSecretValue(secretId).valueOrNull())
}

```



The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Default Fake port: 58194

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.secretsmanager

import org.http4k.chaos.start
import org.http4k.connect.amazon.secretsmanager.FakeSecretsManager

val secretsManager = FakeSecretsManager().start()

```



