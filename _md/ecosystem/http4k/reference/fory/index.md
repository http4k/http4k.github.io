# Format: Fory


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.61.0.0"))

 
    implementation("org.http4k:http4k-format-fory")
}
```

### About
This module adds the ability to use [Apache Fory](https://fory.apache.org) binary serialization when reading from and to HTTP and WebSocket messages.

We can use this facility in http4k to automatically marshall message bodies using **Lenses**. The body is set with a `Content-Type` of `application/octet-stream`.

For safety, Fory requires every type to be explicitly registered - unregistered types are refused both when writing and when reading untrusted payloads. Common JDK and http4k types (dates, times, `UUID`, `Uri`, `Status` etc.) are supported out of the box, and values4k types can be added with `value()`.

#### Code





```kotlin
package content.ecosystem.http4k.reference.fory

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.Fory
import org.http4k.format.register

data class Email(val value: String)
data class Message(val subject: String, val from: Email, val to: Email)

val MyFory = Fory {
    register<Email>()
    register<Message>()
}

fun main() {
    with(MyFory) {
        val messageLens = Body.auto<Message>().toLens()

        val myMessage = Message("hello", Email("bob@git.com"), Email("sue@git.com"))

        // 1. Lens-first approach
        val request = messageLens(myMessage, Request(GET, "/"))

        println(messageLens(request) == myMessage)

        // 2. with()/of() approach
        val request2 = Request(GET, "/").with(messageLens of myMessage)

        // 3. convenience functions
        val request3 = Request(GET, "/").binary(myMessage)
        println(request3.binary<Message>() == myMessage)
    }
}

```



