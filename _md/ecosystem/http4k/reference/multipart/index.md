# Multipart



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-multipart")
}
```

### About

Multipart form support for fields and files, including a set of lens extensions for fields/files.

See the [how-to guides](/howto/use_multipart_forms/) for example use.

### Receiving Binary content with http4k Contracts

With binary attachments, you need to turn ensure that the pre-flight validation does not eat the stream. This is possible by instructing http4k to ignore the incoming body for validation purposes:





```kotlin
package content.ecosystem.http4k.reference.multipart

import org.http4k.contract.PreFlightExtraction
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK

val binaryUploadRoute = "/api/document-upload" meta {
    preFlightExtraction = PreFlightExtraction.IgnoreBody
} bindContract POST to { req -> Response(OK) }

```



