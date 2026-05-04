# Storage: JDBC


### Installation 

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-storage-jdbc")
}
```


This implementation uses the Jetbrains Exposed library to store the data in the DB. All data is serialised to disk by
passing it though an http4k AutoMarshalling adapter (see the `http4k-format-XXX` modules). In the example below we use a
JSON adapter backed by Moshi (which is the default).





```kotlin
package content.ecosystem.connect.reference.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.connect.storage.Jdbc
import org.http4k.connect.storage.Storage
import org.http4k.format.Moshi

data class AnEntity(val name: String)

val ds = HikariDataSource(
    HikariConfig().apply {
        driverClassName = "org.h2.Driver"
        jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    })

val storage = Storage.Jdbc<AnEntity>(ds, "mytable", Moshi)

val store = run {
    storage["myKey"] = AnEntity("hello")
    println(storage["myKey"])
    storage.removeAll("myKey")
}

```



