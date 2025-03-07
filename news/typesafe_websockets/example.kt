package content.news.typesafe_websockets

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.routing.bindHttp
import org.http4k.routing.poly
import org.http4k.routing.routes
import org.http4k.routing.websocket.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse

// in json, this looks like: {"value": 123, "currency: "EUR" }
data class Money(val value: Int, val currency: String)

fun main() {

    // we use the Lens API to convert between the WsMessage and the Money instance, and to
    // dynamically bind the "name" from the path
    val moneyLens = WsMessage.auto<Money>().toLens()
    val nameLens = Path.of("name")

    // the routing API is virtually identical to the standard http4k http routing API.
    // on connection, the bound WsConsumer is called with the Websocket instance
    val ws = websockets(
        "/hello" bind websockets(
            "/{name}" bind { req: Request ->
                WsResponse { ws: Websocket ->
                    val name = nameLens(req)
                    ws.onMessage {
                        val received = moneyLens(it)
                        ws.send(moneyLens(received))
                    }
                    ws.onClose { println("closed") }
                    ws.send(WsMessage("hello $name"))
                }
            }
        )
    )

    val http = routes("all:{.+}" bindHttp GET to { _: Request ->
        Response(OK).body("hiya world")
    })

    // the poly-handler can serve http, sse and ws protocols.
    poly(http, ws).asServer(Jetty(9000)).start().block()
}
