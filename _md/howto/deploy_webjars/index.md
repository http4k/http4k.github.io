# Deploy WebJars


### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")

    // for the example...
    implementation("org.webjars:swagger-ui", version = "3.43.0")
}
```

[WebJars](https://www.webjars.org/) is a library to ship pre-packaged Web assets on your classpath by just adding the dependency. The assets are rehoused under the META-INF directory and end up with URLs such as: 

http://localhost:8080/webjars/swagger-ui/3.43.0/index.html

http4k integrates this functionality into the core library and ships with the `webJars()` router plugin to activate. As the plugin is just an `HttpHandler`, the simplest example is just to launch WebJars directly as a Server:





```kotlin
package content.howto.deploy_webjars

import org.http4k.routing.webJars
import org.http4k.server.SunHttp
import org.http4k.server.asServer

val server = webJars().asServer(SunHttp(8080)).start()

```



... or a more standard use-case is to mix it into your application routing as in the example below:

### Code





```kotlin
package content.howto.deploy_webjars

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.webJars
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    // mix the WebJars routing into your app...
    val app = routes(
        "/myGreatRoute" bind GET to { _: Request -> Response(OK) },
        webJars()
    )

    app.asServer(SunHttp(8080)).start()

    // then browse to: http://localhost:8080/webjars/swagger-ui/5.1.3/index.html
}

```



