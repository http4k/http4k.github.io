# TDDing http4k Part 1: Building a walking skeleton


Until we have an application that can be deployed, we cannot create any business value. The Walking Skeleton
model dictates that putting the most trivial endpoint into a production environment will prove our deployment
pipeline is sound, and helps to set the direction for the testing strategy that we will use going forward.

We start with in ICT (In-Container-Test), which have the job of testing server-level concerns such as monitoring,
documentation, and checking in a high-level way that the business endpoints are wired correctly.

### Requirements:
- The service can be pinged over HTTP to prove that is still alive.

### Tests:





```kotlin
package content.tutorial.tdding_http4k.part1

import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.client.OkHttp
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
    fun `responds to ping`() {
        assertThat(
            client(Request(GET, "http://localhost:${server.port()}/ping")),
            hasStatus(OK)
        )
    }
}

```



### Production:





```kotlin
package content.tutorial.tdding_http4k.part1

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun MyMathServer(port: Int): Http4kServer =
    { _: Request -> Response(OK) }.asServer(Jetty(port))

```



Next: [Part 2: Adding an endpoint](/tutorial/tdding_http4k/part2/)

