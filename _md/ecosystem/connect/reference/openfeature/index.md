# OpenFeature


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.56.0.0"))

    implementation("org.http4k:http4k-connect-openfeature")
}
```

The OpenFeature connector is a client speaking the standard [OpenFeature Remote Evaluation Protocol (OFREP)](https://openfeature.dev/specification/appendix-c), and provides the following Actions:

- Evaluate Flag
- Evaluate All Flags

It also ships with a `Cached` wrapper that caches evaluation results in a pluggable `Storage` for a configurable TTL.

### Example usage





```kotlin
package content.ecosystem.connect.reference.openfeature

import org.http4k.client.JavaHttpClient
import org.http4k.connect.openfeature.Cached
import org.http4k.connect.openfeature.Http
import org.http4k.connect.openfeature.OpenFeature
import org.http4k.connect.openfeature.evaluateAllFlags
import org.http4k.connect.openfeature.evaluateFlag
import org.http4k.connect.openfeature.model.EvaluationContext
import org.http4k.connect.openfeature.model.FlagKey
import org.http4k.connect.openfeature.model.TargetingKey
import org.http4k.core.Uri

val client = OpenFeature.Http(Uri.of("http://localhost:43778"), JavaHttpClient())
val cached = OpenFeature.Cached(client)

val context = EvaluationContext(TargetingKey.of("alice"), "plan" to "premium")
val flagResult = cached.evaluateFlag(FlagKey.of("dark-mode"), context)
val bulkResult = cached.evaluateAllFlags(context)

```



### Default Fake port: 43778

To start:





```kotlin
package content.ecosystem.connect.reference.openfeature

import org.http4k.chaos.start
import org.http4k.connect.openfeature.FakeOpenFeature

val fakeOpenFeature = FakeOpenFeature().start()

```



