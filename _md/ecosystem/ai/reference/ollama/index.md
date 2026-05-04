# Ollama


### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for the low-level Ollama API client
    implementation("org.http4k:http4k-connect-ai-ollama")

    // for the FakeOllama server
    implementation("org.http4k:http4k-connect-ai-ollama-fake")
}
```

The http4k-ai Ollama integration provides:

- Low-level API Client
- FakeOllama server which can be used as testing harness 

## Low-level API Client

The Ollama connector provides the following Actions:

* GetModels
* ChatCompletion
* CreateEmbeddings
* GenerateImage

New actions can be created easily using the same transport.

The client APIs utilise the Ollama API Key (Bearer Auth). There is no reflection used anywhere in the library, so
this is perfect for deploying to a Serverless function.

### Example usage





```kotlin
package content.ecosystem.ai.reference.ollama

import dev.forkhandles.result4k.Result
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.ollama.FakeOllama
import org.http4k.connect.ollama.Http
import org.http4k.connect.ollama.Ollama
import org.http4k.connect.ollama.getModels
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeOllama()

    // create a client
    val client = Ollama.Http(http.debug())

    // all operations return a Result monad of the API type
    val result = client.getModels()

    println(result)
}

```



Other examples can be found [here](https://github.com/http4k/http4k-connect/tree/master/ai/openai/fake/src/examples/kotlin).

## Fake Ollama Server

The Fake Ollama provides the below actions and can be spun up as a server, meaning it is perfect for using in test
environments without using up valuable request tokens!

* GetModels
* ChatCompletion
* Completion
* PullModel
* CreateEmbeddings

### Generation of responses

By default, a random LoremIpsum generator creates chat completion responses for the Fake. This behaviour can be
overridden to generate custom response formats (eg. structured responses) if required. To do so, create instances of
the `ChatCompletionGenerator` interface and return as appropriate.

### Default Fake port: 31193

To start:





```kotlin
package content.ecosystem.ai.reference.ollama

import org.http4k.chaos.start
import org.http4k.connect.ollama.FakeOllama

val ollama = FakeOllama().start()

```



