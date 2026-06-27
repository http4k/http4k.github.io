# TDDing http4k Part 3: Adding another endpoint


### Requirements:
- Implement a "multiply" service, which will find the product of a number of integer values.

### Tests:





```kotlin
package content.tutorial.tdding_http4k.part3

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import content.tutorial.tdding_http4k.part3.Matchers.answerShouldBe
import org.http4k.client.OkHttp
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

object Matchers {
    fun Response.answerShouldBe(expected: Int) {
        assertThat(this, hasStatus(OK).and(hasBody(expected.toString())))
    }
}

class EndToEndTest {
    private val client = OkHttp()
    private val server = MyMathServer(0)

    @BeforeEach
    fun setup() {
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.stop()
    }

    @Test
    fun `all endpoints are mounted correctly`() {
        assertThat(
            client(Request(GET, "http://localhost:${server.port()}/ping")),
            hasStatus(OK)
        )
        client(
            Request(
                GET,
                "http://localhost:${server.port()}/add?value=1&value=2"
            )
        ).answerShouldBe(3)
        client(
            Request(
                GET,
                "http://localhost:${server.port()}/multiply?value=2&value=4"
            )
        ).answerShouldBe(8)
    }
}

class AddFunctionalTest {
    private val client = MyMathsApp()

    @Test
    fun `adds values together`() {
        client(Request(GET, "/add?value=1&value=2")).answerShouldBe(3)
    }

    @Test
    fun `answer is zero when no values`() {
        client(Request(GET, "/add")).answerShouldBe(0)
    }

    @Test
    fun `bad request when some values are not numbers`() {
        assertThat(
            client(Request(GET, "/add?value=1&value=notANumber")),
            hasStatus(BAD_REQUEST)
        )
    }
}

class MultiplyFunctionalTest {
    private val client = MyMathsApp()

    @Test
    fun `products values together`() {
        client(Request(GET, "/multiply?value=2&value=4")).answerShouldBe(8)
    }

    @Test
    fun `answer is zero when no values`() {
        client(Request(GET, "/multiply")).answerShouldBe(0)
    }

    @Test
    fun `bad request when some values are not numbers`() {
        assertThat(
            client(Request(GET, "/multiply?value=1&value=notANumber")),
            hasStatus(BAD_REQUEST)
        )
    }
}

```



### Production:





```kotlin
package content.tutorial.tdding_http4k.part3

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun MyMathServer(port: Int): Http4kServer = MyMathsApp().asServer(Jetty(port))

fun MyMathsApp(): HttpHandler = CatchLensFailure.then(
    routes(
        "/ping" bind GET to { _: Request -> Response(OK) },
        "/add" bind GET to calculate { it.sum() },
        "/multiply" bind GET to calculate { it.fold(1) { memo, next -> memo * next } }
    )
)

private fun calculate(fn: (List<Int>) -> Int): (Request) -> Response {
    val values = Query.int().multi.defaulted("value", listOf())

    return { request: Request ->
        val valuesToCalc = values(request)
        val answer = if (valuesToCalc.isEmpty()) 0 else fn(valuesToCalc)
        Response(OK).body(answer.toString())
    }
}

```



Next: [Part 4: Adding an external dependency](/tutorial/tdding_http4k/part4/)

