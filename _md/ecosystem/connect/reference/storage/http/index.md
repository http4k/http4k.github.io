# Storage: HTTP


### Installation 

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-storage-http")
}
```


This storage implementation provides the ability to mount another storage implementation remotely over HTTP inside an OpenAPI compatible server.

You can mount the storage with: 




```kotlin
package content.ecosystem.connect.reference.storage.http

import org.http4k.connect.storage.InMemory
import org.http4k.connect.storage.Storage
import org.http4k.connect.storage.asHttpHandler
import org.http4k.server.SunHttp
import org.http4k.server.asServer

data class AnEntity(val name: String)

val baseStorage = Storage.InMemory<AnEntity>()
val storageServer = baseStorage.asHttpHandler().asServer(SunHttp(8000)).start()

```



Then simply use your browser to see the OpenAPI specification at http://localhost:8000:

<img class="imageMid" alt="openapi.png" src="openapi.png">

