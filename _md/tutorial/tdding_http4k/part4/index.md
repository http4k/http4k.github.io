# TDDing http4k Part 4: Adding an external dependency


At this point, the separation of the layers starts to become clear:
- The server layer is responsible for taking external configuration and instantiating the app layer.
- The application layer API is only in terms of HTTP transports - it constructs business level abstractions
  which are passed down into to the individual endpoints

The process here is to create fake versions of the dependency which can be tested against through the business interface.
This requires another style of testing, CDCs (Consumer Driven Contracts), to be created. These contract tests ensure that our
interactions with the external service are valid.

### Requirements:
- Results from calculations should be POSTed via HTTP to another "answer recording" service.

### Implementation Notes:
The following process is followed to us to the final state, whilst always allowing us to keep the build green:

1. Determine the HTTP contract required by the Recorder (in this case an HTTP POST to /{answer}
1. Create RecorderCdc and RealRecorderTest and make it pass for the real dependency by implementing the Recorder
1. Create FakeRecorderTest and FakeRecorderHttp and make it pass for the fake. We can now use the Fake to implement our requirement
1. Include the FakeRecorderHttp in the setup of EndToEndTest, starting and stopping the server (even though it's not doing anything)
1. Pass the configuration of the Recorder (baseUri) into the MyMathServer, which uses it to create the recorder HttpHandler
1. Factor AppEnvironment out of the functional tests. This is where all the setup of the functional testing environment will be done
1. Introduce the recorder HttpHandler to MyMathApp, creating a FakeRecorderHttp in the AppEnvironment
1. Alter the AddFunctionalTest and MultiplyFunctionalTest to set the expectations on the interactions recorder in FakeRecorderHttp
1. In MyMathApp, create the Recorder business implementation (Recorder) and pass it to calculate(), then implement the call to record()

### Tests:





```kotlin
package content.tutorial.tdding_http4k.part4

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import content.tutorial.tdding_http4k.part4.Matchers.answerShouldBe
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetHostFrom
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.Random

object Matchers {
    fun Response.answerShouldBe(expected: Int) {
        assertThat(this, hasStatus(OK).and(hasBody(expected.toString())))
    }
}

abstract class RecorderCdc {
    abstract val client: HttpHandler

    @Test
    fun `records answer`() {
        Recorder(client).record(123)
        checkAnswerRecorded()
    }

    open fun checkAnswerRecorded() {}
}

class FakeRecorderHttp : HttpHandler {
    val calls = mutableListOf<Int>()

    private val answer = Path.int().of("answer")

    private val app = CatchLensFailure.then(
        routes(
            "/{answer}" bind POST to { request -> calls.add(answer(request)); Response(ACCEPTED) }
        )
    )

    override fun invoke(request: Request): Response = app(request)
}

class FakeRecorderTest : RecorderCdc() {
    override val client = FakeRecorderHttp()

    override fun checkAnswerRecorded() {
        assertThat(client.calls, equalTo(listOf(123)))
    }
}

@Disabled // this obviously doesn't exist, so we ignore it here
class RealRecorderTest : RecorderCdc() {
    override val client = SetHostFrom(Uri.of("http://realrecorder")).then(OkHttp())
}

class EndToEndTest {
    private val port = Random().nextInt(1000) + 8000
    private val recorderPort = port + 1
    private val client = OkHttp()
    private val recorder = FakeRecorderHttp()
    private val server = MyMathServer(0, Uri.of("http://localhost:$recorderPort"))
    private val recorderServer = recorder.asServer(Jetty(recorderPort))

    @BeforeEach
    fun setup() {
        recorderServer.start()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.stop()
        recorderServer.stop()
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

class AppEnvironment {
    val recorder = FakeRecorderHttp()
    val client = MyMathsApp(recorder)
}

class AddFunctionalTest {
    private val env = AppEnvironment()

    @Test
    fun `adds values together`() {
        env.client(Request(GET, "/add?value=1&value=2")).answerShouldBe(3)
        assertThat(env.recorder.calls, equalTo(listOf(3)))
    }

    @Test
    fun `answer is zero when no values`() {
        env.client(Request(GET, "/add")).answerShouldBe(0)
        assertThat(env.recorder.calls, equalTo(listOf(0)))
    }

    @Test
    fun `bad request when some values are not numbers`() {
        assertThat(
            env.client(Request(GET, "/add?value=1&value=notANumber")),
            hasStatus(BAD_REQUEST)
        )
        assertThat(env.recorder.calls.isEmpty(), equalTo(true))
    }
}

class MultiplyFunctionalTest {
    private val env = AppEnvironment()

    @Test
    fun `products values together`() {
        env.client(Request(GET, "/multiply?value=2&value=4")).answerShouldBe(8)
        assertThat(env.recorder.calls, equalTo(listOf(8)))
    }

    @Test
    fun `answer is zero when no values`() {
        env.client(Request(GET, "/multiply")).answerShouldBe(0)
        assertThat(env.recorder.calls, equalTo(listOf(0)))
    }

    @Test
    fun `bad request when some values are not numbers`() {
        assertThat(
            env.client(Request(GET, "/multiply?value=1&value=notANumber")),
            hasStatus(BAD_REQUEST)
        )
        assertThat(env.recorder.calls.isEmpty(), equalTo(true))
    }
}

```



### Production:





```kotlin
package content.tutorial.tdding_http4k.part4

import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetHostFrom
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer

class Recorder(private val client: HttpHandler) {
    fun record(value: Int) {
        val response = client(Request(POST, "/$value"))
        if (response.status != ACCEPTED) throw RuntimeException("recorder returned ${response.status}")
    }
}

fun MyMathsApp(recorderHttp: HttpHandler): HttpHandler {
    val recorder = Recorder(recorderHttp)
    return CatchLensFailure.then(
        routes(
            "/ping" bind GET to { _: Request -> Response(OK) },
            "/add" bind GET to calculate(recorder) { it.sum() },
            "/multiply" bind GET to calculate(recorder) { it.fold(1) { memo, next -> memo * next } }
        )
    )
}

private fun calculate(recorder: Recorder, fn: (List<Int>) -> Int): (Request) -> Response {
    val values = Query.int().multi.defaulted("value", listOf())

    return { request: Request ->
        val valuesToCalc = values(request)
        val answer = if (valuesToCalc.isEmpty()) 0 else fn(valuesToCalc)
        recorder.record(answer)
        Response(OK).body(answer.toString())
    }
}

fun MyMathServer(port: Int, recorderBaseUri: Uri): Http4kServer =
    MyMathsApp(SetHostFrom(recorderBaseUri).then(OkHttp())).asServer(Jetty(port))

```



