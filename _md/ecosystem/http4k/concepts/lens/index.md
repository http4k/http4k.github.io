# Lenses


Lenses provide typesafe parameter destructuring/construction of HTTP messages. Getting values from HTTP messages is one thing, but we want to ensure that those values are both present and valid. For this purpose, we can use a [Lens](https://www.schoolofhaskell.com/school/to-infinity-and-beyond/pick-of-the-week/basic-lensing).

A Lens is a bi-directional entity which can be used to either **get** or **set** a particular value from/onto an HTTP message. http4k provides a DSL
to configure these lenses to target particular parts of the message, whilst at the same time specifying the requirement for those parts (i.e. mandatory or optional).

To utilise a lens, first you have to declare it with the form `<Location>.<configuration and mapping operations>.<terminator>`.

There is one "location" type for each part of the message, each with config/mapping operations which are specific to that location:

| Location  | Starting type | Applicable to           | Multiplicity         | Requirement terminator | Examples  |
------------|---------------|-------------------------|----------------------|------------------------|------------
| Query     | `String`      | `Request`               | Singular or multiple | Optional or Required   | `Query.optional("name")`<br/>`Query.required("name")`<br/>`Query.int().required("name")`<br/>`Query.localDate().multi.required("name")`<br/>`Query.map(::CustomType, { it.value }).required("name")` |
| Header    | `String`      | `Request` or `Response` | Singular or multiple | Optional or Required   | `Header.optional("name")`<br/>`Header.required("name")`<br/>`Header.int().required("name")`<br/>`Header.localDate().multi.required("name")`<br/>`Header.map(::CustomType, { it.value }).required("name")`|
| Path      | `String`      | `Request`               | Singular | Required   |  `Path.of("name")`<br/>`Path.int().of("name")`<br/>`Path.map(::CustomType, { it.value }).of("name")`|
| FormField | `String`      | `WebForm`               | Singular or multiple | Optional or Required   | `FormField.optional("name")`<br/>`FormField.required("name")`<br/>`FormField.int().required("name")`<br/>`FormField.localDate().multi.required("name")`<br/>`FormField.map(::CustomType, { it.value }).required("name")`|
| Body      | `ByteBuffer`  | `Request` or `Response` | Singular | Required   |  `Body.string(ContentType.TEXT_PLAIN).toLens()`<br/>`Body.json().toLens()`<br/>`Body.webForm(Validator.Strict, FormField.required("name")).toLens()` |

Once the lens is declared, you can use it on a target object to either get or set the value:

- Retrieving a value: use `<lens>.extract(<target>)`, or the more concise invoke form: `<lens>(<target>)`
- Setting a value: use `<lens>.inject(<value>, <target>)`, or the more concise invoke form: `<lens>(<value>, <target>)`

#### Code





```kotlin
package content.ecosystem.http4k.concepts.lens

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.lens.Header
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.localDate
import org.http4k.lens.nonEmptyString
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.time.LocalDate

val pathLocalDate = Path.localDate().of("date")
val requiredQuery = Query.required("myQueryName")
val nonEmptyQuery = Query.nonEmptyString().required("myNonEmptyQuery")
val optionalHeader = Header.int().optional("Content-Length")
val responseBody = Body.string(ContentType.TEXT_PLAIN).toLens()

// Most of the useful common JDK types are covered. However, if we want to use our own types, we can just use `map()`
data class CustomType(val value: String)

val requiredCustomQuery = Query.map(::CustomType, { it.value }).required("myCustomType")

//To use the Lens, simply `invoke() or extract()` it using an HTTP message to extract the value, or alternatively
// `invoke() or inject()` it with the value if we are modifying (via copy) the message:
val handler: RoutingHttpHandler = routes(
    "/hello/{date:.*}" bind GET to { request: Request ->
        val pathDate: LocalDate = pathLocalDate(request)
        // SAME AS:
        // val pathDate: LocalDate = pathLocalDate.extract(request)

        val customType: CustomType = requiredCustomQuery(request)
        val anIntHeader: Int? = optionalHeader(request)

        val baseResponse = Response(OK)
        val responseWithHeader = optionalHeader(anIntHeader, baseResponse)
        // SAME AS:
        // val responseWithHeader = optionalHeader.inject(anIntHeader, baseResponse)

        responseBody("you sent $pathDate and $customType", responseWithHeader)
    }
)

//With the addition of the `CatchLensFailure` filter, no other validation is required when using Lenses, as http4k
// will handle invalid requests by returning a BAD_REQUEST (400) response.
val app = ServerFilters.CatchLensFailure.then(handler)(
    Request(
        GET,
        "/hello/2000-01-01?myCustomType=someValue"
    )
)

//More conveniently for construction of HTTP messages, multiple lenses can be used at once to modify a message,
// which is useful for properly building both requests and responses in a typesafe way without resorting to string
// values (especially in URLs which should never be constructed using String concatenation):
val modifiedRequest: Request = Request(GET, "http://google.com/{pathLocalDate}").with(
    pathLocalDate of LocalDate.now(),
    requiredQuery of "myAmazingString",
    optionalHeader of 123
)

```



