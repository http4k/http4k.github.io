# API: JSON-RPC



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-api-jsonrpc")
}
```

### About

Support for JSON-RPC handlers, with support for both manual and automatic marshalling modes.

Each service method "name" is bound to a particular endpoint function and then the entire API is 
exposed as a standard http4k `HttpHandler`, so it can be composed with other HttpHandlers and Filters.

A specialised ErrorHandler can also be assigned to the RPC contract.

Note that in order to activate JSON RPC, you need to import one of the supported JSON modules.

#### Code





```kotlin
package content.ecosystem.http4k.reference.jsonrpc

import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.format.Jackson
import org.http4k.format.Json
import org.http4k.jsonrpc.ErrorHandler
import org.http4k.jsonrpc.ErrorMessage
import org.http4k.jsonrpc.JsonRpc
import java.util.concurrent.atomic.AtomicInteger

class Counter {
    private val value = AtomicInteger()

    fun increment(amount: Increment): Int = when {
        amount.value == 10 -> throw RuntimeException("Boom!")
        amount.value < 0 -> throw NegativeIncrementException()
        else -> value.addAndGet(amount.value)
    }

    fun currentValue(): Int = value.get()

    data class Increment(val value: Int)

    class NegativeIncrementException : RuntimeException("negative increment not allowed")
}

object CounterErrorHandler : ErrorHandler {
    override fun invoke(error: Throwable): ErrorMessage? = when (error) {
        is Counter.NegativeIncrementException -> NegativeIncrementExceptionMessage()
        else -> null
    }

    private class NegativeIncrementExceptionMessage :
        ErrorMessage(1, "Increment by negative") {
        override fun <NODE> data(json: Json<NODE>) =
            json.string("cannot increment counter by negative")
    }
}

fun main() {
    val counter = Counter()

    val rpcHandler: HttpHandler = JsonRpc.auto(Jackson, CounterErrorHandler) {
        method("increment", handler(counter::increment))
        method("current", handler(counter::currentValue))
    }

    fun runRequest(s: String) {
        println(
            rpcHandler(
                Request(POST, "/rpc")
                    .header("Content-Type", "application/json")
                    .body(s)
            )
        )
    }

    val increment =
        """ {"jsonrpc": "2.0", "method": "increment", "params": {"value": 3}, "id": 1} """
    runRequest(increment)

    val incrementInvalid =
        """ {"jsonrpc": "2.0", "method": "increment", "params": {"value": -1}, "id": 2} """
    runRequest(incrementInvalid)

    val current = """ {"jsonrpc": "2.0", "method": "current", "id": 3} """
    runRequest(current)
}

```



