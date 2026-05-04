# Format: JSON


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

 
    // Argo:  
    implementation("org.http4k:http4k-format-argo")

    // Gson:  
    implementation("org.http4k:http4k-format-gson")

    // Jackson: 
    implementation("org.http4k:http4k-format-jackson")

    // Klaxon: 
    implementation("org.http4k:http4k-format-klaxon")

    // KondorJson: 
    implementation("org.http4k:http4k-format-kondor-json")

    // Moshi: 
    implementation("org.http4k:http4k-format-moshi")

    // KotlinX Serialization: 
    implementation("org.http4k:http4k-format-kotlinx-serialization")
}
```

### About
These modules add the ability to use JSON as a first-class citizen when reading from and to HTTP messages. Each 
implementation adds a set of standard methods and extension methods for converting common types into native JSON/XML 
objects, including custom Lens methods for each library so that JSON node objects can be written and read directly from
 HTTP messages:

### Notes on individual module choice
- As a default, we recommend using **Moshi** as an engine for JSON marshalling, as it is the most modern, lightweight and Kotlin-friendly library with automarshalling capabilities. It also has the option of [Kotshi](https://github.com/ansman/kotshi), which allows the compile-time generation of adapters for Kotlin data classes.
- If you are using OpenAPI contracts, the only current option is to use **Jackson**.
- **Kondor** is an excellent choice for manual mapping of classes to JSON, and does not use reflection.
- For the simplest use-cases, **Argo** is a good lightweight non-reflection choice, although it's API is Java-first.
- **GSON** support is provided in http4k, but is not recommended due to being mostly unsupported.
- We have found **KotlinSerialisation** is possibly the least friendly to use in the context of http4k.

#### Code





```kotlin
package content.ecosystem.http4k.reference.json

import com.fasterxml.jackson.databind.JsonNode
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.format.Jackson.asJsonArray
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asJsonValue
import org.http4k.format.Jackson.asPrettyJsonString
import org.http4k.format.Jackson.json
import org.http4k.format.Xml.xml
import org.w3c.dom.Node

val json = Jackson

// Extension method API:

val objectUsingExtensionFunctions: JsonNode =
    listOf(
        "thisIsAString" to "stringValue".asJsonValue(),
        "thisIsANumber" to 12345.asJsonValue(),
        "thisIsAList" to listOf(true.asJsonValue()).asJsonArray()
    ).asJsonObject()

val jsonString: String = objectUsingExtensionFunctions.asPrettyJsonString()

// Direct JSON library API:
val objectUsingDirectApi: JsonNode = json.obj(
    "thisIsAString" to json.string("stringValue"),
    "thisIsANumber" to json.number(12345),
    "thisIsAList" to json.array(listOf(json.boolean(true)))
)

// DSL JSON library API:
val objectUsingDslApi: JsonNode = json {
    obj(
        "thisIsAString" to string("stringValue"),
        "thisIsANumber" to number(12345),
        "thisIsAList" to array(listOf(boolean(true)))
    )
}

val response = Response(OK).with(
    Body.json().toLens() of json.array(
        listOf(
            objectUsingDirectApi,
            objectUsingExtensionFunctions,
            objectUsingDslApi
        )
    )
)

val xmlLens = Body.xml().toLens()

val xmlNode: Node = xmlLens(Request(GET, "").body("<xml/>"))

```



### Auto-marshalling capabilities

Some of the message libraries (eg. Moshi, Jackson, Kotlin serialization, GSON, XML, CSV ....) provide the mechanism to automatically marshall data objects 
to/from JSON and XML using reflection.

We can use this facility in http4k to automatically marshall objects to/from HTTP message bodies using **Lenses**. Note that this approach also sets the appropriate `Content-Type` header for the message.

#### Code





```kotlin
package content.ecosystem.http4k.reference.json

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.format.Jackson.json

data class Email(val value: String)
data class Message(val subject: String, val from: Email, val to: Email)

fun main() {
    // We can use the auto method here from either Moshi, Jackson ... message format objects.
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
//    content-type: application/json
//
//    {"subject":"hello","from":{"value":"bob@git.com"},"to":{"value":"sue@git.com"}}

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

    // 3. json() approach - user friendly but recreates the lense with every call
    val requestWithEmail3 = Request(GET, "/").json(myMessage)

    println(requestWithEmail3)

    val extractedMessage2 = requestWithEmail3.json<Message>()

    println(extractedMessage2)
}

```



serializing an object/class for a Response via `Lens.inject()` - this properly sets the `Content-Type` header to `application/json`:




```kotlin
import kotlinx.serialization.Serializable
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.lens.BiDiBodyLens

@Serializable // required by Kotlinx.Serialization
data class Car(val brand: String, val model: String, val year: Int, val miles: Int)

// 'auto' is an extension function of each org.http4k.format.[serialization library]
// example: https://github.com/http4k/http4k/blob/master/http4k-format/kotlinx-serialization/src/main/kotlin/org/http4k/format/ConfigurableKotlinxSerialization.kt
val lensCarResponse: BiDiBodyLens<Car> =
    Body.auto<Car>().toLens() // BiDi allows for outgoing + incoming

fun main() {

    val sweetride = Car("Porsche", "911 Turbo", 1988, 45000)

    // lens(object, response) serializes the object and sets content-type header to 'application/json'
    // can be used with any Serializable type (Map, List, etc)
    val app: HttpHandler =
        { _: Request -> lensCarResponse(sweetride, Response(Status.OK)) }

    val request: Request = Request(Method.GET, "/")
    val response = app(request)

    println(response)
    /*
    HTTP/1.1 200 OK
    content-type: application/json; charset=utf-8

    {"brand":"Porsche","model":"911 Turbo","year":1988,"miles":45000}
    */
}

```



There is a utility to generate Kotlin data class code for JSON documents [here](http://toolbox.http4k.org/dataclasses). 
These data classes are compatible with using the `Body.auto<T>()` functionality. 

#### FAQ (aka gotchas) regarding Auto-marshalling capabilities

**Q. Where is the `Body.auto` method defined?**

**A.** `Body.auto` is an extension method which is declared on the parent singleton `object` for each of the message libraries that supports auto-marshalling - eg. `Jackson`, `Gson`, `Moshi` and `Xml`. All of these objects are declared in the same package, so you need to add an import similar to:
`import org.http4k.format.Jackson.auto`

**Q. Jackson: The Data class auto-marshalling is not working correctly when my JSON fields start with capital letters**

**A.** Because of the way in which the Jackson library works, uppercase field names are NOT supported. Either switch out to use `http4k-format-gson` (which has the same API), or annotate your Data class with `@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)` or the fields with `@JsonAlias` or to get it work correctly.

**Q. Jackson: Boolean properties with names starting with "is" do not marshall properly**

**A.** This is due to the way in which the Jackson `ObjectMapper` is configured. Annotation of the fields in question should help, or using `ObjectMapper.disable(MapperFeature.AUTO_DETECT_IS_GETTERS)`

**Q. Moshi: Declared with `Body.auto<List<XXX>>().toLens()`, my auto-marshalled List doesn't extract properly!**

**A.** This occurs in Moshi when serialising bare lists to/from JSON and is to do with the underlying library being lazy in deserialising objects (using LinkedHashTreeMap) ()). Use `Body.auto<Array<MyIntWrapper>>().toLens()` instead. Yes, it's annoying but we haven't found a way to turn if off.

**Q. Kotlin Serialization: the standard mappings are not working on my data classes.**

**A.** This happens because http4k adds the standard mappings to Kotlin serialization as contextual serializers. This can be solved by marking the fields as `@Contextual`.

**Q. Gson: the data class auto-marshalling does not fail when a null is populated in a Kotlin non-nullable field**

**A.** This happens because http4k uses straight GSON demarshalling, of JVM objects with no-Kotlin library in the mix. The nullability generally gets checked at compile-type and the lack of a Kotlin sanity check library exposes this flaw. No current fix - apart from to use the Jackson demarshalling instead!

This can be demonstrated by the following, where you can see that the output of the auto-unmarshalling a naked JSON is NOT 
the same as a native Kotlin list of objects. This can make tests break as the unmarshalled list is NOT equal to the native list.

As shown, a workaround to this is to use `Body.auto<Array<MyIntWrapper>>().toLens()` instead, and then compare using 
`Arrays.equal()`







```kotlin
package content.ecosystem.http4k.reference.json

import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.format.Moshi.auto

data class MyIntWrapper(val value: Int)

fun main() {
    val aListLens = Body.auto<List<MyIntWrapper>>().toLens()

    val req = Request(GET, "/").body(""" [ {"value":1}, {"value":2} ] """)

    val extractedList = aListLens(req)

    val nativeList = listOf(MyIntWrapper(1), MyIntWrapper(2))

    println(nativeList)
    println(extractedList)
    println(extractedList == nativeList)

    //solution:
    val anArrayLens = Body.auto<Array<MyIntWrapper>>().toLens()

    println(anArrayLens(req).contentEquals(arrayOf(MyIntWrapper(1), MyIntWrapper(2))))

// produces:
//    [MyIntWrapper(value=1), MyIntWrapper(value=2)]
//    [{value=1}, {value=2}]
//    false
//    true
}

```



[http4k]: https://http4k.org

