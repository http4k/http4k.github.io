# Storage: Redis


### Installation 

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-storage-redis")
}
```


This implementation uses the Lettuce Client library to store the data in Redis. All data is serialised to disk by
passing it though an http4k AutoMarshalling adapter (see the `http4k-format-XXX` modules). In the example below we use a
JSON adapter backed by Moshi (which is the default).





```kotlin
package content.ecosystem.connect.reference.storage.redis

import org.http4k.connect.storage.Redis
import org.http4k.connect.storage.Storage
import org.http4k.core.Uri
import org.http4k.format.Moshi

data class AnEntity(val name: String)

val storage = Storage.Redis<AnEntity>(Uri.of("redis://host:8000"), Moshi)

val store = run {
    storage["myKey"] = AnEntity("hello")
    println(storage["myKey"])
    storage.removeAll("myKey")
}

```



