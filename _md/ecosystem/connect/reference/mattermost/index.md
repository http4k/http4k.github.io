# Mattermost


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-mattermost")
}
```

The Mattermost connector provides the following Actions:

- TriggerWebhook

### Example usage





```kotlin
import dev.forkhandles.result4k.Result
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.mattermost.FakeMattermost
import org.http4k.connect.mattermost.Http
import org.http4k.connect.mattermost.Mattermost
import org.http4k.connect.mattermost.action.TriggerWebhookPayload
import org.http4k.connect.mattermost.triggerWebhook
import org.http4k.connect.storage.InMemory
import org.http4k.connect.storage.Storage
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.filter.debug
import java.util.UUID

fun main() {
    val USE_REAL_CLIENT = false

    val payloads = Storage.InMemory<List<TriggerWebhookPayload>>()

    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeMattermost(payloads)

    // create a client
    val mattermost = Mattermost.Http(
        baseUri = Uri.of("https://mattermost.com"),
        http = http.debug()
    )

    val payload = TriggerWebhookPayload(
        text = "Hello world",
        iconUrl = Uri.of("http://icon.url"),
    )

    // all operations return a Result monad of the API type
    val result: Result<String, RemoteFailure> = mattermost.triggerWebhook(
        key = UUID.randomUUID().toString(),
        payload = payload,
    )
    println(result)

    println(payloads)
}

```



### Default Fake port: 54786

To start:





```kotlin
package content.ecosystem.connect.reference.mattermost

import org.http4k.chaos.start
import org.http4k.connect.mattermost.FakeMattermost

val mattermost = FakeMattermost().start()

```



