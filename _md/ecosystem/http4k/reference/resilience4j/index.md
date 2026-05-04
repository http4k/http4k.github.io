# Ops: Resilience4J



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-ops-resilience4j")
}
```

### About

This module provides configurable Filters to provide CircuitBreaking, RateLimiting, Retrying and Bulkheading, by integrating with the awesome [Resilience4J](http://resilience4j.github.io/resilience4j/) library.

### Circuit Breaking 
A Circuit Filter detects failures and then Opens for a set period to allow the underlying system to recover.





```kotlin
package content.ecosystem.http4k.reference.resilience4j

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowSynchronizationStrategy.LOCK_FREE
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import java.time.Duration
import java.util.ArrayDeque

// Circuit state transition: CLOSED (ok) -> OPEN (dead) -> HALF_OPEN (test) -> CLOSED (ok)
fun main() {

    // these example responses are queued up to trigger the circuit state changes
    val responses = ArrayDeque<Response>()
    responses.add(Response(INTERNAL_SERVER_ERROR))
    responses.add(Response(OK))
    responses.add(Response(OK))

    // configure the circuit breaker filter here
    val circuitBreaker = CircuitBreaker.of(
        "circuit",
        CircuitBreakerConfig.custom()
            .slidingWindow(2, 2, COUNT_BASED, LOCK_FREE)
            .permittedNumberOfCallsInHalfOpenState(2)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .build()
    )

    val circuited = ResilienceFilters.CircuitBreak(circuitBreaker,
        isError = { r: Response -> !r.status.successful } // this defaults to >= 500
    ).then { responses.removeFirst() }

    println(
        "Result: " + circuited(
            Request(
                GET,
                "/"
            )
        ).status + " Circuit is: " + circuitBreaker.state
    )
    println(
        "Result: " + circuited(
            Request(
                GET,
                "/"
            )
        ).status + " Circuit is: " + circuitBreaker.state
    )
    Thread.sleep(1100) // wait for reset
    println(
        "Result: " + circuited(
            Request(
                GET,
                "/"
            )
        ).status + " Circuit is: " + circuitBreaker.state
    )
    println(
        "Result: " + circuited(
            Request(
                GET,
                "/"
            )
        ).status + " Circuit is: " + circuitBreaker.state
    )
}

```



### Rate Limiting 
A RateLimit Filter monitors the number of requests over a set window.





```kotlin
package content.ecosystem.http4k.reference.resilience4j

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import java.time.Duration

fun main() {

    // configure the rate limiter filter here
    val config = RateLimiterConfig.custom()
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .limitForPeriod(1)
        .timeoutDuration(Duration.ofMillis(10)).build()

    // set up the responses to sleep for a bit
    val rateLimits = ResilienceFilters.RateLimit(RateLimiter.of("ratelimiter", config))
        .then { Response(OK) }

    println(rateLimits(Request(GET, "/")).status)
    println(rateLimits(Request(GET, "/")).status)
}

```



### Retrying 
A Retrying Filter retries requests if a failure is generated.





```kotlin
package content.ecosystem.http4k.reference.resilience4j

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import java.util.ArrayDeque

fun main() {

    // configure the retry filter here, with max attempts and backoff
    val retry = Retry.of("retrying", RetryConfig.custom<RetryConfig>()
        .maxAttempts(3)
        .intervalFunction { attempt: Int -> (attempt * 2).toLong() }
        .build())

    // queued up responses
    val responses = ArrayDeque<Response>()
    responses.add(Response(INTERNAL_SERVER_ERROR))
    responses.add(Response(OK))

    val retrying = ResilienceFilters.RetryFailures(retry,
        isError = { r: Response -> !r.status.successful }
    ).then {
        val response = responses.removeFirst()
        println("trying request, will return " + response.status)
        response
    }

    println(retrying(Request(GET, "/")))
}

```




### Bulkheading 
A Bulkhead Filter limits the amount of parallel calls that can be executed.





```kotlin
package content.ecosystem.http4k.reference.resilience4j

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import java.time.Duration
import kotlin.concurrent.thread

fun main() {

    // configure the Bulkhead filter here
    val config = BulkheadConfig.custom()
        .maxConcurrentCalls(5)
        .maxWaitDuration(Duration.ofMillis(1000))
        .build()

    val bulkheading = ResilienceFilters.Bulkheading(Bulkhead.of("bulkhead", config)).then {
        Thread.sleep(100)
        Response(OK)
    }

    // throw a bunch of requests at the filter - only 5 should pass
    for (it in 1..10) {
        thread {
            println(bulkheading(Request(GET, "/")).status)
        }
    }
}

```



