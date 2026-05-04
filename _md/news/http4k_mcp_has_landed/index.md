# http4k MCP Has Landed: Build Your Own AI Agents with Zero Compromise on Testability!


<img class="imageMid my-4" src="./http4k-mcp.png" alt="http4k MCP logo"/>

We're thrilled to announce the launch of the http4k [Model Context Protocol (MCP) SDK](https://mcp.http4k.org)! 🚀
This powerful addition to the http4k ecosystem brings seamless AI integration capabilities to your applications through
a clean, functional API that stays true to http4k's core principles.

This module represents our implementation of the [Model Context Protocol](https://modelcontextprotocol.io/) - an open
standard that enables AI systems like Claude to interact with your data, tools, and services. And naturally, we've built
it with the same unwavering commitment to testability that defines everything we do.

The http4k MCP SDK also supports the latest version of the [specification](https://spec.modelcontextprotocol.io/) which
supports stateless and stateful
connections - this is a major advancement in the abilities of the protocol and should unlock mass adoption of the
technology - especially with the recent announcement that OpenAI will also be supporting MCP.

We are also very proud to be the first JVM-based SDK to support this new version of the protocol - which the http4k team
has been involved in feeding back into via the specification GitHub.

## TL;DR - What is MCP?

MCP (Model Context Protocol) is an open protocol that standardises how AI assistants like Claude can access external
data sources and execute actions through your code. The http4k MCP SDK provides a type-safe, functional Kotlin
implementation that makes it easy to expose your applications' capabilities to AI systems.

With the http4k MCP SDK, you can (amongst other things!):

- Create **Tools** that let AI models perform actions through your code
- Share **Resources** that provide context for AI reasoning
- Define **Prompts** that standardize how users interact with AI
- Build **Agents** that can be deployed to both server and serverless environments.

## Why http4k MCP?

From the beginning, we designed the http4k MCP SDK with the same principles that have made http4k so beloved:

### Testable Design

Just like the rest of http4k, our MCP implementation is built around pure functions with no side effects. This means you
can verify your AI integrations without spinning up servers or making external calls - the entire capability surface can
be tested with simple unit tests.





```kotlin
package content.news.http4k_mcp_has_landed

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.lens.with
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.Tool
import org.http4k.routing.bind

val weatherTool = Tool("weather", "Gets weather for a city", cityArg) bind { req ->
    val city = cityArg(req)
    ToolResponse.Ok(Content.Text("Weather in $city: Sunny and 25°C"))
}

//@Test
fun `can call a tool as a function`() {
    // Test it directly - no server needed!
    val request = ToolRequest().with(cityArg of "London")
    val response = weatherTool(request) as ToolResponse.Ok
    assertThat(response, equalTo(ToolResponse.Ok(Content.Text("Weather in London: Sunny and 25°C"))))
}

```



### Flexible Transport

The module supports all standard MCP transports, including the latest Streamable HTTP protocol, SSE, WebSocket, and
Standard IO. This means you can run your MCP server anywhere - from cloud platforms to desktop applications.





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.core.Credentials
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.BasicAuthMcpSecurity
import org.http4k.ai.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.routing.mcp
import org.http4k.routing.mcpWebsocket

// Use the standard Streamable HTTP transport
val mcpServer = mcp(
    ServerMetaData(McpEntity.of("My MCP Server"), Version.of("1.0.0")),
    BasicAuthMcpSecurity("realm") { it == Credentials("user", "password") },
    weatherTool
)

// Or use WebSockets
val wsServer = mcpWebsocket(
    ServerMetaData(McpEntity.of("WebSocket MCP Server"), Version.of("1.0.0")),
    BasicAuthMcpSecurity("realm") { it == Credentials("user", "password") },
    weatherTool
)

```



### Type-safe Tooling

Our implementation leverages http4k's powerful Lens system to provide type-safe tool definitions and capability
bindings, including simple and complex arguments. This gives you compile-time safety when defining your AI integrations.





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.int
import org.http4k.ai.mcp.model.localDate
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.util.McpJson.auto

val cityArg = Tool.Arg.string().required("city", "City name")
val temperatureArg = Tool.Arg.int().required("temperature", "Temperature in Celsius")
val dateArg = Tool.Arg.localDate().required("date", "Date in yyyy-MM-dd format")

data class Human(val name: String, val age: Int)

val complexArg = Tool.Arg.auto(Human("David", 21)).required("human dev")

```



### Protocol Client

Beyond just serving MCP capabilities, we've included a fully-featured MCP protocol client that you can build directly
into your applications. This enables you to create custom agents and advanced AI workflows by consuming MCP services
programmatically.





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.ai.model.ToolName
import org.http4k.core.Uri
import org.http4k.lens.with
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.client.http.HttpStreamingMcpClient
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.Version

// Create an MCP client
val client = HttpStreamingMcpClient(
    Uri.of("http://localhost:3000/sse"),
    McpEntity.of("My Client"),
    Version.of("1.0.0"),
).apply { start() }

// Call tools programmatically
val toolResponse = client.tools().call(
    ToolName.of("weather"),
    ToolRequest().with(cityArg of "London"),
)

```



### Security

The MCP spec has also importantly introduced standards for security based around OAuth2 and JWT tokens. The http4k MCP SDK provides
simple plug-in integration with all of the security models which http4k supports - including Basic, API key and OAuth.





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.time.Instant

fun main() {
    mcp(
        ServerMetaData(McpEntity.of("foo"), Version.of("bar")),
        BearerAuthMcpSecurity { it == "my_oauth_token" },
        Tool("time", "Get the current time") bind { ToolResponse.Ok(listOf(Content.Text(Instant.now().toString()))) }
    )
        .asServer(JettyLoom(3001)).start()
}

```



## Getting Started

It's incredibly easy to get started with the http4k MCP SDK:





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.core.Uri
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Content
import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.ai.mcp.server.security.OAuthMcpSecurity
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    // 1. Define a tool using a typesafe lens
    val cityArg = Tool.Arg.string().required("city", "City name")

    val weatherTool = Tool(
        "weather",
        "Gets city weather",
        cityArg
    ) bind { req ->
        val city = cityArg(req)
        ToolResponse.Ok(Content.Text("Weather in $city: Sunny and 25°C"))
    }

    // 2. Create an MCP server and select your security model
    val mcpServer = mcp(
        ServerMetaData(McpEntity.of("Weather API"), Version.of("1.0.0")),
        OAuthMcpSecurity(Uri.of("https://oauth-server"), Uri.of("https://mcp-server/mcp")) { it == "my_oauth_token" },
        weatherTool
    )

    // 3. Start the server
    mcpServer.asServer(Jetty(3000)).start()

    println("MCP Server running on http://localhost:3000/mcp")
}

```



## Desktop Integration with Claude

It's no good having a server if you can't connect it to your favourite LLM - and this is the job of the
[http4k-mcp-desktop](https://github.com/http4k/mcp-desktop), which allows you to connect any MCP server directly to
Claude and other AI assistants with zero coding:

```bash
# Connect your MCP server to Claude
http4k-mcp-desktop --url http://localhost:3000/mcp
```

This opens up endless possibilities for enhancing AI assistants with access to your data and tools.

## Serverless Ready

Deploy your MCP capabilities to AWS Lambda, GCP Functions, and other FaaS platforms using http4k's serverless adapters:





```kotlin
package content.news.http4k_mcp_has_landed

import org.http4k.ai.mcp.model.McpEntity
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.Version
import org.http4k.ai.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.routing.mcpHttpNonStreaming
import org.http4k.serverless.ApiGatewayV2LambdaFunction
import org.http4k.serverless.AppLoader

class McpLambdaFunction : ApiGatewayV2LambdaFunction(AppLoader {
    mcpHttpNonStreaming(
        ServerMetaData(McpEntity.of("Serverless MCP"), Version.of("1.0.0")),
        BearerAuthMcpSecurity { it == "my-oauth-token" },
        weatherTool
    )
})

```



## What's Next?

In order to provide the best possible support for our users, we're offering the [http4k MCP SDK](https://mcp.http4k.org)
as a commercially licensed module as a part of our Pro tier and is automatically included in all http4k Enterprise
Edition subscriptions. As with all of the other Pro features, it is free to use for personal use, non-profit
organisations, non-commercial research, and qualifying small businesses (see our [commercial license](/commercial-license/) for exact terms).

We're really excited to see what you build with the http4k MCP SDK. If you have any questions, feedback, then hit us up
on GitHub or through the Kotlin Slack.

The http4k team is just at the start of it's MCP journey, We're diving deeper into what really matters: making AI
development safer, smarter, and more intuitive. Think easier security controls, more graceful ways to build autonomous
agents, and tools that just understand developers. We're not chasing buzzwords—we're creating an environment where
innovation meets responsibility, and complexity transforms into clarity.

**Less vibe, more value. That's the http4k way.**

Happy coding!

# /the http4k team

