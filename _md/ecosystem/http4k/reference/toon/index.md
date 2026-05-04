# Format: Toon


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

 
    implementation("org.http4k:http4k-format-toon")
}
```

### About
This module add the ability to use Toon as a first-class citizen when reading from and to HTTP messages. 

We can use this facility in http4k to automatically marshall HTTP message bodies using **Lenses**. Note that this approach also sets the appropriate `Content-Type` header for the message.

#### Code





```kotlin
package content.ecosystem.http4k.reference.toon

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.Gson.with
import org.http4k.format.Toon.auto

data class Email(val value: String)
data class Message(val subject: String, val from: Email, val to: Email)

fun main() {
    // We can use the auto method here
    // Note that the auto() method needs to be manually imported as IntelliJ won't pick it up automatically.
    val messageLens = Body.auto<Message>().toLens()

    val myMessage = Message("hello", Email("bob@git.com"), Email("sue@git.com"))

    /**
     * There are several options for injection/extraction API:
     */

    // 1. Lens-first approach

    // to inject the body into the message apply the lens with the "part" - this also works with Response
    val requestWithEmail = messageLens(myMessage, Request(GET, "/"))

    println(requestWithEmail)

// Produces:
//    GET / HTTP/1.1
//    content-type: text/toon; charset=utf-8
//
//    subject: hello
//      from:
//    value: bob@git.com
//      to:
//    value: sue@git.com

    // to extract the body from the message apply the lens - this also works with Response
    val extractedMessage = messageLens(requestWithEmail)

    println(extractedMessage)
    println(extractedMessage == myMessage)

// Produces:
//    Message(subject=hello, from=Email(value=bob@git.com), to=Email(value=sue@git.com))
//    true

    // 2. with()/of() approach - this reuses the lense
    val requestWithEmail2 = Request(GET, "/").with(messageLens of myMessage)

    println(requestWithEmail2)
}

```



[http4k]: https://http4k.org

