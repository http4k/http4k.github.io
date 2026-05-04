# Web: HTMX


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-web-htmx")

    implementation("org.http4k:http4k-template-handlebars") // Handlebars
}
```

### About

Utilities to support [htmx](https://htmx.org) development. Includes the htmx and hyperscript Webjar support and a set of classes/functions to ease development of htmx-powered applications.

### Code





```kotlin
package content.ecosystem.http4k.reference.htmx

import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.isHtmx
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.htmxWebjars
import org.http4k.routing.orElse
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import java.util.Date

fun main() {
    val view = Body.viewModel(HandlebarsTemplates().CachingClasspath(), TEXT_HTML).toLens()

    val app = routes(
        htmxWebjars(),
        "/" bind GET to routes(
            // Respond to htmx powered requests
            Request.isHtmx bind { Response(OK).with(view of Time(Date())) },
            // Standard requests get routed to here
            orElse bind { Response(OK).with(view of Index) }
        )
    )

    app.asServer(SunHttp(9000)).start()

    System.err.println("htmx server started at http://localhost:9000")
}

// We are using Handlebars to power the templates used by this app - see the *.hbs files
data object Index : ViewModel
data class Time(val date: Date) : ViewModel

```



