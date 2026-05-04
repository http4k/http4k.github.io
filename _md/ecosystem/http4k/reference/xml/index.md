# Format: XML



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    // json.org XML:
    implementation("org.http4k:http4k-format-xml")

    // Jackson XML:
    implementation("org.http4k:http4k-format-jackson-xml")
}
```

### About
These modules provide auto-marshalling functionality to convert XML into arbitrary data classes. The 2 differ slightly in their behaviour, due to the underlying libraries used for implementation. We recommend using `http4k-format-jackson-xml` as it has more predictable and consistent behaviour.

### JacksonXml
- Provides bi-directional conversion
- Does NOT expose an XML DOM node model
- Generally requires `Jackson` field annotations to manipulate output format
- Provides extension point to map custom types using BiDiMapping registration (so supports all Java and Http4k primitives such as `Uri`)

#### JacksonXML Code 
- Provides extraction conversion only
- Exposes an XML DOM node model as a first-class citizen - so can read directly from a string into a DOM model
- Does not generate a wrapper element to represent the parent node
- Has trouble with repeating child-elements, depending on zero, one or many elements in the XML. This is due to the underlying library implementation
- Only handles objects with primitive JDK types





```kotlin
package content.ecosystem.http4k.reference.xml

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.JacksonXml.auto

data class JacksonWrapper(val message: JacksonMsg?)

data class JacksonMsg(
    val subject: String?,
    val from: String?,
    val to: String?,
    val content: String?
)

fun main() {
    // We can use the auto method here from the JacksonXML message format object. Note that the
    // auto() method is an extension function which needs to be manually imported (IntelliJ won't pick it up automatically).
    val messageLens = Body.auto<JacksonWrapper>().toLens()

    // extract the body from the message - this also works with Response
    val wrapper = JacksonWrapper(JacksonMsg("subject", "from", "to", "content"))
    val message =
        """<jacksonWrapper><message subject="hi"><from>david@http4k.org</from><to>ivan@http4k.org</to>hello world</message></jacksonWrapper>"""

    println(messageLens(Request(GET, "/").body(message)))

    // inject a converted object-as-XML-string into a request
    println(Request(GET, "").with(messageLens.of(wrapper)).bodyString())
}

```



### Xml
As above, we recommend using `http4k-format-jackson-xml` as it has more predictable and consistent behaviour.
 
#### XML Code 





```kotlin
package content.ecosystem.http4k.reference.xml

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.format.Xml.auto

data class XmlWrapper(val mesApacheClientStreamingContractessage: MessageXml?)

data class MessageXml(
    val subject: String?,
    val from: String?,
    val to: String?,
    val content: String?
)

fun main() {
    // We can use the auto method here from the Xml message format object. Note that the
    // auto() method is an extension function which needs to be manually imported (IntelliJ won't pick it up automatically).
    // Also, this lense is ONLY one way - to extract values from a message
    val messageLens = Body.auto<XmlWrapper>().toLens()

    // extract the body from the message - this also works with Response
    val message =
        """<message subject="hi"><from>david@http4k.org</from><to>ivan@http4k.org</to>hello world</message>"""
    val requestWithEmail = Request(GET, "/").body(message)

    println(messageLens(requestWithEmail))
}

```



