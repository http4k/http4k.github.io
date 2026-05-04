# Ops: Failsafe



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-ops-failsafe")
}
```

### About

This module provides a configurable Filter to provide fault tolerance (CircuitBreaking, RateLimiting, Retrying, Bulkheading, Timeouts etc.),
by integrating with the [Failsafe](https://failsafe.dev/) library.

### Basic example 

Here's an example that uses BulkHeading to demonstrate how easy it is to use the filter with configured Failsafe policies.





```kotlin
package content.ecosystem.http4k.reference.failsafe

import dev.failsafe.Bulkhead
import dev.failsafe.Failsafe
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.FailsafeFilter
import kotlin.concurrent.thread

fun main() {
    // Configure a Failsafe policy
    val failsafeExecutor = Failsafe.with(
        Bulkhead.of<Response>(5)
    )

    // Use the filter in a filter chain
    val app = FailsafeFilter(failsafeExecutor).then {
        Thread.sleep(100)
        Response(OK)
    }

    // Throw a bunch of requests at the filter - only 5 should pass
    for (it in 1..10) {
        thread {
            println(app(Request(GET, "/")).status)
        }
    }
}

```



### Example of using multiple policies 

Using multiple Failsafe policies in the filter is just as easy, as the following example shows.





```kotlin
package content.ecosystem.http4k.reference.failsafe

import dev.failsafe.CircuitBreaker
import dev.failsafe.Failsafe
import dev.failsafe.Fallback
import dev.failsafe.RetryPolicy
import dev.failsafe.Timeout
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.FailsafeFilter
import java.time.Duration
import kotlin.random.Random

fun main() {
    // Configure multiple Failsafe policies
    val failsafeExecutor = Failsafe.with(
        Fallback.of(Response(OK).body("Fallback")),
        RetryPolicy.builder<Response>()
            .withMaxAttempts(2)
            .onRetry { println("Retrying") }
            .handleResultIf { !it.status.successful }
            .build(),
        CircuitBreaker.builder<Response>()
            .withFailureThreshold(3)
            .withDelay(Duration.ofSeconds(3))
            .onOpen { println("Circuit open") }
            .onHalfOpen { println("Circuit half open") }
            .onClose { println("Circuit closed") }
            .handleResultIf { it.status.serverError }
            .build(),
        Timeout.of(Duration.ofMillis(100))
    )

    // We then create a very unstable client using the filter
    val client = FailsafeFilter(failsafeExecutor).then {
        when (Random.nextInt(0, 7)) {
            0 -> Response(INTERNAL_SERVER_ERROR).body("Oh no!")
            1, 2 -> {
                Thread.sleep(200)
                Response(OK).body("Slow!")
            }
            else -> Response(OK).body("All good!")
        }.also { println("Call result: ${it.bodyString()}") }
    }

    // Throw a bunch of request at the filter - some will fail and be retried until
    // the circuit breaker opens and the fallback value will be used after that.
    repeat(1000) {
        client(Request(GET, "/"))
        Thread.sleep(1000)
    }
}

```



