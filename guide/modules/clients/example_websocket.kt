package guide.modules.clients

import org.http4k.client.WebsocketClient
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage

fun main(args: Array<String>) {

    // a standard websocket app
    val server = websockets(
        "/bob" bind { ws: Websocket ->
            ws.send(WsMessage("bob"))
            ws.onMessage {
                println("server received: " + it)
                ws.send(it)
            }
        }
    ).asServer(Jetty(8000)).start()

    // blocking client - connection is done on construction
    val blockingClient = WebsocketClient.blocking(Uri.of("ws://localhost:8000/bob"))
    blockingClient.send(WsMessage("hello"))
    blockingClient.received().take(2).forEach { println("blocking client received: " + it) }
    blockingClient.close()

    // non-blocking client - exposes a Websocket interface for attaching listeners,
    // and connection is done on construction, but doesn't block
    val websocket = WebsocketClient.nonBlocking(Uri.of("ws://localhost:8000/bob"))

    Thread.sleep(100)

    websocket.run {
        onMessage {
            println("non-blocking client received:" + it)
        }

        onClose {
            println("non-blocking client closing")
        }

        send(WsMessage("hello"))
    }

    server.stop()
}

