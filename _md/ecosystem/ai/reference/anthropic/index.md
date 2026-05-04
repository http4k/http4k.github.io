# Anthropic


### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for the Universal LLM adapter
    implementation("org.http4k:http4k-ai-llm-anthropic")

    // for the low-level AnthropicAI API client
    implementation("org.http4k:http4k-connect-ai-anthropic")

    // for the FakeAnthropicAI server
    implementation("org.http4k:http4k-connect-ai-anthropic-fake")
}
```

The http4k-ai AnthropicAI integrations provide:

- Universal LLM adapter
- Low-level API Client
- FakeAnthropicAI server which can be used as testing harness for the API Client 

## Universal LLM adapter

The Universal LLM adapter converts the http4k LLM interface into the underlying API, allowing you to swap out providers without changing your application code.





```kotlin
package content.ecosystem.ai.reference.anthropic

import org.http4k.ai.llm.chat.AnthropicAI
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.anthropic.AnthropicModels.Claude_Sonnet_4_5

val llm = Chat.AnthropicAI(ApiKey.of("api-key"), JavaHttpClient())

val request = ChatRequest(
    Message.User("What's the weather like in London?"),
    params = ModelParams(
        modelName = Claude_Sonnet_4_5,
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

The AnthropicAI connector provides the following Actions:

- MessageCompletion (streaming and non-streaming)

* New actions can be created easily using the same transport.

The client APIs utilise the AnthropicAI API Key. There is no reflection used anywhere in the library, so
this is perfect for deploying to a Serverless function.

## Fake AnthropicAI Server

The Fake AnthropicAI provides the below actions and can be spun up as a server, meaning it is perfect for using in test
environments without using up valuable request tokens!

- MessageCompletion (streaming and non-streaming)

### Security

The Fake server endpoints are secured with a API key header, but the value is not checked for anything other than presence.

### Generation of responses

By default, a random LoremIpsum generator creates message completion responses for the Fake. This behaviour can be
overridden to generate custom response formats (eg. structured responses) if required. To do so, create instances of
the `MessageContentGenerator` interface and return as appropriate.

### Default Fake port: 18909

To start:





```kotlin
package content.ecosystem.ai.reference.anthropic

import org.http4k.chaos.start
import org.http4k.connect.anthropic.FakeAnthropicAI

val anthropicAI = FakeAnthropicAI().start()

```



