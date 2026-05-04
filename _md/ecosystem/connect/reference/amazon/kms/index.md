# AWS: KMS


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-kms")
    implementation("org.http4k:http4k-connect-amazon-kms-fake")
}
```


The KMS connector provides the following Actions:

     *  CreateKey
     *  DescribeKey
     *  Decrypt
     *  Encrypt
     *  GetPublicKey
     *  ListKeys
     *  ScheduleKeyDeletion
     *  Sign
     *  Verify

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.kms

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.valueOrNull
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.kms.FakeKMS
import org.http4k.connect.amazon.kms.Http
import org.http4k.connect.amazon.kms.KMS
import org.http4k.connect.amazon.kms.action.Decrypted
import org.http4k.connect.amazon.kms.action.Encrypted
import org.http4k.connect.amazon.kms.action.KeyCreated
import org.http4k.connect.amazon.kms.createKey
import org.http4k.connect.amazon.kms.decrypt
import org.http4k.connect.amazon.kms.encrypt
import org.http4k.connect.amazon.kms.model.CustomerMasterKeySpec.ECC_NIST_P384
import org.http4k.connect.amazon.kms.model.KeyUsage.ENCRYPT_DECRYPT
import org.http4k.connect.model.Base64Blob
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeKMS()

    // create a client
    val client = KMS.Http(Region.of("us-east-1"), { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val createdKeyResult: Result<KeyCreated, RemoteFailure> = client.createKey(ECC_NIST_P384, ENCRYPT_DECRYPT)
    val key: KeyCreated = createdKeyResult.valueOrNull()!!

    // we can encrypt some text...
    val encrypted: Encrypted = client.encrypt(key.KeyMetadata.KeyId, Base64Blob.encode("hello"))
        .valueOrNull()!!
    println(encrypted.CiphertextBlob.decoded())

    // and decrypt it again!
    val decrypted: Decrypted = client.decrypt(key.KeyMetadata.KeyId, encrypted.CiphertextBlob).valueOrNull()!!
    println(decrypted.Plaintext.decoded())
}

```



The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

The FakeKMS implementation currently does not properly encrypt/decrypt or sign/verify the contents of messages - it uses
a trivially simple (and fast) reversible algorithm which simulates this functionality.

### Default Fake port: 45302

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.kms

import org.http4k.chaos.start
import org.http4k.connect.amazon.kms.FakeKMS

val kms = FakeKMS().start()

```



