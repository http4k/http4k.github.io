# GeminiAI


### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for the Universal LLM adapter (uses the OpenAI fake)
    implementation("org.http4k:http4k-ai-llm-gemini")
    implementation("org.http4k:http4k-connect-ai-openai-fake")
}
```

The http4k-ai Gemini integration provides:
- Universal LLM adapter
- Testing support using the [FakeOpenAI](../openai) connect module

## Universal LLM adapter

The Universal LLM adapter converts the http4k LLM interface into the underlying API, allowing you to swap out providers without changing your application code.





```kotlin
package content.ecosystem.ai.reference.gemini

import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.Gemini
import org.http4k.ai.llm.chat.GeminiModels
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient

val llm = Chat.Gemini(ApiKey.of("api-key"), JavaHttpClient())

val request = ChatRequest(
    Message.User("What's the weather like in London?"),
    params = ModelParams(
        modelName = GeminiModels.Gemini1_5,
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



