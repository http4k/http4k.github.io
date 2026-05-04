# API: OpenApi



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-api-openapi")
    implementation("org.http4k:http4k-format-<insert json lib>")
}
```

### About
The `http4k-api-openapi` module adds a much more sophisticated routing mechanism to that available in `http4k-core`. It adds the facility 
to declare server-side `Routes` in a completely typesafe way, leveraging the Lens functionality from the core. These `Routes` are combined into `Contracts`, which have the following features:

- **Auto-validating** - the `Route` contract is automatically validated on each call for required-fields and type conversions, removing the requirement  for any validation code to be written by the API user. Invalid calls result in a `HTTP 400 (BAD_REQUEST)` response.     
- **Self-describing:** - a generated endpoint is provided which describes all of the `Routes` in that module. Implementations include [OpenApi v2 & v3](http://swagger.io/) documentation, including generation of [JSON schema](http://json-schema.org/). These documents can then be used to generate HTTP client and server code in various languages using the [OpenAPI generator](https://openapi-generator.tech/).
 models for messages.
- **Security:** to secure the `Routes` against unauthorised access. Current implementations include `ApiKey`, `BasicAuth`, `BearerAuth`, `OpenIdConnect` and `OAuth`.
- **Callbacks and Webhooks** can be declared, which give the same level of documentation and model generation
#### Code





```kotlin
package content.ecosystem.http4k.reference.contracts

import org.http4k.contract.ContractRoute
import org.http4k.contract.Tag
import org.http4k.contract.bindCallback
import org.http4k.contract.contract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.Credentials
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.I_M_A_TEAPOT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.format.Klaxon.json
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes

// for this example we're using Jackson - note that the auto method imported is an extension
// function that is defined on the Jackson instance

// 1. this route has no dynamic path segments and simply echoes the body back. We are also adding various metadata
// to the route, which will be used in the OpenAPI documentation. All of the metadata is optional.
fun echo(): ContractRoute =
    "/echo" meta {
        summary = "echoes the body back"
        description = "This is a simple route which echoes the body back to the caller"
        tags += Tag("env", "production")
        consumes += TEXT_PLAIN
        produces += TEXT_PLAIN
        returning(OK, I_M_A_TEAPOT)
    } bindContract POST to { req: Request ->
        Response(OK).body(req.body)
    }

// 2. this route has a dynamic path which is automatically injected into the handler and security applied. There
// are various security instances, from Basic to APIKey to OAuth2
fun securelyGreet(): ContractRoute =
    "/greet" / Path.of("name") meta {
        security = org.http4k.security.BasicAuthSecurity("myrealm", Credentials("user", "password"))
    } bindContract POST to { name ->
        { _: Request ->
            Response(OK).body("hello $name")
        }
    }

// 3. this route uses a query lens to extract a parameter from the query string. we add the lens to the contract metadata
fun copy(): ContractRoute {
    val times = Query.int().required("times")

    return "/copy" meta {
        // register the query lens with the contract
        queries += times
    } bindContract POST to
        { req: Request ->
            // extract the value from the request using the lens
            val copies: Int = times(req)
            Response(OK).body(req.bodyString().repeat(copies))
        }
}

// 4. echoing JSON
fun echoJson(): ContractRoute {
    data class NameAndMessage(val name: String, val message: String)

    // the body lens here is imported as an extension function from the Jackson instance
    val body = Body.auto<NameAndMessage>().toLens()

    return "/echoJson" meta {
        // register the receiving and returning lenses - these also set the content type
        receiving(body)
        returning(OK, body to NameAndMessage("jim", "hello!"))
    } bindContract POST to { req: Request ->
        val input: NameAndMessage = body(req)

        // we can inject the type directly into the response using either...
        Response(OK).with(body of input)

        // ... or the more convenient... (note that json() is an extension function from the Jackson instance)
        Response(OK).json(input)
    }
}

// this route has a callback registered, so can be used when processes have asynchronous updates
// they will be POSTed back to callbackUrl received in the request
fun routeWithCallback(): ContractRoute {

    data class StartProcess(val callbackUrl: Uri)

    val body = Body.auto<StartProcess>().toLens()

    val spec = "/callback" meta {
        summary = "kick off a process with an async callback"

        // register the callback for later updates. The syntax of the callback URL comes
        // from the OpenApi spec
        callback("update") {
            """{${"$"}request.body#/callbackUrl}""" meta {
                receiving(body to StartProcess(Uri.of("http://caller")))
            } bindCallback POST
        }
    } bindContract POST

    val echo: HttpHandler = { request: Request ->
        println(body(request))
        Response(OK)
    }

    return spec to echo
}

// Combine the Routes into a contract and bind to a context, defining a renderer (in this example
// OpenApi/Swagger) and a global security model (in this case an API-Key):
val contract = contract {
    renderer = OpenApi3(ApiInfo("My great API", "v1.0"), Jackson)
    descriptionPath = "/openapi.json"
    security = org.http4k.security.ApiKeySecurity(Query.required("api_key"), { it.isNotEmpty() })

    routes += echo()
    routes += echoJson()
    routes += copy()
    routes += securelyGreet()
    routes += routeWithCallback()
}

val handler: HttpHandler = routes("/api/v1" bind contract)

// by default, the OpenAPI docs live at the root of the contract context, but we can override it..
fun main() {
    println(handler(Request(GET, "https://localhost:10000/api/v1/openapi.json")))
}

```



When launched, OpenApi format documentation (including JSON schema models) can be found at the route of the module.

For a more extended example, see the following example apps: 

- [TDD'd example application](https://github.com/http4k/http4k-by-example)
- [Todo backend (typesafe contract version)](https://github.com/http4k/http4k-contract-todo-backend)

### Naming of JSON Schema models
There are currently 2 options for JSON schema generation. 

1. *OpenApi v2 & v3:* The standard mechanism can be used with any of the supported http4k JSON modules. It generates 
anonymous JSON schema definition names that are then listed in the `schema` section of the OpenApi docs.
```kotlin
    OpenApi3(ApiInfo("title", "1.2", "module description"), Argo)
```
... generates definitions like the following in the schema definitions:
```json
{
  "components": {
    "schemas": {
      "object1283926341": {
        "type": "object",
        "properties": {
          "aString": {
            "type": "string"
          }
        }
      }
    }
  }
}
```

2. *OpenApi v3 only:* By including a supported Auto-JSON marshalling module on the classpath (currently only `http4k-format-jackson`), 
the names of the definitions are generated based on the Kotlin class instances provided to the Contract Route DSL. Note that 
an overloaded OpenApi function automatically provides the default Jackson instance, so we can remove it from the renderer creation:
```kotlin
    OpenApi3(ApiInfo("title", "1.2", "module description"), Jackson)
```
... generates definitions like the following in the schema definitions:
```json
{
   "components":{
      "schemas":{
          "ArbObject": {
            "properties": {
              "uri": {
                "example": "http://foowang",
                "type": "string"
              }
            },
            "example": {
              "uri": "http://foowang"
            },
            "type": "object",
            "required": [
              "uri"
            ]
          }
      }
   }
}
```

### Receiving Binary content with http4k Contracts (application/octet-stream or multipart etc)

With binary attachments, you need to turn ensure that the pre-flight validation does not eat the stream. This is possible by instructing http4k to ignore the incoming body for validation purposes:





```kotlin
package content.ecosystem.http4k.reference.contracts

import org.http4k.contract.PreFlightExtraction
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.routes

val binaryUploadRoute = "/api/document-upload" meta {
    preFlightExtraction = PreFlightExtraction.IgnoreBody
} bindContract POST to { req -> Response(OK) }

```



