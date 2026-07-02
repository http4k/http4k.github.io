# A2A SDK


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.54.0.0"))


    // for A2A server development
    implementation("org.http4k.pro:http4k-ai-a2a-sdk")

    // for connecting to A2A agents
    implementation("org.http4k.pro:http4k-ai-a2a-client")

    // for exposing an A2A agent to MCP clients (the MCP bridge)
    implementation("org.http4k.pro:http4k-ai-mcp-a2a-bridge")
}
```

## About

The [Agent2Agent (A2A) Protocol](https://a2a-protocol.org/) is an open standard designed to facilitate communication and interoperability between independent AI agent systems. In an ecosystem where agents might be built using different frameworks, languages, or by different vendors, A2A provides a common language and interaction model.

A2A supports two protocol bindings:

- **JSON-RPC:** Single endpoint for all operations. Streaming via Server-Sent Events on the same path.
- **REST/HTTP:** RESTful endpoints for tasks, messages, and push notification configs. Streaming via SSE.

Both bindings support the same capabilities: Agent Cards, Tasks, Messages, Artifacts, Streaming, Push Notifications, and multi-turn conversations.

## http4k and Agent2Agent

http4k provides a complete, type-safe implementation of the A2A protocol - both server and client - using the familiar http4k patterns of composable functional protocols. The core extension point is the `MessageHandler`, a simple function that receives a message and returns a response.

```kotlin
typealias MessageHandler = (MessageRequest) -> MessageResponse
```

A `MessageHandler` can return:
- A **Task** for long-running operations with state tracking
- A **Message** for immediate responses
- A **ResponseStream** for streaming multiple results

# Server: http4k-ai-a2a-sdk

## Creating a Server

The simplest way to create an A2A server is to define an `AgentCard` and a `MessageHandler`:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.A2ARole.ROLE_AGENT
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Version
import org.http4k.routing.a2aJsonRpc
import org.http4k.server.Helidon
import org.http4k.server.asServer

fun main() {
    val agentCard = AgentCard(
        name = "My Agent",
        version = Version.of("1.0.0"),
        description = "An example A2A agent"
    )

    // The MessageHandler is a simple function (MessageRequest) -> MessageResponse
    val server = a2aJsonRpc(agentCard) { req ->
        Message(
            messageId = MessageId.random(),
            role = ROLE_AGENT,
            parts = listOf(Part.Text("Hello from My Agent!"))
        )
    }

    // For REST protocol binding, use a2aRest() instead:
    // val server = a2aRest(agentCard) { request -> ... }

    server.asServer(Helidon(8080)).start()
}

```



Both `a2aJsonRpc()` and `a2aRest()` return a `PolyHandler` which can be served by any http4k server backend.

## Agent Cards

Agent Cards describe your agent's identity, capabilities, and skills. They are served at `/.well-known/agent-card.json` and discovered by clients automatically.





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentProvider
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.connect.model.MimeType
import org.http4k.core.Uri

val agentCard = AgentCard(
    name = "Recipe Agent",
    version = Version.of("1.0.0"),
    description = "An agent that helps users with recipes and cooking",
    provider = AgentProvider(organization = "http4k", url = Uri.of("https://http4k.org")),
    documentationUrl = Uri.of("https://http4k.org/docs/a2a"),
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = true,
        extendedAgentCard = true
    ),
    defaultInputModes = listOf(MimeType.of("text/plain")),
    defaultOutputModes = listOf(MimeType.of("text/plain"), MimeType.of("application/json")),
    skills = listOf(
        AgentSkill(
            id = SkillId.of("find-recipe"),
            name = "Find Recipe",
            description = "Search for recipes by ingredients or cuisine",
            tags = listOf("cooking", "recipes", "search")
        ),
        AgentSkill(
            id = SkillId.of("nutrition-info"),
            name = "Nutrition Info",
            description = "Get nutritional information for a recipe",
            tags = listOf("nutrition", "health")
        )
    )
)

```



For agents that expose additional capabilities to authenticated users, use `AgentCardProvider` to serve both standard and extended cards:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentCardProvider
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.routing.a2aJsonRpc

val standardCard = AgentCard(
    name = "My Agent",
    version = Version.of("1.0.0"),
    description = "Public agent capabilities",
    capabilities = AgentCapabilities(extendedAgentCard = true)
)

val extendedCard = standardCard.copy(
    description = "Full agent capabilities (authenticated)",
    skills = listOf(
        AgentSkill(
            id = SkillId.of("admin"),
            name = "Admin Operations",
            description = "Privileged operations for authenticated users"
        )
    )
)

// AgentCardProvider serves both standard and extended cards
val agentCardServer = a2aJsonRpc(messageHandler = {
    // handle messages...
    TODO()
}, cards = AgentCardProvider(standardCard, extendedCard))

```



## Streaming Responses

To stream responses back to clients, return a `ResponseStream` from your handler. The stream is delivered via Server-Sent Events:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.A2ARole.ROLE_AGENT
import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_COMPLETED
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_WORKING
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.a2a.model.Version
import org.http4k.routing.a2aJsonRpc

val streamingAgent = a2aJsonRpc(
    AgentCard(
        name = "Streaming Agent",
        version = Version.of("1.0.0"),
        description = "Agent with streaming support",
        capabilities = AgentCapabilities(streaming = true)
    ),
    messageHandler = { request ->
        val taskId = TaskId.of("task-1")
        val contextId = ContextId.of("context-1")

        // Return a ResponseStream to stream multiple updates to the client
        ResponseStream(
            sequenceOf(
                Task(
                    id = taskId,
                    status = TaskStatus(state = TASK_STATE_WORKING),
                    contextId = contextId,
                    history = listOf(request.message)
                ),
                Message(
                    messageId = MessageId.random(),
                    role = ROLE_AGENT,
                    parts = listOf(Part.Text("Processing your request..."))
                ),
                Task(
                    id = taskId,
                    status = TaskStatus(state = TASK_STATE_COMPLETED),
                    contextId = contextId
                )
            )
        )
    }
)

```



## Task Management

Tasks are the core unit of work in A2A. The server automatically manages task storage and lifecycle. You can provide custom storage implementations:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.A2ARole.ROLE_AGENT
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_COMPLETED
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.a2a.server.storage.TaskStorage
import org.http4k.routing.a2aJsonRpc

// Use the built-in in-memory storage (default)
val tasks = TaskStorage.InMemory()

val serverForStorage = a2aJsonRpc(
    agentCard = AgentCard("My Agent", Version.of("1.0.0"), "Example"),
    tasks = tasksWithPush,
    messageHandler = { request ->
        val task = Task(
            id = TaskId.of("my-task"),
            status = TaskStatus(state = TASK_STATE_COMPLETED),
            contextId = ContextId.of("my-context"),
            history = listOf(
                request.message,
                Message(MessageId.random(), ROLE_AGENT, listOf(Part.Text("Done!")))
            )
        )
        // Store task for later retrieval via GetTask/ListTasks
        tasksWithPush.store(task)
        task
    }
)

```



## Push Notifications

Push notifications allow agents to notify clients of task status changes via webhooks:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.a2a.server.notification.PushNotificationSender
import org.http4k.ai.a2a.server.storage.PushNotificationConfigStorage
import org.http4k.ai.a2a.server.storage.TaskStorage
import org.http4k.ai.a2a.server.storage.withPushNotifications
import org.http4k.routing.a2aJsonRpc

// Enable push notifications in the agent capabilities
val agentCardWithPush = AgentCard(
    name = "Notifying Agent",
    version = Version.of("1.0.0"),
    description = "Agent with push notification support",
    capabilities = AgentCapabilities(pushNotifications = true)
)

val pushConfigs = PushNotificationConfigStorage.InMemory()
val pushSender = PushNotificationSender.Http()  // sends POST to configured webhook URLs

// Wrap task storage to automatically send push notifications on task updates
val tasksWithPush = TaskStorage.InMemory().withPushNotifications(pushConfigs, pushSender)

val serverWithPush = a2aJsonRpc(
    agentCard = agentCardWithPush,
    tasks = tasksWithPush,
    pushNotifications = pushConfigs,
    messageHandler = { request -> TODO() }
)

```



## Multi-tenancy

Both protocol bindings support multi-tenant routing. REST endpoints accept an optional `/{tenant}` path prefix, and JSON-RPC passes tenant via request parameters:





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Version
import org.http4k.routing.a2aRest

// REST binding supports multi-tenant routing with /{tenant} prefix
// Requests to /acme/message:send will have tenant="acme"
val restServer = a2aRest(
    agentCard = AgentCard("Multi-tenant Agent", Version.of("1.0.0"), "Serves multiple tenants"),
    messageHandler = { request ->
        // Tenant is available in the HTTP request headers/path
        TODO()
    }
)

// JSON-RPC passes tenant via the request params
// val server = a2aJsonRpc(agentCard) { request -> ... }

```



## Message Filters

Similar to http4k's `Filter`, the `MessageFilter` allows cross-cutting concerns to be applied to all message handling:





```kotlin
package content.ecosystem.pro.reference.a2a

import content._sites.a2a.handler
import org.http4k.ai.a2a.MessageFilter
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.a2a.then
import org.http4k.routing.a2aJsonRpc

// MessageFilter works like http4k's Filter but for A2A message handling
val logging = MessageFilter { next ->
    { request ->
        println("Received message: ${request.message.parts}")
        next(request).also { println("Response: $it") }
    }
}

val auth = MessageFilter { next ->
    { request ->
        // Check authorization from the HTTP request
        val token = request.http.header("Authorization")
        requireNotNull(token) { "Missing authorization" }
        next(request)
    }
}

// Compose filters and handler
val handler = logging.then(auth).then { request ->
    TODO("handle message")
}

val serverWithFilter = a2aJsonRpc(
    AgentCard("Filtered Agent", Version.of("1.0.0"), "With filters"),
    messageHandler = handler
)

```



# Client: http4k-ai-a2a-client

## Connecting to an Agent





```kotlin
package content.ecosystem.pro.reference.a2a

import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.HttpA2AClient
import org.http4k.ai.a2a.client.RestA2AClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.core.Uri

fun main() {
    // JSON-RPC client
    val jsonRpcClient = HttpA2AClient(Uri.of("http://localhost:8080"))

    // Or REST client
    val restClient = RestA2AClient(Uri.of("http://localhost:8080"))

    jsonRpcClient.use { client ->
        // Discover agent capabilities
        val card = client.agentCard().valueOrNull()!!
        println("Connected to: ${card.name} v${card.version}")

        // Send a message
        val response = client.message(
            Message(
                messageId = MessageId.random(),
                role = ROLE_USER,
                parts = listOf(Part.Text("Hello, agent!"))
            )
        ).valueOrNull()!!

        println("Response: $response")
    }
}

```



## Streaming Messages





```kotlin
package content.ecosystem.pro.reference.a2a

import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.HttpA2AClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task
import org.http4k.core.Uri

fun main() {
    HttpA2AClient(Uri.of("http://localhost:8080")).use { client ->
        val stream = client.messageStream(
            Message(
                messageId = MessageId.random(),
                role = ROLE_USER,
                parts = listOf(Part.Text("Process this data"))
            )
        ).valueOrNull()!! as ResponseStream

        // Iterate over streaming responses
        stream.forEach { item ->
            when (item) {
                is Task -> println("Task ${item.id}: ${item.status.state}")
                is Message -> println("Message: ${item.parts}")
                else -> {}
            }
        }
    }
}

```



## Task Operations





```kotlin
package content.ecosystem.pro.reference.a2a

import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.HttpA2AClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskState
import org.http4k.core.Uri

fun main() {
    HttpA2AClient(Uri.of("http://localhost:8080")).use { client ->
        // Send a message that creates a task
        val task = client.message(
            Message(MessageId.random(), ROLE_USER, listOf(Part.Text("Start processing")))
        ).valueOrNull()!! as Task

        // Get task by ID
        val retrieved = client.tasks().get(task.id).valueOrNull()!!
        println("Task state: ${retrieved.status.state}")

        // List tasks with filtering
        val page = client.tasks().list(
            status = TaskState.TASK_STATE_WORKING,
            pageSize = 10
        ).valueOrNull()!!
        println("Found ${page.totalSize} working tasks")

        // Cancel a task
        val cancelled = client.tasks().cancel(task.id).valueOrNull()!!
        println("Cancelled: ${cancelled.status.state}")
    }
}

```



# Exposing an A2A Agent to MCP Clients

The `http4k-ai-mcp-a2a-bridge` module wraps any A2A agent as an [MCP](/ecosystem/ai/reference/mcp/) HTTP server. This lets an LLM in any MCP client (Claude Desktop, Cursor, etc.) discover and talk to your A2A agent without writing custom MCP integration code.

The bridge fetches the agent card once at startup and folds the name, description, and skill catalog into the `send_message` tool description, then exposes four MCP tools: `send_message`, `get_task`, `cancel_task`, and `list_tasks`. Responses come back as MCP `structuredContent` carrying the full A2A `Task` / `Message` shape - including `taskId` and `contextId` - so the LLM can chain follow-up calls.

The inbound MCP request's `Authorization` header is forwarded to the A2A agent on every tool call, so each MCP caller authenticates as itself.





```kotlin
package content.ecosystem.pro.reference.a2a

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.core.Uri
import org.http4k.routing.mcpA2aBridgeServer
import org.http4k.server.Helidon
import org.http4k.server.asServer

fun main() {
    // Expose an existing A2A agent as a full MCP HTTP server. Tools generated:
    //  - send_message: send a free-text message to the agent (with skill catalog in the description)
    //  - get_task / cancel_task / list_tasks: task lifecycle management
    //
    // The inbound MCP request's Authorization header is forwarded to the A2A
    // agent, so each MCP caller authenticates as itself against the agent.
    val server = mcpA2aBridgeServer(
        identity = ServerMetaData(McpEntity.of("a2a-bridge"), Version.of("1.0.0")),
        baseUri = Uri.of("https://my-a2a-agent.example.com"),
        security = NoMcpSecurity
    )

    server.asServer(Helidon(3001)).start()
}

```



For finer control - custom auth headers, a pre-built `A2AClient`, or composing the bridge alongside other MCP capabilities - use `mcpA2aBridge(...)` directly and wrap it with `mcp(...)` yourself.

# Testing

A2A servers can be tested fully in-memory without starting a real server, using the test client factories:





```kotlin
package content.ecosystem.pro.reference.a2a

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_AGENT
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Version
import org.http4k.routing.a2aJsonRpc
import org.junit.jupiter.api.Test

class A2ATestingExample {

    private val agentCard = AgentCard("Test Agent", Version.of("1.0.0"), "For testing")

    // Create an A2A server as a PolyHandler
    private val server = a2aJsonRpc(agentCard, messageHandler = { request ->
        Message(MessageId.of("response-1"), ROLE_AGENT, listOf(Part.Text("Echo")))
    })

    // Create an in-memory test client - no network, no ports
    private val client = server.testA2AJsonRpcClient()

    @Test
    fun `can discover agent card`() {
        assertThat(client.agentCard(), equalTo(Success(agentCard)))
    }

    @Test
    fun `can send message and get response`() {
        val response = client.message(
            Message(MessageId.of("msg-1"), ROLE_USER, listOf(Part.Text("Hello")))
        ).valueOrNull()!! as Message

        assertThat(response.role, equalTo(ROLE_AGENT))
        assertThat(response.parts.first(), equalTo(Part.Text("Echo")))
    }
}

```



