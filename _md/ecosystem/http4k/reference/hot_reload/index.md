# Tools: Hot Reload


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k.pro:http4k-tools-hotreload")
}
```

### About

The `http4k-tools-hotreload` module provides a simple mechanism to dynamically reload the application when the source
code changes. This is extremely useful for web development when used in combination with a templating system such as
Handlebars, as it allows you to see live changes in both the application logic and the templates without having to
restart the application.

The reloading mechanism works implementing a class which implements the `HotReloadable` interface to create a fresh
instance of your application (`HttpHandler` or `PolyHandler`), and using a reference to this class to the
`HotReloadServer` class which acts like a standard `Http4kServer`.

Additionally, the `HotReloadServer` class intercepts HTML responses and injects a JavaScript `EventSource` that will 
automatically reload a page in the browser when the server restarts.

Note that by default the `HotReloadServer` will listen on port 8000 and uses the `SunHttp` server as it is both lightweight, has no dependencies, and starts incredibly quickly. For apps requiring SSE or Websockets, we recommend Jetty as it has the most reliable behaviour. 
Also note that some server implementations do not support hot-reloading correctly due to quirks around the stop/start loop and reloading, but as http4k decouples the HTTP runtime from the application logic, this should not cause an issue for using this functionality.

#### Example

Once the server is started, open a browser at any page, edit the code (or template), and see
the page refresh.

A production application (lives in `src/main`)




```kotlin
package content.ecosystem.http4k.reference.hot_reload

import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.contentType
import org.http4k.routing.bind
import org.http4k.routing.routes

fun MyApp() = routes("/" bind Method.GET to {
    // standard HTML response which will be reloaded when sources file are changed
    Response(Status.OK)
        .contentType(ContentType.TEXT_HTML)
        .body(EDIT_THIS_AND_SEE_THE_RELOAD_HAPPEN_IN_THE_BROWSER)
})

private val EDIT_THIS_AND_SEE_THE_RELOAD_HAPPEN_IN_THE_BROWSER = """
<body>
<h1>Hello, Hot-Reload World!</h1>
<h2>Edit me</h2>
</body>
"""

```



The Hot Reload is setup in `src/test`  by implementing the `HotReloadable` interface and 
then configuring the `HotReloadServer`.




```kotlin
import content.ecosystem.http4k.reference.hot_reload.MyApp

import org.http4k.core.HttpHandler
import org.http4k.hotreload.HotReloadServer
import org.http4k.hotreload.HotReloadable

// This class is needed to create a HotReloadable instance of the HttpHandler
class ReloadableHttpApp : HotReloadable<HttpHandler> {
    override fun create() = MyApp()
}

fun main() {
    // Start the HotReloadServer - configuration is available for non-standard setups
    HotReloadServer.http<ReloadableHttpApp>().start()
}

```





