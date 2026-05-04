# AzureAI



### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for the Universal LLM adapter (uses the OpenAI fake)
    implementation("org.http4k:http4k-ai-llm-azure")
    implementation("org.http4k:http4k-connect-ai-openai-fake")

    // for the low-level Azure API client
    implementation("org.http4k:http4k-connect-ai-azure")
    implementation("org.http4k:http4k-connect-ai-azure-fake")
}
```

The http4k-ai AzureAI integration provides:

- Universal LLM adapter for AzureAI and AzureGitHubModels
- Low-level AzureAI API Client
- Compatibility with GitHubModels for testing, so you can use a GitHub ApiKey instead of a deployed Azure model. Note that some endpoints are not available in GitHubModels APIs.
- FakeAzureAI server which can be used as testing harness for the API Client 

## Universal LLM adapter

The Universal LLM adapter converts the http4k LLM interface into the underlying API, allowing you to swap out providers without changing your application code.





```kotlin
package content.ecosystem.ai.reference.azure

import org.http4k.ai.llm.AzureRegion
import org.http4k.ai.llm.AzureResource
import org.http4k.ai.llm.chat.Azure
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.openai.OpenAIModels

val llm = Chat.Azure(ApiKey.of("api-key"), AzureResource.of("foo"), AzureRegion.of("london"), JavaHttpClient())

val request = ChatRequest(
    Message.User("What's the weather like in London?"),
    params = ModelParams(
        modelName = OpenAIModels.GPT4,
        tools = listOf(
            LLMTool(
                name = "get_weather",
                description = "Get current weather for a location",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "location" to mapOf("type" to "string")
                    ),
                    "required" to listOf("location")
                )
            )
        )
    )
)

val response = llm(request)

```



## Low-level API Client

The AzureAI connector provides the following Actions:

* GetInfo
* ChatCompletions
* Completions
* CreateEmbeddings

New actions can be created easily using the same transport.

The client APIs utilise the AzureAI API Key (Bearer Auth). There is no reflection used anywhere in the library, so
this is perfect for deploying to a Serverless function.

### Example usage





```kotlin
package content.ecosystem.ai.reference.azure

import org.http4k.ai.model.ApiKey
import org.http4k.connect.azure.AzureAI
import org.http4k.connect.azure.AzureHost
import org.http4k.connect.azure.Http
import org.http4k.connect.azure.Region

// create a client
val client = AzureAI.Http(ApiKey.of("foobar"), AzureHost.of("myHost"), Region.of("us-east-1"))

```



Other examples can be found [here](https://github.com/http4k/http4k-connect/tree/master/ai/azure/fake/src/examples/kotlin).

## Fake AzureAI Server

The Fake AzureAI provides the below actions and can be spun up as a server, meaning it is perfect for using in test
environments without using up valuable request tokens!

* GetInfo
* ChatCompletions
* Completions
* CreateEmbeddings

### Security

The Fake server endpoints are secured with a BearerToken header, but the value is not checked for anything other than presence.

### Generation of responses

By default, a random LoremIpsum generator creates chat completion responses for the Fake. This behaviour can be
overridden to generate custom response formats (eg. structured responses) if required. To do so, create instances of
the `ChatCompletionGenerator` interface and return as appropriate.

### Default Fake port: 14504

To start:





```kotlin
package content.ecosystem.ai.reference.azure

import org.http4k.chaos.start
import org.http4k.connect.azure.FakeAzureAI

val azureAI = FakeAzureAI().start()

```



