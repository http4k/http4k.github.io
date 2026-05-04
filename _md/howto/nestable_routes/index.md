# Routing API (Advanced)


This is a fairly comprehensive example of the core-routing logic available: 

- Individual HTTP endpoints are represented as `HttpHandlers`.
- Binding an `HttpHandler` to a path and HTTP verb yields a `Route`.
- `Routes` can be combined together into a `RoutingHttpHandler`, which is both an `HttpHandler` and a`Router`.
- A `Router` is a selective request handler, which attempts to match a request. If it cannot, processing falls through to the next `Router` in the list.
- Routers can be combined together to form another `HttpHandler`
- Usage of supplied core library `Filters`
- Serving of static content using a `Classpath` resource loader
- Support for Single Page Applications using a `singlePageApp()` block - resources loaded from here are loaded from the underlying `ResourceLoader` or fallback to `/` (and passed to the SPA code)

### Dynamic Paths / Path Variables
As you would expect, http4k allows routes to include dynamic or variable elements in the matching path, and allows you to reference the variable in the Handler. For example:
```
"/book/{title}" bind GET to { req -> 
    Response.invoke(Status.OK).body(GetBookDetails(req.path("title")) 
}
"/author/{name}/latest" bind GET to { req -> 
    Response.invoke(Status.OK).body(GetAllBooks(author = req.path("name")).last()) 
}
```

By default, the variable(s) will match anything. However you can append the variable name with a RegEx expression to limit the matches.
```
// will match /book/978-3-16-148410-0 (i.e. only digits and dashes)
// /book/A%20Confederacy%20Of%20Dunces would return a 404 (Not Found)
"/book/{isbn:[\\d-]+}"

// will NOT match /sales/south or /sales/usa
"/sales/{region:(?:northeast|southeast|west|international)}" 
```

There are no pre-defined types such as `int` or `path` for matching but these are easy to replicate with RegEx's:
- string (excluding slashes) : `[^\\/]+` (note that Kotlin requires backslashes to be escaped, so `\w` in RegEx is expressed as `\\w` in Kotlin)
- int : `\\d+`
- float : `[+-]?([0-9]*[.])?[0-9]+` (this will match basic floats. Does not match exponents, or scientific notation)
- path : `.*`

Note that paths, not strings, will match by default. `"/news/{date}"` will match `www.example.com/news/2018/05/26`, making `request.path("date")` equal to `2018/05/26`. This may be exactly what you want, or it may produce unexpected results, depending on how your URLs are structured.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Code





```kotlin
package content.howto.nestable_routes

import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequestAndResponse
import org.http4k.routing.ResourceLoader.Companion.Classpath
import org.http4k.routing.and
import org.http4k.routing.bind
import org.http4k.routing.header
import org.http4k.routing.headers
import org.http4k.routing.path
import org.http4k.routing.queries
import org.http4k.routing.routes
import org.http4k.routing.singlePageApp
import org.http4k.routing.static

fun main() {
    val routesWithFilter =
        PrintRequestAndResponse().then(
            routes(
                "/get/{name}" bind GET to { req: Request ->
                    Response(OK).body(req.path("name")!!)
                },
                "/post/{name}" bind POST to { Response(OK) }
            )
        )
    println(routesWithFilter(Request(GET, "/get/value")))

    val staticWithFilter = PrintRequestAndResponse()
        .then(static(Classpath("guide/howto/nestable_routes")))

    val app = routes(
        "/bob" bind routesWithFilter,
        "/static" bind staticWithFilter,
        "/pattern/{rest:.*}" bind { req: Request ->
            Response(OK).body(req.path("rest").orEmpty())
        },
        "/rita" bind routes(
            "/delete/{name}" bind DELETE to { Response(OK) },
            "/post/{name}" bind POST to { Response(OK) }
        ),
        "/matching" bind GET to routes(
            header("requiredheader", "somevalue")
                .and(queries("requiredquery")) bind {
                Response(OK).body("matched 2 parameters")
            },
            headers("requiredheader") bind { Response(OK).body("matched 1 parameters") }
        ),
        singlePageApp(Classpath("guide/howto/nestable_routes"))
    )

    println(app(Request(GET, "/bob/get/value")))
    println(app(Request(GET, "/static/someStaticFile.txt")))
    println(app(Request(GET, "/pattern/some/entire/pattern/we/want/to/capture")))
    println(
        app(
            Request(GET, "/matching")
                .header("requiredheader", "somevalue")
                .query("requiredquery", "somevalue")
        )
    )
    println(app(Request(GET, "/matching").header("requiredheader", "somevalue")))
    println(app(Request(GET, "/someSpaResource")))
}

```



For the typesafe contract-style routing, refer to [this](/howto/integrate_with_openapi/) recipe instead.


### Serving Static Content - Security Note

When using `ResourceLoader.Classpath()` to serve static resources in production, be aware that requests for non-existent files can cause unbounded growth in the JVM's internal ClassLoader cache. This can be exploited to cause memory exhaustion. While it's technically possible to implement workarounds such as pre-loading resources or custom caching strategies, the complexity and potential side effects make these approaches impractical for a general-purpose core library.

**For production deployments**, we recommend:
- Using `ResourceLoader.Directory()` instead of `Classpath()`
- Deploying behind a reverse proxy (nginx, Apache, CDN) that handles static assets
- Implementing rate limiting or a WAF to protect against malicious request patterns

`ResourceLoader.Classpath()` is best suited for development and simple use cases where the application is not directly exposed to untrusted traffic.

