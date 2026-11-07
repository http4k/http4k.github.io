# Using Tools


Tool usage in LLMs follows a **request-response cycle** where the model can invoke external tools during conversation and incorporate their results into responses.

## Tool Flow

```
User Message → LLM → Tool Request → Tool Execution → Tool Result → LLM → Final Response
```

1. **User sends message** with available tools
2. **LLM decides** which tools to use (if any)
3. **Tool requests** are executed by your application
4. **Tool results** are fed back to the LLM
5. **LLM generates** final response incorporating tool data

## Tool Definition





```kotlin
package content.ecosystem.ai.concepts.tools

import org.http4k.ai.model.ToolName

data class LLMTool(
    val name: ToolName,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap(),
    val outputSchema: Map<String, Any>? = null
)

```



Tools interfaces are defined with **JSON schemas** that specify input parameters and expected outputs.

## Message Flow Example

### 1. User Request with Tools




```kotlin
package content.ecosystem.ai.concepts.tools

import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.model.ModelName

val weatherTool = LLMTool(
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

val userRequest = Message.User("What's the weather like in London?")

val modelParameters = ModelParams(modelName = ModelName.of("gpt-4"), tools = listOf(weatherTool))

val request = ChatRequest(userRequest, params = modelParameters)

```



### 2. LLM Response with Tool Request




```kotlin
package content.ecosystem.ai.concepts.tools

import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.model.ModelName
import org.http4k.ai.model.RequestId
import org.http4k.ai.model.ResponseId
import org.http4k.ai.model.ToolName

val llmResponseWithToolRequest = Message.Assistant(
    contents = emptyList(),
    toolRequests = listOf(
        ToolRequest(
            id = RequestId.of("call_123"),
            name = ToolName.of("get_weather"),
            arguments = mapOf("location" to "London")
        )
    )
)
val toolResponse = ChatResponse(
    message = llmResponseWithToolRequest,
    metadata = ChatResponse.Metadata(ResponseId.of("response_456"), ModelName.of("gpt-4"))
)

```



### 3. Tool Execution

The caller is responsible for executing the tool request.

### 4. User submits follow-up request, including the history and tool results




```kotlin
package content.ecosystem.ai.concepts.tools

import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.model.Message
import org.http4k.ai.model.RequestId
import org.http4k.ai.model.ToolName

val followUpRequest = ChatRequest(
    messages = listOf(
        userRequest,
        llmResponseWithToolRequest,
        Message.ToolResult(
            id = RequestId.of("call_123"),
            tool = ToolName.of("get_weather"),
            text = "London: 18°C, partly cloudy"
        )
    ),
    params = modelParameters
)

```




### 5. Final LLM Response




```kotlin
package content.ecosystem.ai.concepts.tools

import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.Message
import org.http4k.ai.model.ModelName
import org.http4k.ai.model.ResponseId

val finalResponse = ChatResponse(
    message = Message.Assistant(
        contents = listOf(
            Content.Text("The weather in London is currently 18°C and partly cloudy.")
        )
    ),
    metadata = ChatResponse.Metadata(ResponseId.of("response_456"), ModelName.of("gpt-4"))
)

```



## Schema Validation

The **JSON schema support** ensures:
- **Input validation** - Tool arguments match expected schema
- **Type safety** - Compile-time checking of tool definitions
- **Documentation** - Self-describing tool capabilities
- **Interoperability** - Standard schema format across providers

Tools enable LLMs to access real-time data, perform calculations, interact with APIs, and extend their capabilities beyond their training data.

