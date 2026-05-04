# Platform: AWS



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-platform-aws")
}
```

### About
This module provides 2 things: a http4k compatible `SdkHttpClient` and a super-simple AWS request signing functionality for talking to AWS services.

1. With the `SdkHttpClient` you can use the standard Amazon SDKs libraries by plugging in a standard `HttpHandler`. This simplifies fault testing and means that you can print out the exact traffic which is going to AWS - which is brilliant for both debugging and writing Fakes. :)

#### Code





```kotlin
package content.ecosystem.http4k.reference.aws

import org.http4k.aws.AwsSdkClient
import org.http4k.client.OkHttp
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest

fun main() {
    val http4kClient = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())

    val s3 = S3Client.builder()
        .region(EU_WEST_1)
        .credentialsProvider { AwsBasicCredentials.create("accessKey", "secret") }
        .httpClient(AwsSdkClient(http4kClient))
        .build()

    s3.createBucket(CreateBucketRequest.builder().bucket("hello").build())
}

```



2. With the request signing functionality, once configured with the correct keys, the various AWS services are actually really simple to integrate with. They're just RESTy-type HTTPS services - the main difficulty is that all requests need to have their contents digitally signed with the AWS credentials to be authorised.

http4k provides a `Filter` which does this request signing process. Just decorate a standard HTTP client and then make the relevant calls:


#### Code





```kotlin
package content.ecosystem.http4k.reference.aws

import org.http4k.aws.AwsCredentialScope
import org.http4k.aws.AwsCredentials
import org.http4k.client.ApacheClient
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.AwsAuth
import org.http4k.filter.ClientFilters
import java.util.UUID

fun main() {

    val region = "us-east-1"
    val service = "s3"
    val accessKey = "myGreatAwsAccessKey"
    val secretKey = "myGreatAwsSecretKey"

    val client = ClientFilters.AwsAuth(
        AwsCredentialScope(region, service),
        AwsCredentials(accessKey, secretKey)
    )
        .then(ApacheClient())

    // create a bucket
    val bucketName = UUID.randomUUID().toString()
    val bucketUri = Uri.of("https://$bucketName.s3.amazonaws.com/")
    println(client(Request(PUT, bucketUri)))

    // get list of buckets with the new bucket in it
    println(client(Request(GET, Uri.of("https://s3.amazonaws.com/"))).bodyString())

    // create a key into the bucket
    val key = UUID.randomUUID().toString()

    val keyUri = Uri.of("https://$bucketName.s3.amazonaws.com/$key")
    println(client(Request(PUT, keyUri).body("some amazing content that I want stored on S3")))

    // get the keys in the bucket
    println(client(Request(GET, bucketUri)))

    // get the contents of the key in the bucket
    println(client(Request(GET, keyUri)))

    // delete the key in the bucket
    println(client(Request(GET, keyUri)))

    // delete the bucket
    println(client(Request(GET, bucketUri)))
}

```



