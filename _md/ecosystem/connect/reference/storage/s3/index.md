# Storage: S3


### Installation 

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-storage-s3")
}
```


This implementation uses the http4k Connect adapter to store the data in S3. All data is serialised to disk by
passing it though an http4k AutoMarshalling adapter (see the `http4k-format-XXX` modules). In the example below we use a
JSON adapter backed by Moshi (which is the default).





```kotlin
package content.ecosystem.connect.reference.storage.s3

import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.s3.Http
import org.http4k.connect.amazon.s3.S3Bucket
import org.http4k.connect.amazon.s3.model.BucketName
import org.http4k.connect.storage.S3
import org.http4k.connect.storage.Storage
import org.http4k.format.Moshi
import java.time.Clock

data class AnEntity(val name: String)

val awsCredentials = AwsCredentials("accessKey", "secret")
val bucketClient = S3Bucket.Http(BucketName.of("foobar"), Region.AP_EAST_1, { awsCredentials }, JavaHttpClient(), Clock.systemUTC())

val storage = Storage.S3<AnEntity>(bucketClient, Moshi)

val store = run {
    storage["myKey"] = AnEntity("hello")
    println(storage["myKey"])
    storage.removeAll("myKey")
}

```



