# Ops: OpenFeature



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.56.0.0"))

    implementation("org.http4k:http4k-ops-openfeature")
}
```

### About

This module integrates [OpenFeature](https://openfeature.dev/) feature flag evaluation into the http4k filter chain. A `ServerFilter` evaluates all flags for the incoming request once and stores the snapshot in the `Request`, which typed `OpenFeatureFlag` Lenses can then read in a type-safe manner. An `Http4kOpenFeatureProvider` is also supplied for those wishing to plug http4k into the official OpenFeature SDK as a `FeatureProvider`.

### OpenFeature

The `ServerFilters.PopulateOpenFeatureContext` filter accepts any `OpenFeature` client (typically from `http4k-connect-openfeature`, real or fake) and a function deriving an `EvaluationContext` from the request. Downstream handlers extract values via `OpenFeatureFlag` Lenses - each supporting `required`, `optional`, and `defaulted` variants.





```kotlin
package content.ecosystem.http4k.reference.openfeature

import dev.openfeature.sdk.ImmutableContext
import org.http4k.connect.openfeature.FakeOpenFeature
import org.http4k.connect.openfeature.model.FlagKey
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.PopulateOpenFeatureContext
import org.http4k.filter.ServerFilters
import org.http4k.lens.OpenFeatureFlag

fun main() {

    // a fake OpenFeature provider - substitute the real OpenFeature.Http client
    val fake = FakeOpenFeature().apply {
        this[FlagKey.of("dark-mode")] = true
        rule(FlagKey.of("greeting")) { ctx -> ctx.context["targetingKey"] == "alice" } returns "hello, alice"
    }

    // typed Lenses for reading flags out of a Request
    val darkMode = OpenFeatureFlag.boolean().defaulted("dark-mode", false)
    val greeting = OpenFeatureFlag.string().optional("greeting")

    // the filter evaluates all flags once per request from the supplied EvaluationContext
    val app = ServerFilters.PopulateOpenFeatureContext(fake.client()) { ImmutableContext("alice") }
        .then { req -> Response(OK).body("darkMode=${darkMode(req)}, greeting=${greeting(req)}") }

    println(app(Request(GET, "/")).bodyString())
}

```



