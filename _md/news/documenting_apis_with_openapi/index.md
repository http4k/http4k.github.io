# Documenting http4k APIs with OpenApi3


This post describes **http4k** support for fully describing and securing HTTP endpoints using version 3 of the **[OpenApi]** specification, providing typesafe JSON-schema documentation for messages and automatically validating incoming HTTP traffic.

### About OpenApi
In microservice environments, some of the biggest challenges exist around the communications between processes that simply aren't present when you're doing monolith-based development. This manifests in many different operational ways such as monitoring, discovery and fault tolerance, but one of the key aspects is communicating the the HTTP contract provided by a particular service.

There have been various efforts to standardise these aspects, and one of the most popular is **[OpenApi]**, which grew out of the original **[Swagger]** project. There are 3 key advantages to OpenApi:

1. It provides a standardised way of documenting APIs, including routes, parameter optionality and format, security models and JSON Schema breakdown of JSON messages. It has standardised support from cloud providers such as **[Google Cloud Endpoints]** and **[AWS API Gateway]**.
1. The OpenApi **[UI](https://http4k.org/openapi3/)** allows a very simple and developer-focused way of exploring and interacting with HTTP services from a browser environment.
1. It is cross-platform and has good tooling support. Using **[OpenApi Generators]**, a specification document can be used to generate HTTP server stubs and working HTTP clients in a variety of languages, thus reducing integration efforts.

### Typesafe HTTP contracts with http4k-api-openapi
http4k has supported generating version 2 of **[OpenApi]** docs since all the way back in 2017 (v1.16) via it's `http4k-api-openapi` module, and after a couple of releases ironing out the niggles (and some amazing help from the community), the team is now happy to announce OpenApi3 support with the release of http4k version 3.179.0.

In line with the overall **[ethos of the project](/overview/)**, http4k OpenApi support is done entirely through code and in a typesafe and refactorable way. This is somewhat of a departure from how most other libraries have implemented OpenApi (where often annotations and other compile-time magic are used) and means that in http4k the spec defined in code is the same one that is used to generate the API documentation and the same one used to validate incoming HTTP messages, meaning that it can never go stale. This focus on runtime code also allows for dynamic behaviours which would be very difficult to replicate at compile-time.

Out of the box, `http4k-api-openapi` the module now provides the following features when configured for OpenApi3:

1. **Automatic generation of route documentation** in OpenApi v3 format, including the JSON Schema models for example incoming and outgoing messages (which arguably provide at least 50% of the value of using OpenApi).
1. **Complete auto-validation** of the defined HTTP contract through the typesafe http4k Lens mechanism - violations are automatically detected and a BAD_REQUEST returned to the caller. This means that zero custom validation code is required to clutter up your routing layer and you can concentrate on working with meaningful domain types instead of primitives.
1. **Support/implementation of all defined OpenApi security models** at both a global and per-route scope - BearerToken, ApiKey, OAuth and BasicAuth, although you can of course define and use custom implementations.
1. **Simple API for defining custom [OpenApi extensions]** to extend the outputted specification document, for example using http4k in with **[AWS API Gateway]** or **[Google Cloud Endpoints]**

So, how does we do all this using the http4k API? Let's find out with a worked example. 

### 1. Your first endpoint
After importing the `http4k-core` and `http4k-api-openapi` dependencies into your project, we can write a new endpoint aka `ContractRoute`. The first thing to note is that we will be using a slightly different routing DSL the standard http4k one, one which provides a richer way to document endpoints - but don't worry - at it's core it utilises the same simple http4k building blocks of `HttpHandler` and `Filter`, as well as leveraging the **[http4k Lens API]** to automatically extract and convert incoming  parameters into richer domain types. As ever, routes can (and should) be written and testing independently, which aids code decomposition and reuse. 

In this simple example, we're going to use a path with two dynamic parameters; `name` - a String, and the Integer `age` - which will be extracted and "mapped" into the constructor of a simple validated domain wrapper type. If the basic format of the path or the values for these path parameters cannot be extracted correctly, the endpoint fails to match and is skipped - this allows for several different variations of the same URI path signature to co-exist. 

Once the values have been extracted, they are passed as arguments to a function which will return a pre-configured `HttpHandler` for that call:





```kotlin
package content.news.documenting_apis_with_openapi

import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.int

data class Age(val value: Int) {
    init {
        require(value >= 0)
    }
}

fun basicHandler(name: String, age: Age): HttpHandler = { req: Request ->
    val beverage = if (age.value >= 18) "beer" else "lemonade"
    Response(OK).body("Hello $name, would you like some $beverage?")
}

val basicRoute: ContractRoute =
    "/greet" / Path.of("name") / Path.int().map(::Age).of("age") bindContract
        GET to ::basicHandler

```



And here's a unit test for that endpoint - the good news is that it's no more complex than a standard http4k unit test because `ContractRoute` is also an `HttpHandler` so can just be invoked as a function. Here, we're also leveraging the `http4k-testing-hamkrest` module to supply **[Hamkrest]** Matchers for validating the response message:





```kotlin
package content.news.documenting_apis_with_openapi

import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.hamkrest.hasBody
import org.junit.jupiter.api.Test

class BasicGreetingRouteTest {
    @Test
    fun `greets an adult`() {
        assertThat(
            basicRoute(Request(GET, "/greet/Bob/21")),
            hasBody("Hello Bob, would you like some beer?")
        )
    }
}

```



### 2. Defining an HTTP contract
Now that we've got our endpoint, we want to be able to actually serve it with the **[OpenApi]** documentation. For contract-based routing, we use the `contract {}` routing DSL which allows us to specify a richer set of details about the API definition, but exposes exactly the same API semantics as the standard `routes()` block - it is also an `HttpHandler` and can therefore be composed together to form standard route-matching trees.

For rendering the API documentation, we configure an `OpenApi` object, supplying a standard http4k JSON adapter instance - the recommended one to use is `Jackson` from the `http4k-format-jackson` module, so we'll need to import that module into our project as well.

Whilst all of the settings used in this DSL above are optional (and default to sensible values if not overridden), here we are updating the URL where the OpenApi spec is served and supplying an instance of `Security` that we will use to protect our routes (more about that later). 





```kotlin
package content.news.documenting_apis_with_openapi

import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Credentials
import org.http4k.core.HttpHandler
import org.http4k.format.Jackson
import org.http4k.server.Undertow
import org.http4k.server.asServer

fun main() {
    val http: HttpHandler = contract {
        renderer = OpenApi3(ApiInfo("my secure api", "v1.0", "API description"), Jackson)
        descriptionPath = "/reference/api/swagger.json"
        security = org.http4k.security.BasicAuthSecurity("realm", Credentials("user", "password"))
        routes += basicRoute
    }

    http.asServer(Undertow(9000)).start()
}

```



Now we've got a complete contract, we can simply start the server and browse to `http://localhost:9000/api/swagger.json` to see the basic API spec in the OpenApi UI (or see the online version **<a target="_blank" href="https://http4k.org/openapi3/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fhttp4k%2Fhttp4k%2Fmaster%2Fsrc%2Fdocs%2Fblog%2Fdocumenting_apis_with_openapi%2F2_openapi.json">here</a>**) to see how the endpoint contract looks and how the process of supplying credentials is done through the UI by clicking `Authorize`. 

This covers the very basics of generating API docs, but there is still a lot more http4k can do for us...

### 3. Auto-validating incoming HTTP messages
For a better standard of API docs, we should add more details to the endpoint definition. The OpenAPI spec allows us to add this detail, but this normally comes with a maintenance cost - especially when the documentation is static or disparate from the location of the actual code serving requests, and we want to minimise the risk of stale documentation.
In http4k, the extended contract metadata is kept close to the endpoint code and mostly type-checked by the compiler, so this threat is minimised as far as practical. 

Metadata for endpoints can be supplied via inserting a `meta {}` DSL block, which contains a mixture of 2 main types of property: 

1. **Informational** properties - such as `summary`, `description` and `tags` simply improve the experience of the user of the UI.
1. **Contractual** properties define parameters using the **[http4k Lens API]** (in the same way as we used for the path) for the `Query`, `Header` or `Body` parts of the request. Once added to the contract, these items will also be auto-validated for form and presence before the contract HttpHandler is invoked, thus eliminating the need for any custom validation code to be written. We can then use the same lenses to confidently extract those values inside our HttpHandler code.

Let's demonstrate by writing a slightly different version of the same endpoint, but move `age` to be a required query parameter, and also add the option to override the `drink` we offer:





```kotlin
package content.news.documenting_apis_with_openapi

import org.http4k.contract.ContractRoute
import org.http4k.contract.Tag
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int

data class Drink(val name: String) {
    init {
        require(name.isNotEmpty())
    }
}

fun Greetings(): ContractRoute {
    val age = Query.int().map(::Age).required("age", "Your age")
    val favouriteDrink = Query.map(::Drink).optional("drink", "Your favourite beverage")

    fun handler(name: String): HttpHandler = { req: Request ->
        val drinkToOffer: Drink? = favouriteDrink(req)
        val beverage: String = drinkToOffer?.name
            ?: if (age(req).value >= 18) "beer" else "lemonade"
        Response(OK).body("Hello $name, would you like some $beverage?")
    }

    return "/greet" / Path.of("name", "Your name") meta {
        summary = "Send greetings"
        description =
            "Greets the stupid human by offering them a beverage suitable for their age"
        tags += Tag("query")
        queries += favouriteDrink
        queries += age
        produces += TEXT_PLAIN
        returning(OK to "A successful offer of a drink to the lowly meatbag.")
    } bindContract GET to ::handler
}

```



If we then add the `Greetings` endpoint to the contract and make a call omitting `age`...

```http://localhost:9000/greet/Bob?drink=cola```

... the contract validation will fail and a HTTP Bad Request (400) returned to the client with a JSON body describing the error:





```kotlin
HTTP/1.1 400 Bad Request
content-type: application/json; charset=utf-8

{
  "message": "Missing/invalid parameters",
  "params": [
    {
      "name": "age",
      "type": "query",
      "datatype": "integer",
      "required": true,
      "reason": "Missing"
    }
  ]
}

```



We can see the updated OpenApi UI **<a target="_blank" href="https://http4k.org/openapi3/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fhttp4k%2Fhttp4k%2Fmaster%2Fsrc%2Fdocs%2Fblog%2Fdocumenting_apis_with_openapi%2F3_openapi.json">here</a>**. Note that because request parameters are validated before sending, we cannot replicate the above invalid request in the UI.

### 4. Modelling HTTP body messages
The most exciting part http4k supporting OpenApi3 is the ability to represent HTTP messages in **[JSON Schema]** form in the documentation. This facility is what unlocks the true cross-language support and takes the usefulness of the OpenApi UI to another level, for both exploratory and support functions. Request and response messages can both be specified in the `meta {}` block using overloads of the `receiving()` and `returning()` functions. By using these functions, we can supply an example object to the DSL - this is what drives the generation of the JSON Schema and, more importantly, ensures that the documentation cannot go stale as it is driven by code.

Lets add another route to the mix which returns a JSON body object modelled with a Kotlin Data class and once again using the **[http4k Lens API]**. Here, the lens not only provides the validating (de)serialisation mechanism, but also activates the `Content-Type` header injection and parsing behaviour - this will ensure that all incoming and outgoing messages have the correct headers. 

For JSON bodies, the lens is created with `Body.auto<>().toLens()` (`auto()` is an extension function imported from `Jackson`) which provides the typed injection and extraction functions. Notice here that for injection we are using the more fluent API  `with()` and `of()` extension functions, as opposed to the standard lens injection function`(X, HttpMessage) -> HttpMessage`:





```kotlin
package content.news.documenting_apis_with_openapi

import org.http4k.contract.ContractRoute
import org.http4k.contract.Tag
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path

data class Person(val name: String, val age: Age, val children: List<Person> = emptyList())

fun Family(): ContractRoute {

    val familyData = Person(
        "Bob",
        Age(85),
        listOf(
            Person("Anita", Age(55)),
            Person("Donald", Age(52), listOf(Person("Don Jr", Age(21))))
        )
    )

    val responseLens = Body.auto<Person>("The matched family tree").toLens()

    fun handler(queryName: String): HttpHandler = {
        fun Person.search(): Person? = when (name) {
            queryName -> this
            else -> children.firstOrNull { it.search() != null }
        }

        familyData.search()
            ?.let { Response(OK).with(responseLens of it) }
            ?: Response(NOT_FOUND)
    }

    return "/search" / Path.of("name", "The name to search for in the tree") meta {
        summary = "Search family tree"
        description = "Given a name, returns a sub family tree starting with that person"
        tags += Tag("query")
        returning(
            OK,
            responseLens to Person("Donald", Age(52), listOf(Person("Don Jr", Age(21)))),
            "Cut down family tree"
        )
        returning(NOT_FOUND to "That person does not exist the family")
    } bindContract GET to ::handler
}

```



Taking a final look at the OpenApi UI **<a target="_blank" href="https://http4k.org/openapi3/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fhttp4k%2Fhttp4k%2Fmaster%2Fsrc%2Fdocs%2Fblog%2Fdocumenting_apis_with_openapi%2F4_openapi.json">here</a>** shows that not just has the UI been updated with the new route, but that example entries for the expected response are now displayed, as well as JSON Schema entries for the `Person` and `Age` classes in the `Schemas` section at the bottom.

### Wrapping up...
Once we have the final specification document available, users of our API can use the various **[OpenApi Generators]** to generate HTTP clients in various languages for interacting with it, or to generate fake services that provide our API in their own environments (and thus enabling more simple end-to-end testing). The "Fake HTTP services" technique also enables the creation of Consumer-Driven-Contract style tests, and opens up possibilities for all kinds of interesting Chaos/failure-mode testing (you can even use the `http4k-testing-chaos` module to help with this 😉).

The full source for this tutorial can be found **[here](https://github.com/http4k/http4k/tree/master/src/docs/blog/documenting_apis_with_openapi/)**, or for a sense of how this all looks in when mixed into a complete http4k project, check out the **[http4k-by-example]** repo, which contains an entire TDD'd project showcasing a multitude of http4k features and testing styles.

[http4k]: https://http4k.org
[github]: https://github.com/daviddenton
[Swagger]: https://swagger.io
[Hamkrest]: https://github.com/npryce/hamkrest
[OpenApi]: https://www.openapis.org/
[JSON Schema]: https://json-schema.org/
[OpenApi Generators]: https://openapi-generator.tech
[OpenApi extensions]: https://swagger.io/docs/specification/openapi-extensions/
[AWS API Gateway]: https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html
[Google Cloud Endpoints]: https://cloud.google.com/endpoints/docs/openapi/
[http4k-by-example]: https://github.com/http4k/http4k-by-example
[http4k Lens API]:  /ecosystem/http4k/reference/core/#typesafe-parameter-destructuringconstruction-of-http-messages-with-lenses

