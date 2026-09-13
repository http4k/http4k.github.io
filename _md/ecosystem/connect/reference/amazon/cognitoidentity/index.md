# AWS: Cognito Identity


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.59.0.0"))

    implementation("org.http4k:http4k-connect-amazon-cognitoidentity")
    implementation("org.http4k:http4k-connect-amazon-cognitoidentity-fake")
}
```


The Cognito Identity connector covers the Identity Pool APIs which exchange a set of logins for temporary AWS
credentials. It provides the following Actions:

     *  GetCredentialsForIdentity
     *  GetId

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

The returned `TemporaryCredentials` convert to http4k `AwsCredentials` with `asHttp4k()`, so they can be passed
straight to another http4k Connect client as its `CredentialsProvider`.

Note that this is the Identity Pool API - User Pools are covered by [Cognito](/ecosystem/connect/reference/amazon/cognito/).

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.cognitoidentity

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.map
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.cognitoidentity.CognitoIdentity
import org.http4k.connect.amazon.cognitoidentity.FakeCognitoIdentity
import org.http4k.connect.amazon.cognitoidentity.Http
import org.http4k.connect.amazon.cognitoidentity.action.Identity
import org.http4k.connect.amazon.cognitoidentity.getCredentialsForIdentity
import org.http4k.connect.amazon.cognitoidentity.getId
import org.http4k.connect.amazon.cognitoidentity.model.IdentityPoolId
import org.http4k.connect.amazon.core.model.Region
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    val region = Region.of("us-east-1")

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeCognitoIdentity()

    // create a client
    val client = CognitoIdentity.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val identity: Result<Identity, RemoteFailure> = client.getId(
        IdentityPoolId.of("us-east-1:12345678-1234-1234-1234-123456789012")
    )

    // exchange the identity for temporary AWS credentials
    identity.map { client.getCredentialsForIdentity(it.IdentityId) }
}

```



## # Fake

The Fake is backed by a `Storage<StoredIdentity>` and issues one identity per pool and set of logins, as the real
service does - so repeating a `GetId` with the same logins returns the same `IdentityId`. `GetCredentialsForIdentity`
returns fixed credentials with a configurable expiry, and rejects an unknown identity with a
`ResourceNotFoundException`.

### Default Fake port: 15167

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.cognitoidentity

import org.http4k.chaos.start
import org.http4k.connect.amazon.cognitoidentity.FakeCognitoIdentity

val cognitoIdentity = FakeCognitoIdentity().start()

```



