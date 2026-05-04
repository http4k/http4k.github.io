# Platform: GCP



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-platform-gcp")
}
```

### About
This module provides a http4k compatible `HttpClient` so you can http4k-ise your use of the standard GCP SDKs libraries by plugging in a standard `HttpHandler`. This simplifies fault testing and means that you can print out the exact traffic which is going to Azure - which is brilliant for both debugging and writing Fakes. :)

#### Code





```kotlin
package content.ecosystem.http4k.reference.gcp

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpResponse
import org.http4k.client.OkHttp
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.gcp.GcpSdkHttpTransport

fun main() {
    val http4kClient = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())

    // you can plug this HttpTRansport into any GCP SDK client
    val client = GcpSdkHttpTransport(http4kClient)

    val response: HttpResponse = client.createRequestFactory().buildGetRequest(GenericUrl("https://example.com"))
        .execute()

    println(response)
}

```



