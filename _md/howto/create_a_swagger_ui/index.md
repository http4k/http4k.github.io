# Redoc and Swagger UI


Http4k makes it easy to include Swagger UI or Redoc in your application.
These UIs can often replace traditional hand-written documentation for API consumers to learn your API,
and can even serve as useful debugging tools.

## Build the OpenAPI spec

Swagger UI and Redoc both require an **OpenApi** v2 or v3 description to function.
Http4k can generate a description for your API with the `http4k-api-openapi` module,
but any hand-crafted or external description can be used as well.

For more detail on generating **OpenAPI** descriptions, see:

- [Http4k Reference: Contracts](/ecosystem/http4k/reference/contracts/)
- [Integrate with OpenAPI](/howto/integrate_with_openapi/)

### Example 

This simple description will be used for all examples in this guide:





```kotlin
package content.howto.create_a_swagger_ui

import org.http4k.contract.contract
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler

fun createContractHandler(descriptionPath: String): RoutingHttpHandler {
    val greetingLens = Body.string(ContentType.TEXT_PLAIN).toLens()

    // Define a single http route for our contract
    val helloHandler = "/v1/hello" meta {
        operationId = "v1Hello"
        summary = "Say Hello"
        returning(Status.OK, greetingLens to "Sample Greeting")
    } bindContract Method.GET to { _: Request ->
        Response(Status.OK).with(greetingLens of "HI!")
    }

    // Define a contract, and render an OpenApi 3 spec at the given path
    return contract {
        routes += helloHandler
        renderer = OpenApi3(
            ApiInfo("Hello Server - Developer UI", "99.3.4")
        )
        this.descriptionPath = descriptionPath
    }
}

```



## Build the UI

The `http4-contract` module includes functions to configure and serve Swagger UI, Redoc, or both.
These "lite" UIs are thin; meaning most of the assets are pulled from an external Public CDN.

### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-api-openapi")
}
```

### Example 





```kotlin
package content.howto.create_a_swagger_ui

import org.http4k.contract.ui.redocLite
import org.http4k.contract.ui.swaggerUiLite
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val http = routes(
        // bind the API and OpenApi description off of root
        createContractHandler("/openapi.json"),

        // bind Swagger UI to the root path
        swaggerUiLite {
            url = "/openapi.json"
            pageTitle = "Hello Server - Swagger UI"
            persistAuthorization = true
        },

        // Bind Redoc to another path
        "/redoc" bind redocLite {
            url = "/openapi.json"
            pageTitle = "Hello Server - Redoc"
            options["disable-search"] = "true"
        }
    )

    // run the server.  The default UI is available at http://localhost:8080
    http.asServer(SunHttp(8080))
        .start()
        .block()
}

```



## Bundle the UI with Webjars

The "lite" UIs included in the `http4k-api-openapi` module are great for serverless APIs, where binary size is a major concern.
For more control over the assets, http4k has optional modules to bundle the assets into your application.

### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-api-ui-swagger")
    implementation("org.http4k:http4k-api-ui-redoc")
}
```

You can pick and choose whether you want Redoc, Swagger UI, or both bundled with your application.

### Example 





```kotlin
package content.howto.create_a_swagger_ui

import org.http4k.contract.ui.redoc.redocWebjar
import org.http4k.contract.ui.swagger.swaggerUiWebjar
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val http = routes(
        // bind the API and OpenApi description off of root
        createContractHandler("/openapi.json"),

        // Bind Redoc to the root path
        redocWebjar {
            url = "/openapi.json"
            pageTitle = "Hello Server - WebJar"
            options["disable-search"] = "true"
        },

        // Bind Swagger UI to another path
        "/swagger" bind swaggerUiWebjar {
            url = "/openapi.json"
            pageTitle = "Hello Server"
            displayOperationId = true
        }
    )

    // run the server.  The default UI is available at http://localhost:8080
    http.asServer(SunHttp(8080))
        .start()
        .block()
}



```



