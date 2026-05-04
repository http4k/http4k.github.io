# LMStudio

### Installation

```kotlin
dependencies {

    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for the low-level LMStudio API client
    implementation("org.http4k:http4k-connect-ai-lmstudio")

    // for the FakeLMStudio server
    implementation("org.http4k:http4k-connect-ai-lmstudio-fake")
}
```

The http4k-ai LmStudio integration provides:

- Low-level API Client
- FakeLmStudio server which can be used as testing harness for either API Client

## Low-level API Client

The LmStudio connector provides the following Actions:

* GetModels
* ChatCompletion
* CreateEmbeddings

New actions can be created easily using the same transport.

The client APIs utilise the LmStudio API Key (Bearer Auth). There is no reflection used anywhere in the library, so
this is perfect for deploying to a Serverless function.

### Example usage





```kotlin
package content.ecosystem.ai.reference.lmstudio

import dev.forkhandles.result4k.Result
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.lmstudio.FakeLmStudio
import org.http4k.connect.lmstudio.Http
import org.http4k.connect.lmstudio.LmStudio
import org.http4k.connect.lmstudio.getModels
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeLmStudio()

    // create a client
    val client = LmStudio.Http(http.debug())

    // all operations return a Result monad of the API type
    val result = client.getModels()

    println(result)
}

```



Other examples can be
found [here](https://github.com/http4k/http4k-connect/tree/master/ai/lmstudio/fake/src/examples/kotlin).

## Fake LmStudio Server

The Fake LmStudio provides the below actions and can be spun up as a server, meaning it is perfect for using in test
environments without using up valuable request tokens!

* GetModels
* ChatCompletion

### Generation of responses

By default, a random LoremIpsum generator creates chat completion responses for the Fake. This behaviour can be
overridden to generate custom response formats (eg. structured responses) if required. To do so, create instances of
the `ChatCompletionGenerator` interface and return as appropriate.

### Default Fake port: 58438

To start:





```kotlin
package content.ecosystem.ai.reference.lmstudio

import org.http4k.chaos.start
import org.http4k.connect.lmstudio.FakeLmStudio

val lmStudio = FakeLmStudio().start()

```



