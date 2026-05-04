# Typesafe your API with lenses


Example showing how to create and apply lenses to requests and responses to both extract and inject typesafe values out of and into HTTP messages. Note that since the **http4k** `Request/Response` objects are immutable, all injection occurs via copy.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Standard (exception based) approach 
Errors in extracting Lenses are propagated as exceptions which are caught and handled by the `CatchLensFailure` Filter.





```kotlin
package content.howto.typesafe_your_api_with_lenses

import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.lens.Header
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.lens.composite
import org.http4k.lens.int
import org.http4k.lens.string

fun main() {

    data class Child(val name: String)
    data class Pageable(val sortAscending: Boolean, val page: Int, val maxResults: Int)

    val nameHeader = Header.required("name")
    val ageQuery = Query.int().optional("age")
    val childrenBody = Body.string(TEXT_PLAIN)
        .map({ it.split(",").map(::Child) }, { it.joinToString { it.name } })
        .toLens()
    val pageable = Query.composite {
        Pageable(
            boolean().defaulted("sortAscending", true)(it),
            int().defaulted("page", 1)(it),
            int().defaulted("maxResults", 20)(it)
        )
    }

    val endpoint = { request: Request ->

        val name: String = nameHeader(request)
        val age: Int? = ageQuery(request)
        val children: List<Child> = childrenBody(request)
        val pagination = pageable(request)

        val msg = """
            $name is ${age ?: "unknown"} years old and has 
            ${children.size} children (${children.joinToString { it.name }})
            Pagination: $pagination
            """
        Response(OK).with(
            Body.string(TEXT_PLAIN).toLens() of msg
        )
    }

    val app = ServerFilters.CatchLensFailure.then(endpoint)

    val goodRequest = Request(GET, "http://localhost:9000").with(
        nameHeader of "Jane Doe",
        ageQuery of 25,
        childrenBody of listOf(Child("Rita"), Child("Sue"))
    )

    println(listOf("", "Request:", goodRequest, app(goodRequest)).joinToString("\n"))

    val badRequest = Request(GET, "http://localhost:9000")
        .with(nameHeader of "Jane Doe")
        .query("age", "some illegal age!")

    println(listOf("", "Request:", badRequest, app(badRequest)).joinToString("\n"))
}

```



### Using "Result" ADT
An alternative approach to using Exceptions to automatically produce `BadRequests` is to use an Either-type structure, and this would be easy to implement - but the lack of an usable Result/Either type in the standard Kotlin library means that we have chosen to use `Result4k` as an optional dependency. If it is on the classpath you will gain support for it.

Additionally, the lack of Higher Kinded Types in Kotlin means that we are unable to provide a generic method for converting standard lenses. However, it is easy to implement an extension method to use in specific use cases - you can follow the example in the http4k source to implement your own version of the one we supply for Result4k. Below is an example which uses that Result4k ADT:

### Code





```kotlin
package content.howto.typesafe_your_api_with_lenses

import com.fasterxml.jackson.databind.JsonNode
import dev.forkhandles.result4k.Result
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.format.Jackson.json
import org.http4k.lens.LensFailure
import org.http4k.lens.Query
import org.http4k.lens.asResult
import org.http4k.lens.int

fun main() {
    val queryResultLens = Query.int().required("foo").asResult()
    val intResult: Result<Int, LensFailure> = queryResultLens(Request(GET, "/?foo=123"))
    println(intResult)

    val jsonResultLens = Body.json().toLens().asResult()
    val jsonResult: Result<JsonNode, LensFailure> = jsonResultLens(Request(GET, "/foo"))
    println(jsonResult)
}

```



