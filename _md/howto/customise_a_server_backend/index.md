# Customise a Server backend


### How to write a custom server implmentation

Whilst the http4k server modules ship with a sensibly configured standard server-backend setup, a lot of projects will
require specialised implementations of the underlying server backend. http4k makes this easy with the `ServerConfig`
interface.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-jetty")
}
```

## Examples

### Secure Jetty

The example below shows a customised Jetty setup which enables HTTPS traffic by reimplementing the `ServerConfig`
interface. The idea is that this single class will encapsulate the usage of the Server platform API behind the http4k
abstraction and provide a simple way to reuse it across different applications.





```kotlin
package content.howto.customise_a_server_backend

import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequestAndResponse
import org.http4k.server.Http4kServer
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import org.http4k.server.toJettyHandler

class SecureJetty(
    private val sslPort: Int,
    private val localKeyStorePath: String,
    private val localKeystorePassword: String,
    private val localKeyManagerPassword: String

) : ServerConfig {
    override fun toServer(http: HttpHandler): Http4kServer {
        val server = Server().apply {
            val https = HttpConfiguration().apply {
                addCustomizer(SecureRequestCustomizer())
            }

            val sslContextFactory = SslContextFactory.Server().apply {
                keyStorePath = localKeyStorePath
                keyStorePassword = localKeystorePassword
                keyManagerPassword = localKeyManagerPassword
            }

            connectors = arrayOf(
                ServerConnector(
                    server,
                    SslConnectionFactory(sslContextFactory, "http/1.1"),
                    HttpConnectionFactory(https)
                ).apply { port = sslPort }
            )

            insertHandler(http.toJettyHandler())
        }

        return object : Http4kServer {
            override fun start(): Http4kServer = apply { server.start() }

            override fun stop(): Http4kServer = apply { server.stop() }

            override fun port(): Int = if (sslPort > 0) sslPort else server.uri.port
        }
    }
}

fun main() {
    PrintRequestAndResponse().then { Response(Status.OK).body("hello from secure jetty!") }
        .asServer(
            SecureJetty(
                sslPort = 9000,
                localKeyStorePath = "keystore.jks",
                localKeystorePassword = "password",
                localKeyManagerPassword = "password"
            )
        ).start()
}

```



### Apache with Local Address Binding

This example shows how to create a custom server backend using Apache with a local address binding.





```kotlin
package content.howto.customise_a_server_backend

import org.http4k.core.HttpHandler
import org.http4k.server.Http4kServer
import org.http4k.server.ServerConfig
import org.http4k.server.ServerConfig.StopMode
import org.http4k.server.defaultBootstrap
import org.http4k.server.stopWith
import java.net.InetAddress
import java.time.Duration

/**
 * Support for creating a custom Apache server with address and canonical hostname.
 */
class CustomApacheServer(
    val port: Int = 8000,
    val address: InetAddress,
    val canonicalHostname: String = "localhost",
    override val stopMode: StopMode = StopMode.Graceful(Duration.ofSeconds(5))
) : ServerConfig {

    override fun toServer(http: HttpHandler): Http4kServer = object : Http4kServer {
        private val server = defaultBootstrap(port, http, canonicalHostname)
            .setLocalAddress(address)
            .create()

        override fun start() = apply { server.start() }

        override fun stop() = apply {
            server.stopWith(stopMode)
        }

        override fun port(): Int = if (port != 0) port else server.localPort
    }
}

```



### Undertow with HTTP 2

This example shows how to create a custom server backend using Undertow with HTTP2 support.





```kotlin
import io.undertow.UndertowOptions.ENABLE_HTTP2
import org.http4k.core.HttpHandler
import org.http4k.server.Http4kServer
import org.http4k.server.PolyServerConfig
import org.http4k.server.ServerConfig.StopMode
import org.http4k.server.ServerConfig.StopMode.Immediate
import org.http4k.server.buildHttp4kUndertowServer
import org.http4k.server.buildUndertowHandlers
import org.http4k.server.defaultUndertowBuilder
import org.http4k.sse.SseHandler
import org.http4k.websocket.WsHandler

/**
 * Custom Undertow server configuration with http 2 support
 */
class CustomUndertowServer(
    private val port: Int = 8000,
    private val enableHttp2: Boolean = false,
    override val stopMode: StopMode = Immediate
) : PolyServerConfig {

    override fun toServer(http: HttpHandler?, ws: WsHandler?, sse: SseHandler?): Http4kServer {
        val (httpHandler, multiProtocolHandler) = buildUndertowHandlers(http, ws, sse, stopMode)

        return defaultUndertowBuilder(port, multiProtocolHandler)
            .setServerOption(ENABLE_HTTP2, enableHttp2)
            .buildHttp4kUndertowServer(httpHandler, stopMode, port)
    }
}

```



