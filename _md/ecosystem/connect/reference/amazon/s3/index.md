# AWS: S3


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-s3")
    implementation("org.http4k:http4k-connect-amazon-s3-fake")
}
```


The S3 connector consists of 2 interfaces:

- `S3` for global operations, providing the following Actions:

    * CreateBucket
    * HeadBucket
    * ListBuckets

- `S3Bucket` for bucket level operations, providing the following Actions:

    * CopyObject
    * CreateObject
    * DeleteBucket
    * DeleteObject
    * DeleteObjectTagging
    * GetObject
    * GetObjectTagging
    * HeadObject
    * ListObjectsV2
    * PutObject
    * PutObjectTagging
    * RestoreObject

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.s3

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.valueOrNull
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.s3.FakeS3
import org.http4k.connect.amazon.s3.Http
import org.http4k.connect.amazon.s3.S3
import org.http4k.connect.amazon.s3.S3Bucket
import org.http4k.connect.amazon.s3.createBucket
import org.http4k.connect.amazon.s3.model.BucketKey
import org.http4k.connect.amazon.s3.model.BucketName
import org.http4k.connect.amazon.s3.putObject
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import java.io.InputStream

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeS3()

    val bucketName = BucketName.of("foobar")
    val bucketKey = BucketKey.of("keyName")
    val region = Region.of("us-east-1")

    // create global and bucket level clients
    val s3 = S3.Http({ AwsCredentials("accessKeyId", "secretKey") }, http.debug())
    val s3Bucket = S3Bucket.Http(bucketName, region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val createResult: Result<Unit, RemoteFailure> = s3.createBucket(bucketName, region)
    createResult.valueOrNull()!!

    // we can store some content in the bucket...
    val putResult: Result<Unit, RemoteFailure> = s3Bucket.putObject(bucketKey, "hellothere".byteInputStream())
    putResult.valueOrNull()!!

    // and get back the content which we stored
    val getResult: Result<InputStream?, RemoteFailure> = s3Bucket.get(bucketKey)
    val content: InputStream = getResult.valueOrNull()!!
    println(content.reader().readText())
}

```



The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### How the Fake works with bucket-level operations

S3 is a bit of a strange beast in that it each bucket gets its own virtual hostname. This makes running a Fake an
interesting challenge without messing around with DNS and hostname files.

This implementation supports both global and bucket level operations by inspecting the subdomain of the X-Forwarded-For
header, which is populated by the S3 client built into this module.

In the case of a missing header (if for instance a non-http4k client attempts to push some data into it without the
x-forwarded-for header), it creates a global bucket which is then used to store all of the data for these unknown
requests.

### Default Fake ports:

- Global: default port: 26467
- Bucket: default port: 42628





```kotlin
package content.ecosystem.connect.reference.amazon.s3

import org.http4k.chaos.start
import org.http4k.connect.amazon.s3.FakeS3

val s3 = FakeS3().start()

```



### Connecting to a local S3 emulator

Services like [LocalStack](https://docs.localstack.cloud/user-guide/aws/s3/) or
[MinIO](https://min.io/docs/minio/container/index.html) can emulate AWS services locally.
However, for S3 bucket operations you either need to use a specific pre-configured bucket hostname 
like `http://<bucket-name>.s3.localhost.localstack.cloud:4566`, or you configure the `S3Bucket` to always 
perform path-style requests like this:





```kotlin
package content.ecosystem.connect.reference.amazon.s3

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.valueOrNull
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.s3.FakeS3
import org.http4k.connect.amazon.s3.Http
import org.http4k.connect.amazon.s3.S3
import org.http4k.connect.amazon.s3.S3Bucket
import org.http4k.connect.amazon.s3.createBucket
import org.http4k.connect.amazon.s3.model.BucketKey
import org.http4k.connect.amazon.s3.model.BucketName
import org.http4k.connect.amazon.s3.putObject
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import java.io.InputStream

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeS3()

    val bucketName = BucketName.of("foobar")
    val bucketKey = BucketKey.of("keyName")
    val region = Region.of("us-east-1")

    // create global and bucket level clients
    val s3 = S3.Http({ AwsCredentials("accessKeyId", "secretKey") }, http.debug())
    val s3Bucket = S3Bucket.Http(bucketName, region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val createResult: Result<Unit, RemoteFailure> = s3.createBucket(bucketName, region)
    createResult.valueOrNull()!!

    // we can store some content in the bucket...
    val putResult: Result<Unit, RemoteFailure> = s3Bucket.putObject(bucketKey, "hellothere".byteInputStream())
    putResult.valueOrNull()!!

    // and get back the content which we stored
    val getResult: Result<InputStream?, RemoteFailure> = s3Bucket.get(bucketKey)
    val content: InputStream = getResult.valueOrNull()!!
    println(content.reader().readText())
}

```


### Pre-Signed Requests

Http4k supports pre-signed requests with the generic `AwsRequestPreSigner` class.
However, `http4k-connect` provides a simplified interface for common S3 Bucket operations with the `S3BucketPresigner`.





```kotlin
package content.ecosystem.connect.reference.amazon.s3

import org.http4k.aws.AwsCredentials
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.s3.model.BucketKey
import org.http4k.connect.amazon.s3.model.BucketName
import org.http4k.connect.amazon.s3.model.S3BucketPreSigner
import java.time.Duration

fun main() {
    // create pre-signer
    val preSigner = S3BucketPreSigner(
        bucketName = BucketName.of("foobar"),
        region = Region.of("us-east-1"),
        credentials = AwsCredentials("accessKeyId", "secretKey")
    )

    val key = BucketKey.of("keyName")

    // create a pre-signed PUT
    val put = preSigner.put(
        key = key,
        duration = Duration.ofMinutes(5), // how long the URL is valid for
        headers = listOf("content-type" to "application.json")  // add optional signed headers
    )
    println(put.uri)

    // create a pre-signed GET
    val get = preSigner.get(
        key = key,
        duration = Duration.ofMinutes(5)
    )
    println(get)

    // share these URIs to your clients so they can perform the operations without credentials
}

```



