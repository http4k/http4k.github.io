# Request Contexts


It is possible to attach objects to a request whilst it is being passed down through the layers of an application.

The system uses the http4k Lens system to attach arbitrary data to the request, which can then be accessed by any part of the application that has access to the request. Typically this is used for attaching things like request IDs, authorised user principals, or other context-specific data. Under the covers this is just a non-typesafe map attached to an implementation of `Request`, but the Lens system provides a typesafe way to interact with it.

The basic concept is that requests and responses hold a bag of state. This state can be modified in Filters and then 
that state accessed inside other Filters or the terminating HttpHandler. The data can be manipulated using the lens mechanism (`RequestLens`) available in the `http4k-core` module.

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

#### Lens-based keys 





```kotlin
package content.howto.attach_context_to_a_request

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.NoOp
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.lens.RequestKey
import org.http4k.lens.RequestLens

fun main() {
    data class SharedState(val message: String)

    fun AddState(key: RequestLens<SharedState>) = Filter { next ->
        {
            // "modify" the request like any other Lens
            next(it.with(key of SharedState("hello there")))
        }
    }

    fun PrintState(key: RequestLens<SharedState>): HttpHandler = { request ->
        // we can just extract the Lens state from the request like any other standard Lens
        println(key(request))
        Response(OK)
    }

    // this Lens is the key we use to set and get the type-safe state. By using this, we gain
    // typesafety and the guarantee that there will be no clash of keys.
    // RequestLens can be required or optional, as per the standard Lens mechanism.
    val key = RequestKey.required<SharedState>("sharedState")

    // The Filter modifies the bag of state.
    // The handler just prints out the state.
    val app = AddState(key)
        .then(PrintState(key))

    app(Request(GET, "/hello"))
}

```



