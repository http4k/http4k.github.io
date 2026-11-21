# Nanoservices - The Power of Composition


http4k is a small library with a zero dependencies (apart from Kotlin StdLib), but what really makes it shine is the power afforded by the combination of the "Server as a Function" concepts of `HttpHandler` and `Filter`. 

Skeptical? We would be disappointed if you weren't! Hence, we decided to prove the types of things that can be accomplished with the APIs provided by http4k and a little ingenuity.

For each of the examples below, there is a fully formed http4k application declared inside a function, and the scaffolding to demonstrating it working in an accompanying `main()` using one of the swappable server backends. Even better, each of app's code (excluding import statements 🙂 ) fits in a single Tweet.

### 1. Build a simple proxy 
Requires: `http4k-core`

This simple proxy converts HTTP requests to HTTPS. Because of the symmetrical server/client `HttpHandler` signature, we can simply pipe an HTTP Client onto a server, then add a `ProxyHost` filter to do the protocol conversion.





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.lang.System.setProperty

fun `simple proxy`() =
    JavaHttpClient()
        .asServer(SunHttp())
        .start()

fun main() {
    setProperty("http.proxyHost", "localhost")
    setProperty("http.proxyPort", "8000")
    setProperty("http.nonProxyHosts", "localhost")

    `simple proxy`().use {
        println(JavaHttpClient()(Request(GET, "http://github.com/")))
    }
}

```



<hr/>

### 2. Report latency through a proxy 
Requires: `http4k-core`

Building on the Simple Proxy example, we can simply layer on extra filters to add features to the proxy, in this case reporting the latency of each call.





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.RequestFilters.ProxyHost
import org.http4k.filter.RequestFilters.ProxyProtocolMode.Https
import org.http4k.filter.ResponseFilters.ReportRouteLatency
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.lang.System.setProperty

fun `latency reporting proxy`() =
    ProxyHost(Https)
        .then(ReportRouteLatency { req, ms -> println("$req took $ms") })
        .then(JavaHttpClient())
        .asServer(SunHttp())
        .start()

fun main() {
    setProperty("http.proxyHost", "localhost")
    setProperty("http.proxyPort", "8000")
    setProperty("http.nonProxyHosts", "localhost")

    `latency reporting proxy`().use {
        JavaHttpClient()(Request(GET, "http://github.com/"))
    }
}

```



<hr/>

### 3. Build a Wireshark to sniff inter-service traffic 
Requires: `http4k-core`

Applying a `DebuggingFilter` to the HTTP calls in a proxy dumps the entire contents out to `StdOut` (or other stream).





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequestAndResponse
import org.http4k.filter.RequestFilters.ProxyHost
import org.http4k.filter.RequestFilters.ProxyProtocolMode.Https
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.lang.System.setProperty

fun `wire sniffing proxy`() =
    ProxyHost(Https)
        .then(PrintRequestAndResponse())
        .then(JavaHttpClient())
        .asServer(SunHttp())
        .start()

fun main() {
    setProperty("http.proxyHost", "localhost")
    setProperty("http.proxyPort", "8000")
    setProperty("http.nonProxyHosts", "localhost")

    `wire sniffing proxy`().use {
        JavaHttpClient()(Request(GET, "http://github.com/http4k"))
    }
}

```



<hr/>

### 4. Build a ticking Websocket clock 
Requires: `http4k-core`, `http4k-server-netty`

Like HTTP handlers, Websockets in http4k can be modelled as simple functions that can be mounted onto a Server, or combined with path patterns if required.





```kotlin
package content.news.nanoservices

import org.http4k.client.WebsocketClient
import org.http4k.core.Uri
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import java.time.Instant

fun `ticking websocket clock`() =
    { ws: Websocket ->
        while (true) {
            ws.send(WsMessage(Instant.now().toString()))
            Thread.sleep(1000)
        }
    }.asServer(Netty()).start()

fun main() {
    `ticking websocket clock`()
    WebsocketClient.nonBlocking(Uri.of("http://localhost:8000")).onMessage { println(it) }
}

```



<hr/>

### 5. Build a web cache 
Requires: `http4k-core`, `http4k-server-ktorcio`

Recording all traffic to disk can be achieved by just creating a `ReadWriteCache` and then adding a couple of pre-supplied Filters to a proxy. When running this example you can see that only the first request is audited.





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.RequestFilters.ProxyHost
import org.http4k.filter.RequestFilters.ProxyProtocolMode.Https
import org.http4k.filter.ResponseFilters.ReportHttpTransaction
import org.http4k.filter.TrafficFilters.RecordTo
import org.http4k.filter.TrafficFilters.ServeCachedFrom
import org.http4k.server.Http4kServer
import org.http4k.server.KtorCIO
import org.http4k.server.asServer
import org.http4k.traffic.ReadWriteCache
import java.io.File

fun `disk cache!`(dir: String): Http4kServer {
    val cache = ReadWriteCache.Disk(dir)
    return ProxyHost(Https)
        .then(RecordTo(cache))
        .then(ServeCachedFrom(cache))
        .then(ReportHttpTransaction { println(it.request.uri) })
        .then(JavaHttpClient())
        .asServer(KtorCIO())
        .start()
}

fun main() {
    System.setProperty("http.proxyHost", "localhost")
    System.setProperty("http.proxyPort", "8000")
    System.setProperty("http.nonProxyHosts", "localhost")

    val client = JavaHttpClient()
    val dir = "store"
    File(dir).deleteRecursively()

    `disk cache!`(dir).use {
        val request = Request(GET, "http://api.github.com/users/http4k")

        println(client(request).bodyString())

        // this request is served from the cache, so will not generate a call
        println(client(request).bodyString())
    }
}

```



<hr/>

### 6. Record all traffic to disk and replay it later 
Requires: `http4k-core`

This example contains two apps. The first is a proxy which captures streams of traffic and records it to a directory on disk. The second app is configured to replay the requests from that disk store at the original server. This kind of traffic capture/replay is very useful for load testing or for tracking down hard-to-diagnose bugs - and it's easy to write other other stores such as an S3 bucket etc.





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.RequestFilters.ProxyHost
import org.http4k.filter.RequestFilters.ProxyProtocolMode.Https
import org.http4k.filter.TrafficFilters.RecordTo
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.traffic.ReadWriteStream.Companion.Disk
import java.lang.System.setProperty

fun `recording traffic to disk proxy`() =
    ProxyHost(Https)
        .then(RecordTo(Disk("store")))
        .then(JavaHttpClient())
        .asServer(SunHttp())
        .start()

fun `replay previously recorded traffic from a disk store`() =
    JavaHttpClient().let { client ->
        Disk("store").requests()
            .forEach {
                println(it)
                client(it)
            }
    }

fun main() {
    setProperty("http.proxyHost", "localhost")
    setProperty("http.proxyPort", "8000")
    setProperty("http.nonProxyHosts", "localhost")

    `recording traffic to disk proxy`().use {
        JavaHttpClient()(Request(GET, "http://github.com/"))
        JavaHttpClient()(Request(GET, "http://github.com/http4k"))
        JavaHttpClient()(Request(GET, "http://github.com/http4k/http4k"))
    }

    `replay previously recorded traffic from a disk store`()
}

```



<hr/>

### 7. Watch your FS for file changes 
Requires: `http4k-core`, `http4k-server-undertow`

Back to Websockets, we can watch the file system for changes and subscribe to the event feed.





```kotlin
package content.news.nanoservices

import org.http4k.client.WebsocketClient
import org.http4k.core.Uri
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import java.nio.file.FileSystems.getDefault
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

fun `file watcher`() =
    { ws: Websocket ->
        val w = getDefault().newWatchService()
        Paths.get("").register(w, ENTRY_MODIFY)
        val key = w.take()
        while (true)
            key.pollEvents()
                .forEach { ws.send(WsMessage(it.context().toString())) }
    }.asServer(Jetty()).start()

fun main() {
    `file watcher`()
    WebsocketClient.nonBlocking(Uri.of("http://localhost:8000")).onMessage { println(it) }
}

```



<hr/>

### 8. Serve static files from disk 
Requires: `http4k-core`, `http4k-server-undertow`

Longer than the Python `SimpleHttpServer`, but still pretty small!





```kotlin
package content.news.nanoservices

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.routing.ResourceLoader.Companion.Directory
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.server.asServer

fun `static file server`() =
    static(Directory())
        .asServer(Undertow())
        .start()

fun main() {
    `static file server`().use {
        // by default, static servers will only serve known file types,
        // or those registered on construction
        println(JavaHttpClient()(Request(GET, "http://localhost:8000/version.json")))
    }
}

```



<hr/>

### 9. Build your own ChaosMonkey 
Requires: `http4k-core`, `http4k-testing-chaos`

As per the [Principles of Chaos], this proxy adds Chaotic behaviour to a remote service, which is useful for modelling how a system might behave under various failure modes. Chaos can be dynamically injected via an `OpenApi` documented set of RPC endpoints.





```kotlin
package content.news.nanoservices

import org.http4k.chaos.ChaosBehaviours.Latency
import org.http4k.chaos.ChaosEngine
import org.http4k.chaos.withChaosApi
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.RequestFilters.ProxyHost
import org.http4k.filter.RequestFilters.ProxyProtocolMode.Https
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.lang.System.setProperty

fun `latency injection proxy (between 100ms-500ms)`() =
    ProxyHost(Https)
        .then(JavaHttpClient())
        .withChaosApi(ChaosEngine(Latency()).enable())
        .asServer(SunHttp())
        .start()

fun main() {
    setProperty("http.proxyHost", "localhost")
    setProperty("http.proxyPort", "8000")
    setProperty("http.nonProxyHosts", "localhost")

    `latency injection proxy (between 100ms-500ms)`().use {
        println(JavaHttpClient()(Request(POST, "http://localhost:8000/chaos/activate")))
        println(JavaHttpClient()(Request(GET, "http://github.com/")).header("X-http4k-chaos"))
    }
}

```



<hr/>

### 10. Build a remote terminal! 
Requires: `http4k-core`, `http4k-server-netty`

Use Websockets to remote control a terminal!* Run the example and just type commands into the prompt to have them magicked to the server backend

<sub>*Obviously this is, in general, a really (really) bad idea.</sub>





```kotlin
package content.news.nanoservices

import org.http4k.client.WebsocketClient
import org.http4k.core.Uri
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import java.lang.Runtime.getRuntime
import java.util.Scanner

@Suppress("DEPRECATION")
fun `websocket terminal`() =
    { ws: Websocket ->
        ws.onMessage {
            val text = getRuntime().exec(it.bodyString())
                .inputStream
                .reader()
                .readText()

            ws.send(WsMessage(text))
        }
    }.asServer(Netty()).start()

fun main() {
    `websocket terminal`()

    val ws = WebsocketClient.nonBlocking(Uri.of("http://localhost:8000"))
    ws.onMessage { println(it.bodyString()) }

    val scan = Scanner(System.`in`)
    while (true) {
        ws.send(WsMessage(scan.nextLine()))
    }
}

```



<hr/>

Obviously we haven't thought of everything here. We'd love to hear your ideas about other clever uses of the http4k building blocks, or to take PRs to integrate them into the library for wider use. You can get in touch through [GitHub](http://github.com/http4k) or the usual [channels].

[github]: http://github.com/daviddenton
[http4k]: https://http4k.org
[Principles of Chaos](https://principlesofchaos.org/)
[channels]: https://http4k.org/support

