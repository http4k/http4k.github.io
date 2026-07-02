# Testing: WebDriver (Datastar)



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.54.0.0"))

    implementation("org.http4k:http4k-testing-webdriver-datastar")
}
```

### About

A [Datastar](/ecosystem/http4k/reference/datastar/)-aware extension of the
[http4k-testing-webdriver](/ecosystem/http4k/reference/webdriver/) module. It runs completely out of container (no
network, no browser) for ultra fast tests, while simulating just enough of a Datastar v1 browser to drive a reactive
app end-to-end with the standard Selenium WebDriver API.

On top of the base WebDriver it understands the Datastar runtime:

| Feature | Supported | Notes |
|---------|-----------|-------|
| `data-on-*` event directives | yes | e.g. `data-on-click`, `data-on-load` |
| `@get` / `@post` actions | yes | issued back to your `HttpHandler` |
| SSE patches | yes | `datastar-patch-elements` and `datastar-patch-signals` |
| DOM morphing | yes | applies the configured `MorphMode` |
| Signals / reactivity | yes | client-side signal store with reactive bindings |

When an event fires, the driver makes the corresponding request to your app, reads the returned Server-Sent Events, and
applies the patches to its in-memory DOM - exactly as the real Datastar library would in a browser. This lets you assert
on the resulting page without any JavaScript engine.

Use it like any other WebDriver implementation, by passing your app `HttpHandler` to construct it.

#### Code





```kotlin
package content.ecosystem.http4k.reference.webdriverdatastar

import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.datastar.DatastarEvent.PatchElements
import org.http4k.datastar.Selector
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.webdriver.datastar.DatastarWebDriver
import org.openqa.selenium.By

fun main() {
    val home = """
        <html><body>
            <button id="btn" data-on-click="@get('/clicked')">go</button>
            <div id="out">before</div>
        </body></html>
    """.trimIndent()

    val app = routes(
        "/" bind GET to { Response(OK).body(home) },
        "/clicked" bind GET to {
            // reply with an SSE patch that morphs the #out element
            val patch = PatchElements("<div id=\"out\">after</div>", selector = Selector.of("#out"))
            Response(OK).body(patch.toSseEvent().toMessage())
        }
    )

    val driver = DatastarWebDriver(app)

    driver.get("/")

    driver.findElement(By.id("btn")).click()

    println(driver.findElement(By.id("out")).text)

// prints:
//
// after
}

```



[http4k]: https://http4k.org
[Datastar]: https://data-star.dev

