# MCP SDK


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // for general MCP server development
    implementation("org.http4k.pro:http4k-ai-mcp-sdk")

    // if you want to connect to an MCP server
    implementation("org.http4k.pro:http4k-ai-mcp-client")

    // if you want to test MCP servers or MCP Apps
    implementation("org.http4k.pro:http4k-ai-mcp-testing")

    // for x402 payment-protected MCP tools
    implementation("org.http4k.pro:http4k-ai-mcp-x402")

    // for MPP payment-protected MCP tools
    implementation("org.http4k.pro:http4k-ai-mcp-mpp")
}
```

### Try it out!

Want to see http4k MCP in action? Visit the [http4k Toolbox](https://toolbox.http4k.org) where you can download and install a fully working MCP app that integrates directly with Claude Desktop and other MCP clients.

## About

The [Model Context Protocol](https://modelcontextprotocol.info/) is an open standard created by Anthropic that defines
how apps can feed information to AI language models. It creates a uniform way to link these models with various data
sources and tools, which streamlines the integration process. MCP services can be deployed in a Server or Serverless
environment.

MCP itself is based on the JSON RPC standard, which is used to communicate between the client and server. Messages are
sent from client to server and then asynchronously from server to client. MCP defines a set of standardised capabilities
which can be provided by the server or client. One use of these capabilities is to allow pre-trained models to have
access to both live data and APIs that can be used by the model to provide answers to user requests. Another use is to
provide agentic behaviour by providing standard communications between several MCP entities.

Currently the MCP standard supports the following transports:

- **HTTP Streaming:** Clients can interact with the MCP server by either a SSE connection (accepting
  `application/event-stream` or via plain HTTP (accepting `application/json`). Stream resumption and replay is supported
  on the SSE connection by calling GET with the `Last-Event-ID` header. All traffic is served by calling the `/mcp`
  endpoint.
- **Server Sent Events + HTTP:** Clients initiate an SSE connection to the server (on `/sse`) which is used to send
  messages to the
  client asynchronously at any time. Further client -> server requests and notifications are sent via HTTP `/messages`
  endpoint, with
  responses being sent back via the SSE.
- **Standard IO:** Clients start a process that communicates with the server via JSON RPC messages via standard input
  and output streams.

The MCP capabilities include:

- **Tools:** are exposed by the server and can be used by the client to perform tasks. The tool consists of a
  description and a JSON Schema definition for the inputs and outputs.
- **Prompts:** given a set of inputs by a client, the server can generate a prompt parameterised to those inputs. This
  allows servers to generate prompts that are tailored to the client's data.
- **Resources:** are exposed by the server and can be used by the client to access text or binary content an example of
  this is a browser tool that can access web pages.
- **Roots:** the client supplies a list of file roots to the server which can be used to resolve file paths.
- **Completions:** The server provides auto-completion of options for a particular Prompt or Resource.
- **Sampling:** An MCP server can request an LLN completion for text or binary content a connected Client.
- **Elicitation:** The server can request additional information from the user by rendering dynamic forms based on a supplied schema.
- **Tasks:** The server can create and manage long-running asynchronous operations with progress tracking.

### Supported Protocol Versions

| Protocol | Version            | Description |
|----------|--------------------|-------------|
| **MCP** | 2025-11-25 + Draft | Core protocol with Tasks, Elicitation, and Sampling support |
| **MCP Apps** | 2026-01-26         | Server-rendered UI components for Claude Desktop and other MCP clients |

## http4k ❤️ Model Context Protocol

http4k provides very good support for the **Model Context Protocol**, and has been designed to make it easy to build
your own MCP-compliant servers in Kotlin, using the familiar http4k methodology of simple and composable functional
protocols. Each of the capabilities is modelled as a **binding** between a capability description and a function that
exposes the capability. See [Capability Types](#capabilities) for more details.

The MCP support in http4k consists of two parts - the `http4k-ai-mcp-sdk` and
the [http4k-mcp-desktop](https://github.com/http4k/mcp-desktop) application which is used to connect the MCP server to
a desktop client such as **Claude Desktop**.

# SDK: http4k-ai-mcp-sdk

The core SDK for working with the Model Context Protocol. You can build your own MCP-compliant applications using this
module by plugging in capabilities into the server. The **http4k-ai-mcp-sdk module** provides a simple way to create
either
**HTTP Streaming**, **SSE**, **StdIo** or **Websocket** based servers. For StdIo-based servers, we recommend compiling
your server to GraalVM for ease of distribution.

## Capability Types

The MCP protocol is based on a set of capabilities that can be provided by the server or client. Each capability can be
installed separately into the server, and the client can interact with the server using these capabilities.

Additional, when using one of the Streaming protocols, a `Client` object is passed to the Capability handler through the
request, allowing the server to send messages back to the client. These calls can be blocking or non-blocking, depending
on the client capability in question. These work most effectively when the client sends a `Progress Token` to the
server, which can be used to identify the operation being progressed.

### Server Capability: Tools

Tools allow external MCP clients such as LLMs to request the server to perform bespoke functionality such as invoking an
API. The Tool capability is modelled as a function `typealias ToolHandler = (ToolRequest) -> ToolResponse`, filtered
with a `ToolFilter`, and can be
bound to a tool definition which describes it's arguments and outputs using the http4k Lens system:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ToolFilter
import org.http4k.ai.mcp.ToolHandler
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.localDate
import org.http4k.ai.mcp.then
import org.http4k.lens.with
import java.time.LocalDate

// tool argument inputs are typesafe lens
val toolArg = Tool.Arg.localDate().required("date", "date in format yyyy-mm-dd")

// the description of the tool exposed to clients
fun toolDefinitionFor(name: String): Tool = Tool(
    "diary_for_${name.replace(" ", "_")}",
    "details $name's diary appointments. Responds with a list of appointments for the given month",
    toolArg,
)

// handles the actual call to tht tool
val diaryToolHandler: ToolHandler = {
    val calendarData = mapOf(
        LocalDate.of(2025, 3, 21) to listOf(
            "08:00 - Breakfast meeting",
            "11:00 - Dentist appointment",
            "16:00 - Project review"
        )
    )

    val date = toolArg(it)
    val appointmentContent = calendarData[date]?.map { Content.Text("$date: $it") } ?: emptyList()

    ToolResponse.Ok(appointmentContent)
}

// use a Filter to perform logging/tracing/metrics
val loggingTool = ToolFilter { next ->
    {
        println("Called with: $it")
        val response = next(it)
        println("Result was: $it")
        response
    }
}.then(diaryToolHandler)

object DiaryTool {
    @JvmStatic
    fun main() = println(
        // invoke/test the tool offline - just invoke it like a function
        loggingTool(
            ToolRequest().with(Tool.Arg.localDate().required("date") of LocalDate.parse("2025-03-21"))
        )
    )
}

```



#### Complex Tools request arguments

The http4k MCP SDK also supports handling of complex arguments in the request (and response - MCP draft). This can be
done by using the `auto()` extension function and passing an example argument instance in order that the complex JSON
schema can be rendered. Note that the Kotlin Reflection JAR also needs to be present on the classpath to take advantage
of this feature, or you can supply a custom instance of `ConfigurableMcpJson` (Moshi-based) to work without reflection (
we recommend the use of the [Kotshi](https://github.com/ansman/kotshi) compiler plugin to generate adapters for this
use-case).





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.util.McpJson.auto
import org.http4k.lens.with
import org.http4k.routing.bind

// a complex response object
data class MavenJar(val org: String, val name: String, val version: Int)

// the auto() method is imported from McpJson (requires Kotlin Reflect)
val libDescription = Tool.Arg
    .auto(MavenJar("org.http4k", "http4k-ai-mcp-sdk", 6))
    .required("the maven dependency")

// the auto() method is imported from McpJson (requires Kotlin Reflect)
val nextVersion = Tool.Output.auto(MavenJar("org.http4k", "http4k-ai-mcp-sdk", 6)).toLens()

val getNextVersion = Tool(
    "nextVersion",
    "get the next maven version for a library",
    libDescription,
    output = nextVersion
)

object MavenTool {
    @JvmStatic
    fun main() = println(
        getNextVersion bind {
            // we can extract the class automatically using the lens
            val lib: MavenJar = libDescription(it)

            // and then inject the typesafe response object!
            ToolResponse.Ok().with(nextVersion of lib.copy(version = lib.version + 1))
        }
    )
}

```



#### Tool Argument Types Reference

The `Tool.Arg` object provides type-safe builders for all common argument types:

| Type | Builder | Description |
|------|---------|-------------|
| `String` | `Tool.Arg.string()` | Basic string |
| `String` | `Tool.Arg.nonEmptyString()` | Non-empty string |
| `String` | `Tool.Arg.nonBlankString()` | Non-blank string |
| `Boolean` | `Tool.Arg.boolean()` | Boolean |
| `Int` | `Tool.Arg.int()` | Integer |
| `Long` | `Tool.Arg.long()` | Long integer |
| `Double` | `Tool.Arg.double()` | Floating point |
| `Float` | `Tool.Arg.float()` | Float |
| `UUID` | `Tool.Arg.uuid()` | UUID |
| `URI` | `Tool.Arg.uri()` | URI |
| `Instant` | `Tool.Arg.instant()` | Timestamp |
| `Duration` | `Tool.Arg.duration()` | Duration |
| `Period` | `Tool.Arg.period()` | Period |
| `LocalDate` | `Tool.Arg.localDate()` | Date (ISO format) |
| `LocalTime` | `Tool.Arg.localTime()` | Time |
| `ZonedDateTime` | `Tool.Arg.zonedDateTime()` | Zoned datetime |
| `OffsetDateTime` | `Tool.Arg.offsetDateTime()` | Offset datetime |
| `YearMonth` | `Tool.Arg.yearMonth()` | Year-month |
| `ZoneId` | `Tool.Arg.zoneId()` | Timezone |
| `Locale` | `Tool.Arg.locale()` | Locale |
| `Enum<T>` | `Tool.Arg.enum<T>()` | Enum values |
| `Value<T>` | `Tool.Arg.value(factory)` | values4k types |
| `Regex` | `Tool.Arg.regexObject()` | Regex pattern |
| `Base64` | `Tool.Arg.base64()` | Base64 encoded |

### Server Capability: Completions

Completions give the server to standard autocomplete abilities based on partial input from a client. The Completion
capability is modelled as a
function `typealias CompletionHandler = (CompletionRequest) -> CompletionResponse`, filtered with a `CompletionFilter`,
and can be bound to a prompt definition which describes it's arguments
using the http4k Lens system.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.CompletionFilter
import org.http4k.ai.mcp.CompletionHandler
import org.http4k.ai.mcp.CompletionRequest
import org.http4k.ai.mcp.CompletionResponse
import org.http4k.ai.mcp.model.Reference
import org.http4k.ai.mcp.then

// the reference of the completion
val promptReference = Reference.Prompt("Greet")

// this function provides completion options for the "Greet" prompt, returning
// a list of all users whose names do not contain the letters already typed
val completionHandler: CompletionHandler = {
    val allUsers = listOf("Alice", "Alex", "Albert", "Bob", "Charlie", "David")
    val prefix = it.argument.value

    CompletionResponse.Ok(allUsers.filter { it.startsWith(prefix) })
}

// use a Filter to perform logging/tracing/metrics
val loggingCompletion = CompletionFilter { next ->
    {
        println("Called with: $it")
        val response = next(it)
        println("Result was: $it")
        response
    }
}.then(completionHandler)


object ProvideCompletionOptionsForPrompt {
    @JvmStatic
    fun main() = println(
        // invoke/test the completion offline - just invoke it like a function
        loggingCompletion(
            CompletionRequest("prefix", "Al")
        )
    )
}

```



### Server Capability: Prompts

Prompts allow the server to generate a prompt based on the client's inputs. The Prompt capability is modelled as a
function `typealias PromptHandler = (PromptRequest) -> PromptResponse`, filtered with a `PromptFilter`, and can be
bound to a prompt definition which describes it's arguments
using the http4k Lens system.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.CompletionFilter
import org.http4k.ai.mcp.PromptFilter
import org.http4k.ai.mcp.PromptHandler
import org.http4k.ai.mcp.PromptRequest
import org.http4k.ai.mcp.PromptResponse
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.Message
import org.http4k.ai.mcp.model.Prompt
import org.http4k.ai.mcp.model.PromptName
import org.http4k.ai.mcp.then
import org.http4k.ai.model.Role.Companion.Assistant
import org.http4k.lens.int
import org.http4k.lens.with

// argument lenses for the prompt
val name = Prompt.Arg.required("name", "the name of the person to greet")
val age = Prompt.Arg.int().optional("age", "the age of the person to greet")


// the description of the prompt
val prompt: Prompt = Prompt(PromptName.of("Greet"), "Creates a greeting message for a person", name, age)

// handles the actual call to tht prompt
val greetingPromptHandler: PromptHandler = { req: PromptRequest ->
    val content = when (age(req)) {
        null -> Content.Text("Hello, ${name(req)}!")
        else -> Content.Text("Hello, ${name(req)}! How is req being ${age(req)}?")
    }
    PromptResponse.Ok(listOf(Message(Assistant, content)))
}

// use a Filter to perform logging/tracing/metrics
val loggingPrompt = PromptFilter { next ->
    {
        println("Called with: $it")
        val response = next(it)
        println("Result was: $it")
        response
    }
}.then(greetingPromptHandler)


object GreetPersonPrompt {
    @JvmStatic
    fun main() = println(
        // invoke/test the prompt offline - just invoke it like a function
        loggingPrompt(
            PromptRequest().with(
                name of "David",
                age of 30
            )
        )
    )
}

```



[//]: # (### Capability: Sampling)

[//]: # ()

[//]: # (Sampling allows the server to invoke the client LLM model to generate some content. The Sampling capability is modelled)

[//]: # (as a function `&#40;SamplingRequest&#41; -> Sequence<SamplingResponse>`, and you can pass the contents of previous interactions)

[//]: # (as the)

[//]: # (context to the model.)

[//]: # ()

[//]: # (



```kotlin
package content.ecosystem.ai.reference.mcp

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.allValues
import dev.forkhandles.result4k.map
import org.http4k.ai.mcp.McpError
import org.http4k.ai.mcp.SamplingRequest
import org.http4k.ai.mcp.SamplingResponse
import org.http4k.ai.mcp.ToolHandler
import org.http4k.ai.mcp.ToolResponse.Error
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Message
import org.http4k.ai.model.MaxTokens
import org.http4k.ai.model.Role.Companion.User

val request = SamplingRequest(listOf(Message(User, Text("Roast this tool!"))), MaxTokens.of(1000))

val roastingToolWithSampling: ToolHandler = { req ->

    // at this point, the client will pass context to the model, and return a list of responses
    val allContent: Result<String, McpError> = req.client.sample(request)
        // a list of results is returned, so we can use `allValues` to get all content
        .allValues()
        .map { responses: List<SamplingResponse> ->
            // collect the text from each response
            responses
                .filterIsInstance<SamplingResponse.Ok>()
                .flatMap { it.content }
                .filterIsInstance<Text>()
                .joinToString("")
        }

    when (allContent) {
        is Success<String> -> Ok(listOf(Text(allContent.value)))
        is Failure<McpError> -> Error("Failure sampling: ${allContent.reason}")
    }
}

```

)

### Server Capability: Resources

Resources provide a way to interrogate the contents of data sources such as filesystem, database or website. The
Resource capability is modelled as a function `typealias ResourceHandler = (ResourceRequest) -> ResourceResponse`,
filtered with a `ResourceFilter`. Resources can be static or templated to provide bounds within which the client can
interact with the resource.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ResourceFilter
import org.http4k.ai.mcp.ResourceHandler
import org.http4k.ai.mcp.ResourceRequest
import org.http4k.ai.mcp.ResourceResponse
import org.http4k.ai.mcp.model.Resource
import org.http4k.ai.mcp.model.ResourceName
import org.http4k.ai.mcp.then
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.jsoup.Jsoup


val websiteResource = Resource.Static(Uri.of("https://http4k.org"), ResourceName.of("HTTP4K"), "description")

// this function provides a static resource that contains all the links from the http4k website
val getLinksResourceHandler: ResourceHandler = {
    val htmlPage = JavaHttpClient()(Request(GET, it.uri))

    val links = getAllLinksFrom(htmlPage)
        .map { Resource.Content.Text(it.text(), Uri.of(it.attr("href"))) }

    ResourceResponse.Ok(links)
}

// use a Filter to perform logging/tracing/metrics
val loggingResource = ResourceFilter { next ->
    {
        println("Called with: $it")
        val response = next(it)
        println("Result was: $it")
        response
    }
}.then(getLinksResourceHandler)


private fun getAllLinksFrom(htmlPage: Response) = Jsoup.parse(htmlPage.bodyString())
    .allElements.toList()
    .filter { it.tagName() == "a" }
    .filter { it.hasAttr("href") }

object LookupAllLinksFromWebResource {
    @JvmStatic
    fun main() = println(
        // invoke/test the prompt offline - just invoke it like a function
        loggingResource(ResourceRequest(Uri.of("https://http4k.org")))
    )
}

```



#### Templated Resources

Resources can also be templated using RFC 6570 URI templates, allowing clients to access resources matching a pattern:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ResourceHandler
import org.http4k.ai.mcp.ResourceResponse
import org.http4k.ai.mcp.model.Resource
import org.http4k.routing.bind
import java.io.File

// Define a templated resource using RFC 6570 URI templates
val templatedFileResource = Resource.Templated(
    uriTemplate = "file://{+path}",      // {+path} allows slashes in the path
    name = "files",
    description = "Access files by path"
)

// Handler extracts the path from the URI and reads the file
val templatedFileResourceHandler: ResourceHandler = { request ->
    val path = request.uri.toString().substringAfter("file://")
    val file = File("/data", path)

    when {
        file.exists() && file.isFile -> ResourceResponse.Ok(
            Resource.Content.Text(file.readText(), request.uri)
        )

        else -> throw IllegalArgumentException("File not found: $path")
    }
}

// Bind the templated resource to its handler
val boundTemplatedFileResource = templatedFileResource bind templatedFileResourceHandler

```



#### Directory Resources

For file-based resources, http4k provides `DirectoryResources` which automatically exposes a directory as browsable resources:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.server.capability.DirectoryResources
import org.http4k.ai.mcp.server.capability.RecursionMode.Flat
import org.http4k.ai.mcp.server.capability.RecursionMode.Recursive
import java.io.File

object DirectoryResourcesExample {
    @JvmStatic
    fun main() {
        // Expose a directory as browsable MCP resources
        // DirectoryResources implements the Resources interface
        val recursiveResources = DirectoryResources(
            dir = File("/path/to/documents"),
            recursive = Recursive  // walks subdirectories
        )

        val flatResources = DirectoryResources(
            dir = File("/path/to/documents"),
            recursive = Flat  // only top-level files
        )

        // DirectoryResources provides:
        // - listResources() - Lists all files in the directory
        // - listTemplates() - Returns URI templates for dynamic access
        // - read() - Reads file content by URI

        // Pass to McpProtocol constructor for use with custom server setup,
        // or use individual Resource.Static bindings for simpler cases
    }
}

```



### Server Capability: Reporting Progress

The Progress capability allows the server to report progress of a long-running operation to the client through the
`progress()` call.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.CompletionHandler
import org.http4k.ai.mcp.CompletionRequest
import org.http4k.ai.mcp.CompletionResponse

val progress: CompletionHandler = { req ->
    req.client.progress("token", 50, 100.0, "half way done")
    val allUsers = listOf("Alice", "Alex", "Albert", "Bob", "Charlie", "David")
    val prefix = req.argument.value

    CompletionResponse.Ok(allUsers.filter { it.startsWith(prefix) })
}

```



### Client Capability: Sampling

Sampling allow the server to request information about the client's request from it's connected LLM, passing context.
Note that in order for this to work, a `ProgressToken` must be present in the request, which is used to identify the
operation being progressed.





```kotlin
package content.ecosystem.ai.reference.mcp

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.allValues
import dev.forkhandles.result4k.map
import org.http4k.ai.mcp.McpError
import org.http4k.ai.mcp.SamplingRequest
import org.http4k.ai.mcp.SamplingResponse
import org.http4k.ai.mcp.ToolHandler
import org.http4k.ai.mcp.ToolResponse.Error
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Message
import org.http4k.ai.model.MaxTokens
import org.http4k.ai.model.Role.Companion.User

val request = SamplingRequest(listOf(Message(User, Text("Roast this tool!"))), MaxTokens.of(1000))

val roastingToolWithSampling: ToolHandler = { req ->

    // at this point, the client will pass context to the model, and return a list of responses
    val allContent: Result<String, McpError> = req.client.sample(request)
        // a list of results is returned, so we can use `allValues` to get all content
        .allValues()
        .map { responses: List<SamplingResponse> ->
            // collect the text from each response
            responses
                .filterIsInstance<SamplingResponse.Ok>()
                .flatMap { it.content }
                .filterIsInstance<Text>()
                .joinToString("")
        }

    when (allContent) {
        is Success<String> -> Ok(listOf(Text(allContent.value)))
        is Failure<McpError> -> Error("Failure sampling: ${allContent.reason}")
    }
}

```


    
### Client Capability: Elicitation

Elicitation allow the server to request additional information from the user in order to complete a task, by rendering a
dynamic form based on a supplied schema. You can use the passed `Client` to send the elicitation request to the client
and then wait for a response. Note that in order for this to work, a `ProgressToken` must be present in the request,
which is used to identify the operation being progressed.

A user has the option to accept, decline or cancel the elicitation request, and the server can handle these responses
accordingly. .





```kotlin
package content.ecosystem.ai.reference.mcp

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.recover
import org.http4k.ai.mcp.ElicitationRequest
import org.http4k.ai.mcp.ElicitationResponse
import org.http4k.ai.mcp.ToolHandler
import org.http4k.ai.mcp.ToolResponse.Error
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Elicitation
import org.http4k.ai.mcp.model.Elicitation.Metadata.string.MaxLength
import org.http4k.ai.mcp.model.Elicitation.Metadata.string.MinLength
import org.http4k.ai.mcp.model.ElicitationAction.accept
import org.http4k.ai.mcp.model.int
import org.http4k.ai.mcp.model.string

val userName =
    Elicitation.string().required("name", "What is your name?", "The user's name", MinLength(1), MaxLength(10))
val userAge =
    Elicitation.int().required("age", "How old are you?", "The user's age", Elicitation.Metadata.integer.Min(18))

val greetingToolWithElicitation: ToolHandler = { req ->
    val request = ElicitationRequest.Form("Please fill in your details", userName, userAge)

    // at this point, the client will render
    req.client.elicit(request)
        .map {
            when (it) {
                is ElicitationResponse.Ok -> when (it.action) {
                    accept -> Ok("hello ${(userAge(it))}, when you are twice your age you will be ${2 * userAge(it)}!")
                    else -> Ok("hello stranger!")
                }

                else -> error("not supported in this example")
            }
        }
        .recover { Error("error: $it") }
}

```



### Capability Packs: Composed MCP Capabilities

http4k MCP lets you combine any number of related capabilities into reusable collections using the `CapabilityPack` API.
This is perfect for organizing related tools, resources, or prompts that logically belong together and shipping them as
a module or library.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.server.capability.CapabilityPack
import org.http4k.routing.bind

fun SetOfCapabilities() = CapabilityPack(
    toolDefinitionFor("David") bind diaryToolHandler,
    promptReference bind completionHandler,
    websiteResource bind getLinksResourceHandler,
    prompt bind greetingPromptHandler
)

```



### x402 Payment-Protected Tools

The `http4k-ai-mcp-x402` module integrates the [x402 protocol](/ecosystem/connect/reference/x402/) with MCP, allowing you to require cryptocurrency payments for individual tool calls. Payment information is exchanged via the MCP `_meta` field on tool requests and responses.

The module provides two filters:

- **`X402ToolFilter`** - A `ToolFilter` that wraps individual tools with payment requirements. Payment payloads are sent in `_meta["x402/payment"]` and settlement receipts are returned in `_meta["x402/payment-response"]`. Returns structured `PaymentRequired` errors when payment is missing or invalid.
- **`McpFilters.X402PaymentRequired`** - A lower-level `McpFilter` that operates on raw MCP JSON-RPC requests for protocol-level payment gating.

Both filters use a `PaymentCheck` function to determine whether a request is `Free` or `Required`, allowing you to mix free and paid tools in the same server:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.capability.then
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.x402.PaymentCheck
import org.http4k.ai.mcp.x402.X402ToolFilter
import org.http4k.connect.x402.X402Facilitator
import org.http4k.connect.x402.Http
import org.http4k.connect.x402.model.AssetAddress
import org.http4k.connect.x402.model.PaymentAmount
import org.http4k.connect.x402.model.PaymentNetwork
import org.http4k.connect.x402.model.PaymentRequirements
import org.http4k.connect.x402.model.PaymentScheme
import org.http4k.connect.x402.model.WalletAddress
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.JettyLoom
import org.http4k.server.asServer

fun `x402 mcp tool example`() {
    val requirements = PaymentRequirements(
        scheme = PaymentScheme.of("exact"),
        network = PaymentNetwork.of("base-sepolia"),
        asset = AssetAddress.of("0x036CbD53842c5426634e7929541eC2318f3dCF7e"),
        amount = PaymentAmount.of("100"),
        payTo = WalletAddress.of("0x1234567890abcdef1234567890abcdef12345678"),
        maxTimeoutSeconds = 60
    )

    val facilitator = X402Facilitator.Http(Uri.of("https://x402-facilitator.example.com"))

    // create a ToolFilter that requires payment for all tools it wraps
    val paymentFilter = X402ToolFilter(facilitator) { PaymentCheck.Required(listOf(requirements)) }

    // wrap individual tools with the payment filter
    val paidTool = paymentFilter.then(
        Tool("premium_data", "get premium data (requires payment)") bind {
            Ok(listOf(Content.Text("Here is your premium data!")))
        }
    )

    // free tools can be mixed alongside paid tools
    val freeTool = Tool("free_data", "get free data") bind {
        Ok(listOf(Content.Text("Here is your free data!")))
    }

    mcp(
        ServerMetaData(McpEntity.of("x402 mcp server"), Version.of("0.1.0")),
        NoMcpSecurity,
        paidTool, freeTool
    ).asServer(JettyLoom(3001)).start()
}

```



### MPP Payment-Protected MCP

The `http4k-ai-mcp-mpp` module integrates the [Machine Payments Protocol (MPP)](/ecosystem/connect/reference/mpp/) with MCP, allowing you to require payments for MCP interactions. Unlike x402, MPP has no facilitator — you implement `MppVerifier` directly. Payment credentials are exchanged via the MCP `_meta` field on requests and responses.

The module provides two filters:

- **`MppToolFilter`** - A `ToolFilter` that wraps individual tools with payment challenges. Credentials are sent in `_meta["org.paymentauth/credential"]` and receipts are returned in `_meta["org.paymentauth/receipt"]`. Returns structured errors with challenges when payment is missing or invalid.
- **`McpFilters.MppPaymentRequired`** - A lower-level `McpFilter` that operates on raw MCP JSON-RPC requests, enabling payment gating on any MCP resource — tools, prompts, resources, or any other server capability.

Both filters use an `MppPaymentCheck` function to determine whether a request is `Free` or `Required`, allowing you to mix free and paid capabilities in the same server.

Use `MppToolFilter` to protect individual tools:





```kotlin
package content.ecosystem.ai.reference.mcp

import dev.forkhandles.result4k.Success
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.mpp.MppPaymentCheck
import org.http4k.ai.mcp.mpp.MppPayments
import org.http4k.ai.mcp.mpp.MppToolFilter
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.protocol.withExtensions
import org.http4k.ai.mcp.server.capability.then
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.connect.mpp.MppVerifier
import org.http4k.connect.mpp.model.Challenge
import org.http4k.connect.mpp.model.ChallengeId
import org.http4k.connect.mpp.model.ChargeRequest
import org.http4k.connect.mpp.model.Currency
import org.http4k.connect.mpp.model.PaymentAmount
import org.http4k.connect.mpp.model.PaymentIntent
import org.http4k.connect.mpp.model.PaymentMethod
import org.http4k.connect.mpp.model.Realm
import org.http4k.connect.mpp.model.Receipt
import org.http4k.connect.mpp.model.ReceiptStatus
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.time.Instant

fun `mpp mcp tool example`() {
    val method = PaymentMethod.of("stripe")
    val intent = PaymentIntent.of("charge")

    val challenge = Challenge(
        id = ChallengeId.of("challenge-123"),
        realm = Realm.of("api.example.com"),
        method = method,
        intent = intent,
        request = ChargeRequest(
            amount = PaymentAmount.of("100"),
            currency = Currency.of("USD")
        )
    )

    val verifier = MppVerifier { credential ->
        Success(
            Receipt(
                status = ReceiptStatus.success,
                method = credential.challenge.method,
                timestamp = Instant.now(),
                challengeId = credential.challenge.id
            )
        )
    }

    // create a ToolFilter that requires payment for all tools it wraps
    val paymentFilter = MppToolFilter(verifier) { MppPaymentCheck.Required(listOf(challenge)) }

    // wrap individual tools with the payment filter
    val paidTool = paymentFilter.then(
        Tool("premium_data", "get premium data (requires payment)") bind {
            Ok(listOf(Content.Text("Here is your premium data!")))
        }
    )

    // free tools can be mixed alongside paid tools
    val freeTool = Tool("free_data", "get free data") bind {
        Ok(listOf(Content.Text("Here is your free data!")))
    }

    mcp(
        ServerMetaData(McpEntity.of("mpp mcp server"), Version.of("0.1.0"))
            .withExtensions(MppPayments(methods = listOf(method), intents = listOf(intent))),
        NoMcpSecurity,
        paidTool, freeTool
    ).asServer(JettyLoom(3001)).start()
}

```



Use `McpFilters.MppPaymentRequired` to gate all MCP requests at the protocol level — tools, prompts, resources, and any other capability:





```kotlin
package content.ecosystem.ai.reference.mcp

import dev.forkhandles.result4k.Success
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.mpp.MppPaymentCheck
import org.http4k.ai.mcp.mpp.MppPayments
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.protocol.withExtensions
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.connect.mpp.MppVerifier
import org.http4k.connect.mpp.model.Challenge
import org.http4k.connect.mpp.model.ChallengeId
import org.http4k.connect.mpp.model.ChargeRequest
import org.http4k.connect.mpp.model.Currency
import org.http4k.connect.mpp.model.PaymentAmount
import org.http4k.connect.mpp.model.PaymentIntent
import org.http4k.connect.mpp.model.PaymentMethod
import org.http4k.connect.mpp.model.Realm
import org.http4k.connect.mpp.model.Receipt
import org.http4k.connect.mpp.model.ReceiptStatus
import org.http4k.ai.mcp.mpp.MppPaymentRequired
import org.http4k.filter.McpFilters
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.time.Instant

fun `mpp mcp filter example`() {
    val method = PaymentMethod.of("stripe")
    val intent = PaymentIntent.of("charge")

    val challenge = Challenge(
        id = ChallengeId.of("challenge-123"),
        realm = Realm.of("api.example.com"),
        method = method,
        intent = intent,
        request = ChargeRequest(
            amount = PaymentAmount.of("100"),
            currency = Currency.of("USD")
        )
    )

    val verifier = MppVerifier { credential ->
        Success(
            Receipt(
                status = ReceiptStatus.success,
                method = credential.challenge.method,
                timestamp = Instant.now(),
                challengeId = credential.challenge.id
            )
        )
    }

    // McpFilter gates ALL MCP requests — tools, prompts, resources, etc.
    val mcpFilter = McpFilters.MppPaymentRequired(verifier) { MppPaymentCheck.Required(listOf(challenge)) }

    val tool = Tool("premium_data", "get premium data") bind {
        Ok(listOf(Content.Text("Here is your premium data!")))
    }

    mcp(
        ServerMetaData(McpEntity.of("mpp mcp server"), Version.of("0.1.0"))
            .withExtensions(MppPayments(methods = listOf(method), intents = listOf(intent))),
        NoMcpSecurity,
        tool,
        mcpFilter = mcpFilter
    ).asServer(JettyLoom(3001)).start()
}

```



### MCP Apps: Server-Rendered UI

MCP Apps combine Tool and Resource capabilities to enable server-rendered UI components. When an LLM calls the tool, the client displays the HTML content from the associated resource. This is useful for interactive dashboards, forms, and visualisations.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ResourceRequest
import org.http4k.ai.mcp.model.Domain
import org.http4k.ai.mcp.model.apps.Csp
import org.http4k.ai.mcp.model.apps.McpAppResourceMeta
import org.http4k.ai.mcp.model.apps.McpAppVisibility.app
import org.http4k.ai.mcp.model.apps.McpAppVisibility.model
import org.http4k.ai.mcp.model.apps.McpApps
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.withExtensions
import org.http4k.ai.mcp.server.capability.extension.RenderMcpApp
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.core.Uri
import org.http4k.routing.mcp
import org.http4k.server.Helidon
import org.http4k.server.asServer

object McpAppExample {
    @JvmStatic
    fun main() {
        // Create an MCP App that renders a dashboard UI
        val dashboardApp = RenderMcpApp(
            name = "show_dashboard",
            description = "Display the analytics dashboard",
            uiUri = Uri.of("ui://dashboard"),
            meta = McpAppResourceMeta(
                csp = Csp(connectDomains = listOf(Domain.of("https://api.example.com"))),
                prefersBorder = true
            ),
            toolVisibility = listOf(model, app)
        ) { request: ResourceRequest ->
            // Return HTML content based on the request
            """
            <html>
            <body>
                <h1>Analytics Dashboard</h1>
                <p>Requested: ${request.uri}</p>
            </body>
            </html>
            """.trimIndent()
        }

        // Create the MCP server with Apps extension enabled
        val server = mcp(
            ServerMetaData("Dashboard App", "1.0.0").withExtensions(McpApps),
            NoMcpSecurity,
            dashboardApp
        )

        server.asServer(Helidon(3001)).start()
    }
}

```



MCP Apps support metadata for security and permissions:

- **Content Security Policy (CSP)** - Control allowed domains for connections, resources, and frames
- **Permissions** - Request camera, microphone, geolocation, or clipboard access
- **Visibility** - Control whether the app is visible to the model, the app UI, or both

### MCP Apps Host

The `McpAppsHost` provides a local HTTP server for testing and running multiple MCP apps. It creates endpoints for listing apps, reading resources, and calling tools.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.apps.McpAppsHost
import org.http4k.ai.mcp.model.apps.McpAppResourceMeta
import org.http4k.ai.mcp.model.apps.McpApps
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.withExtensions
import org.http4k.ai.mcp.server.capability.extension.RenderMcpApp
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.testing.McpClientFactory
import org.http4k.core.Uri
import org.http4k.routing.mcp
import org.http4k.server.Helidon
import org.http4k.server.asServer

object McpAppsHostExample {
    @JvmStatic
    fun main() {
        // Create MCP app servers
        val dashboardApp = mcp(
            ServerMetaData("Dashboard", "1.0.0").withExtensions(McpApps),
            NoMcpSecurity,
            RenderMcpApp(
                "show_dashboard",
                "Show dashboard",
                Uri.of("ui://dash"),
                McpAppResourceMeta()
            ) {
                "<h1>Dashboard</h1>"
            }
        )

        val settingsApp = mcp(
            ServerMetaData("Settings", "1.0.0").withExtensions(McpApps),
            NoMcpSecurity,
            RenderMcpApp(
                "show_settings",
                "Show settings",
                Uri.of("ui://settings"),
                McpAppResourceMeta()
            ) {
                "<h1>Settings</h1>"
            }
        )

        // Create host with multiple apps
        // Use McpClientFactory.Test for in-memory testing
        // Use McpClientFactory.Http for remote servers
        val host = McpAppsHost(
            McpClientFactory.Test(dashboardApp),
            McpClientFactory.Test(settingsApp)
        )

        // Start the host server - provides endpoints for listing and accessing apps
        host.asServer(Helidon(8099)).start()

        // The host provides these endpoints:
        // GET /               - List all available MCP apps
        // GET /api/resources  - Read UI resource content (?serverId=...&uri=...)
        // POST /api/tools/call - Call a tool from an MCP app
    }
}

```



### MCP Servers

Servers are created by combining the configured MCP Protocol with a set of capabilities, an optional security, and a
binding to a Server or Serverless backend. The server can be started using any of the http4k server backends which
support SSE ( see [servers](/ecosystem/http4k/reference/servers)).





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.ServerProtocolCapability.ToolsChanged
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.OAuthMcpSecurity
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.Helidon
import org.http4k.server.asServer

object ServerStreamingExample {
    @JvmStatic
    fun main() {
        // call the correct protocol method here - there are 5 to choose from!
        val mcpServer = mcp(
            // give the server an identity
            ServerMetaData(McpEntity.of("http4k MCP Server"), Version.of("1.0.0"), ToolsChanged),

            // insert a security implementation
            OAuthMcpSecurity(Uri.of("https://oauth-server"), Uri.of("https://mcp-server/mcp")) { it == "my_oauth_token" },

            // bind server capabilities here ...
            toolDefinitionFor("David") bind diaryToolHandler,
            promptReference bind completionHandler,
            websiteResource bind getLinksResourceHandler,
            prompt bind greetingPromptHandler
        )

        // simply start it up!
        mcpServer.asServer(Helidon(3002)).start()
    }
}

```



Alternatively you can use any non-SSE supporting server backend and forego the SSE support in lieu of request/response
via JSON:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.ServerProtocolCapability.ToolsChanged
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcpHttpNonStreaming
import org.http4k.server.SunHttp
import org.http4k.server.asServer

object ServerNonstreamingExample {
    @JvmStatic
    fun main() {
        // this protocol version does not support SSE connections.
        val mcpServer = mcpHttpNonStreaming(
            ServerMetaData(McpEntity.of("http4k MCP Server"), Version.of("1.0.0"), ToolsChanged),
            NoMcpSecurity,
            toolDefinitionFor("David") bind diaryToolHandler,
        )

        // simply start it up on any server you like!
        mcpServer.asServer(SunHttp(3002)).start()
    }
}

```



There are a number of different ways customise the MCP protocol server to suit your needs. Features that can be
configured are shown below. Note that the main SDK library is designed for simplicity - and you may have to drill down
one level to access some of these customisations:

- Security - Basic, Bearer, API Key or auto-discovered (or custom!)
  OAuth ([specification standard](https://modelcontextprotocol.info/specification/draft/basic/authorization))
- Session validation (via `SessionProvider`) - Ensure that the client is authenticated to access the contents of the
  session
- Event Store (via `SessionEventStore`) - Store and resume MCP event streams using the SSE last-event-id header
- Event Tracking (via `SessionEventTracking`) - Assign a unique ID to each event to track the progress of the event
  stream
- Origin validation (via `Filter` and `SseFilter`) - Protect against DNS rebinding attacks by configuring allowed
  origins

#### Important: Protecting Against DNS Rebinding Attacks

When deploying an MCP server that uses HTTP Streaming or SSE, you must implement `Origin` header validation to prevent
DNS rebinding attacks. These attacks can allow malicious websites to interact with your MCP server by changing IP
addresses after initial DNS
resolution, potentially bypassing same-origin policy protections. This can be done by implementing the HTTP (`Filter`)
and SSE specific (`SseFilter`) filter implementations and attaching them to the Polyhandler that is returned from the
`mcpXXX()` call.

The http4k-ai-mcp-sdk provides protection mechanisms that can be applied to your server:





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.ServerProtocolCapability.ToolsChanged
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.then
import org.http4k.filter.AnyOf
import org.http4k.filter.CorsAndRebindProtection
import org.http4k.filter.CorsPolicy
import org.http4k.filter.OriginPolicy
import org.http4k.filter.PolyFilters
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.Helidon
import org.http4k.server.asServer

fun main() {
    val mcpServer = mcp(
        ServerMetaData(McpEntity.of("http4k MCP Server"), Version.of("1.0.0"), ToolsChanged),
        BearerAuthMcpSecurity { it == "my_bearer_token" },
        toolDefinitionFor("David") bind diaryToolHandler,
    )

    // Define a CORS policy to protect against cross-origin requests and DNS rebinding attacks
    val corsPolicy = CorsPolicy(
        OriginPolicy.AnyOf("foo.com", "localhost"),
        listOf("allowed-header"), listOf(GET, POST, DELETE)
    )

    PolyFilters.CorsAndRebindProtection(corsPolicy)
        .then(mcpServer)
        .asServer(Helidon(3002)).start()
}

```



#### Serverless Example

MCP capabilities can be bound to [http4k Serverless](/ecosystem/http4k/reference/serverless) functions using the HTTP
protocol in non-streaming mode. To activate this simply bind them into the non-streaming HTTP which is a simple
`HttpHandler`.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcpHttpNonStreaming
import org.http4k.serverless.ApiGatewayV2LambdaFunction
import org.http4k.serverless.AppLoader

// This function is an AWS Lambda function.
class McpLambdaFunction : ApiGatewayV2LambdaFunction(AppLoader {
    mcpHttpNonStreaming(
        ServerMetaData(McpEntity.of("http4k mcp over serverless"), Version.of("0.1.0")),
        NoMcpSecurity,
        toolDefinitionFor("David") bind diaryToolHandler
    )
})

```



### Alternative Transports

In addition to HTTP streaming, http4k MCP supports WebSocket and JSON-RPC transports:

#### WebSocket Server





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcpWebsocket
import org.http4k.server.Helidon
import org.http4k.server.asServer

object WebsocketServerExample {
    @JvmStatic
    fun main() {
        // Create a WebSocket-based MCP server
        // Provides full-duplex communication over a single connection
        val server = mcpWebsocket(
            ServerMetaData("WebSocket MCP Server", "1.0.0"),
            NoMcpSecurity,
            Tool("ping", "Ping the server") bind {
                ToolResponse.Ok(Text("pong"))
            }
        )

        // WebSocket endpoint is available at /ws
        server.asServer(Helidon(3001)).start()
    }
}

```



#### JSON-RPC Server





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcpJsonRpc
import org.http4k.server.SunHttp
import org.http4k.server.asServer

object JsonrpcServerExample {
    @JvmStatic
    fun main() {
        // Create a pure JSON-RPC based MCP server
        // Accepts standard JSON-RPC 2.0 messages
        val server = mcpJsonRpc(
            ServerMetaData("JSON-RPC MCP Server", "1.0.0"),
            NoMcpSecurity,
            Tool("echo", "Echo the input") bind {
                ToolResponse.Ok(Text("Echo received"))
            }
        )

        // JSON-RPC endpoint is available at /jsonrpc
        server.asServer(SunHttp(3001)).start()
    }
}

```



### MCP Client

http4k provides client classes to connect to your MCP servers via HTTP, SSE, JSONRPC or Websockets. The clients take
care of the
initial MCP handshake and provide a simple API to send and receive messages to the capabilities, or to register for
notifications with an MCP server.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.CompletionRequest
import org.http4k.ai.mcp.ElicitationResponse
import org.http4k.ai.mcp.PromptRequest
import org.http4k.ai.mcp.ResourceRequest
import org.http4k.ai.mcp.SamplingResponse
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.client.http.HttpStreamingMcpClient
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.ElicitationAction.accept
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Prompt
import org.http4k.ai.mcp.model.PromptName
import org.http4k.ai.mcp.model.Reference
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.localDate
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.model.ModelName
import org.http4k.ai.model.Role
import org.http4k.ai.model.ToolName
import org.http4k.client.JavaHttpClient
import org.http4k.core.BodyMode
import org.http4k.core.BodyMode.*
import org.http4k.core.Uri
import org.http4k.lens.int
import org.http4k.lens.with
import java.time.LocalDate

object ClientExample {
    @JvmStatic
    fun main() {
        val client = HttpStreamingMcpClient(
            Uri.of("http://localhost:3001/mcp"),
            McpEntity.of("http4k MCP Client"), Version.of("1.0.0"),
            JavaHttpClient(responseBodyMode = Stream)
        )

        println(
            ">>> Server handshake\n" +
                client.start()
        )

        println(
            ">>> Tool list\n" +
                client.tools().list()
        )

        println(
            ">>> Tool calling\n" +
                client.tools().call(
                    ToolName.of("diary_for_David"),
                    ToolRequest().with(
                        Tool.Arg.localDate().required("date") of LocalDate.parse("2025-03-21")
                    )
                )
        )

        println(
            ">>> Prompt list\n" +
                client.prompts().list()
        )

        println(
            ">>> Prompt calling\n" +
                client.prompts().get(
                    PromptName.of("Greet"),
                    PromptRequest().with(
                        Prompt.Arg.required("name") of "David",
                        Prompt.Arg.int().optional("age") of 30
                    )
                )
        )

        println(
            ">>> Completions\n" +
                client.completions().complete(
                    Reference.Prompt("Greet"),
                    CompletionRequest("prefix", "Al")
                )
        )

        println(
            ">>> Resource list\n" +
                client.resources().list()
        )

        println(
            ">>> Resource reading\n" +
                client.resources().read(
                    ResourceRequest(Uri.of("https://http4k.org"))
                )
        )

        client.sampling().onSampled {
            println(">>> Sampled: $it")
            sequenceOf(SamplingResponse.Ok(ModelName.of("gpt-4"), Role.Assistant, listOf(Text("Sampled: $it"))))
        }

        client.elicitations().onElicitation {
            println(">>> Elicitation: $it")
            ElicitationResponse.Ok(accept).with(userName of "David", userAge of 30)
        }

        client.close()
    }
}

```



### Testing MCP Servers

http4k provides an in-memory test client for testing MCP servers without network overhead. Use the `testMcpClient()` extension on any `PolyHandler` to create a test client.





```kotlin
package content.ecosystem.ai.reference.mcp

import org.http4k.ai.mcp.ResourceRequest
import org.http4k.ai.mcp.ResourceResponse
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Resource
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.model.ToolName
import org.http4k.core.Uri
import org.http4k.lens.with
import org.http4k.routing.bind
import org.http4k.routing.mcp

object TestingExample {
    @JvmStatic
    fun main() {
        val nameLens = Tool.Arg.string().required("name")

        // Create your MCP server
        val mcpServer = mcp(
            ServerMetaData("Test Server", "1.0.0"),
            NoMcpSecurity,
            Tool("greet", "Greet someone", nameLens) bind { request ->
                ToolResponse.Ok(Text("Hello, ${nameLens(request)}!"))
            },
            Resource.Static("file://readme", "readme", "The readme") bind { req ->
                ResourceResponse.Ok(Resource.Content.Text("# Welcome", req.uri))
            }
        )

        // Create in-memory test client - no network needed!
        val client = mcpServer.testMcpClient()
        client.start()

        // Test tools - returns McpResult<List<McpTool>>
        val toolsResult = client.tools().list()
        println("Available tools: $toolsResult")

        // Call a tool
        val callResult = client.tools().call(
            ToolName.of("greet"),
            ToolRequest().with(nameLens of "World")
        )
        println("Tool result: $callResult")

        // Test resources - returns McpResult<List<McpResource>>
        val resourcesResult = client.resources().list()
        println("Available resources: $resourcesResult")

        // Read a resource
        val content = client.resources().read(ResourceRequest(Uri.of("file://readme")))
        println("Resource content: $content")

        // Test prompts - returns McpResult<List<McpPrompt>>
        val promptsResult = client.prompts().list()
        println("Available prompts: $promptsResult")

        client.close()
    }
}

```



For more control, use `McpClientFactory` which provides two modes:
- `McpClientFactory.Test(polyHandler)` - In-memory client for unit tests
- `McpClientFactory.Http(serverUri)` - HTTP client for integration tests

# [http4k-mcp-desktop](https://github.com/http4k/mcp-desktop)

A desktop client that bridges StdIo-bound desktop clients such as **Claude Desktop** with your own MCP servers operating
over HTTP/SSE, either locally or remotely. The desktop client is a simple native application that can be downloaded from
the http4k GitHub, or built from the http4k source.

### To use mcp-desktop client with clients such as Claude Desktop or Cursor:

1. Download the `mcp-desktop` binary for your platform from: [https://github.com/http4k/mcp-desktop], or install it with
   brew:

```bash
brew tap http4k/tap
brew install http4k-mcp-desktop
```

2. Configure [Claude Desktop](https://claude.ai/download) to use the `mcp-desktop` binary as an MCP server with the
   following configuration. You can find the configuration file in `claude_desktop_config.json`, or by browsing through
   the
   developer settings menu. You can add as many MCP servers as you like. Note that [Cursor](https://www.cursor.com/)
   users should use the `--transport http-nonstream` or `--transport jsonrpc` option for correct integration:

```json
{
    "mcpServers": {
        "MyMcpServer": {
            "command": "http4k-mcp-desktop",
            // or path to the binary
            "args": [
                "--transport",
                "--http-stream",
                "--url",
                "http://localhost:3001/mcp"
            ]
        }
    }
}
```

### To build mcp-desktop from source:

1. Clone the [http4k MCP Desktop](https://github.com/http4k/mcp-desktop) repo
2. Install a GraalVM supporting JDK
3. Run `./gradlew :native-compile` to build the desktop client binary locally for your platform

## OpenTelemetry Span Modifiers

MCP servers can be instrumented with OpenTelemetry tracing via `McpFilters.OpenTelemetryTracing`. Span names follow [OTel semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/):
- `tools/call show_ui` — method + tool name
- `prompts/get my-prompt` — method + prompt name
- `resources/read` — method only (URIs are high-cardinality)
- `tools/list`, `prompts/list` — method only

### Default Span Modifiers

Included in `defaultMcpOtelSpanModifiers` and active by default:

| Modifier | Attributes Set |
|----------|---------------|
| `CallToolSpanModifiers` | `gen_ai.tool.name`, `gen_ai.operation.name` |
| `GetPromptSpanModifiers` | `gen_ai.prompt.name`, `gen_ai.operation.name` |
| `ReadResourceSpanModifiers` | `mcp.resource.uri`, `gen_ai.operation.name` |
| `CompletionSpanModifiers` | `mcp.completion.ref`, `gen_ai.operation.name` |

### Opt-In Detail Modifiers

Capture MCP request arguments and response payloads as span attributes. These may contain sensitive data — add them explicitly:





```kotlin
import io.opentelemetry.api.GlobalOpenTelemetry
import org.http4k.filter.CallToolDetailSpanModifiers
import org.http4k.filter.CompletionDetailSpanModifiers
import org.http4k.filter.GetPromptDetailSpanModifiers
import org.http4k.filter.McpFilters
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.ReadResourceDetailSpanModifiers
import org.http4k.filter.defaultMcpOtelSpanModifiers

val mcpFilter = McpFilters.OpenTelemetryTracing(
    openTelemetry = GlobalOpenTelemetry.get(),
    spanModifiers = defaultMcpOtelSpanModifiers
        + CallToolDetailSpanModifiers
        + GetPromptDetailSpanModifiers
        + CompletionDetailSpanModifiers
        + ReadResourceDetailSpanModifiers
)

```



| Modifier | Request Attribute | Response Attribute |
|----------|------------------|-------------------|
| `CallToolDetailSpanModifiers` | `gen_ai.tool.call.arguments` | `gen_ai.tool.call.result` |
| `GetPromptDetailSpanModifiers` | `gen_ai.prompt.arguments` | `gen_ai.prompt.result` |
| `CompletionDetailSpanModifiers` | `gen_ai.completion.arguments` | `gen_ai.completion.result` |
| `ReadResourceDetailSpanModifiers` | — | `gen_ai.resource.result` |

`gen_ai.tool.call.*` follows the official OTel spec. `gen_ai.prompt.*`, `gen_ai.completion.*`, and `gen_ai.resource.*` are http4k custom conventions.

When detail modifiers are enabled, [Wiretap Living Test Documents](/ecosystem/http4k/reference/wiretap/#living-test-document) will include the MCP payloads in the generated markdown.

