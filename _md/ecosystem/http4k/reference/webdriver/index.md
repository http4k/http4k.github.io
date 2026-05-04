# Testing: WebDriver



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-testing-webdriver")
}
```

### About

A basic Selenium WebDriver API implementation for http4k HttpHandlers, which runs completely out of container (no network) for ultra fast tests, backed by JSoup.

| Feature | Supported | Notes |
|---------|-----------|-------|
| Navigation|yes|simple back/forward/refresh history|
| CSS selectors|yes||
| Link navigation|yes||
| Form field entry and submission|yes||
| Cookie storage|yes|manual expiry management|
| JavaScript|no||
| Alerts|no||
| Screenshots|no||
| Frames|no||
| Multiple windows|no||

Use the API like any other WebDriver implementation, by simply passing your app HttpHandler to construct it.

#### Code





```kotlin
package content.ecosystem.http4k.reference.webdriver

import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.webdriver.Http4kWebDriver
import org.openqa.selenium.By

fun main() {
    val app = routes(
        "/hello" bind GET to {
            Response(OK).body("<html><title>hello</title></html>")
        }
    )

    val driver = Http4kWebDriver(app)

    driver.navigate().to("http://localhost:10000/hello")

    println(driver.title)

    println(driver.findElement(By.tagName("title")))

// prints:
//
// hello
// <title>hello</title>
}

```



[http4k]: https://http4k.org

