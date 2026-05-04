# Platform: Azure



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-platform-azure")
}
```

### About
This module provides a http4k compatible `HttpClient` so you can http4k-ise your use of the standard Azure SDKs libraries by plugging in a standard `HttpHandler`. This simplifies fault testing and means that you can print out the exact traffic which is going to Azure - which is brilliant for both debugging and writing Fakes. :)

#### Code





```kotlin
package content.ecosystem.http4k.reference.azure

import com.azure.core.credential.AzureKeyCredential
import com.azure.search.documents.indexes.SearchIndexClientBuilder
import org.http4k.azure.AzureHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters

fun main() {
    val http4kClient = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())

    val searchIndexClient = SearchIndexClientBuilder()
        .endpoint("https://....")
        .credential(AzureKeyCredential("APIKEY"))
        .httpClient(AzureHttpClient(http4kClient))
        .buildClient()

    searchIndexClient.deleteIndex("myIndex")
}

```



