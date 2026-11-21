# An MCP Server That Fits in a Tweet (and MCP Apps That Don't Need To)


<img class="imageMid my-4" src="./image.webp" alt="http4k MCP SDK - an iceberg showing the depth beneath a single line of Kotlin"/>

You can turn any MCP Capability - a Tool, a Resource, a Prompt - into a working, spec-compliant MCP server in fewer characters than an old-school tweet. No code generation, no YAML manifests, no runtime magic - just a function that does a thing, served over Streamable HTTP.





```kotlin
package content.news.an_mcp_server_in_a_tweet

import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.server.asServer
import org.http4k.routing.bind
import org.http4k.server.Jetty
import java.time.Instant.now

fun main() {
    val mcp = Tool("clock", "get the time") bind { Ok(Text(now())) }
    mcp.asServer(Jetty(3000)).start()
}

```



That's always been http4k's pitch, and now we've brought the party trick to MCP - any Capability can become a server with the same minimal code.

Yes, it's a contrived example - there's no security, no observability, no structure. But it's also a working MCP server that hands your LLM one of the essential tools it needs to function: knowing what time it is. And with zero dependencies, no reflection, and no classpath scanning, it won't eat your RAM while it's at it - which matters when every AI workload on your cluster is fighting for memory.

But with **v6.33.0.0**, the MCP story has grown well beyond party tricks. There's a *lot* of new goodness, so grab something warm and settle in.

## TL;DR - What's New?

- **Full 2025-11-25 MCP spec** support - including draft features like structured content in tool responses
- **MCP Apps** support (2026-01-26 spec) - server-rendered UI components living inside your AI client
- **http4k-ai-mcp-testing** module with `McpAppsHost` for local testing MCP Apps without Claude or NPX
- **OpenTelemetry MCP Semantic Conventions support** (v1.38.0) - proper observability for your MCP servers
- **OAuth with Resource Metadata** - the auth model the spec should have had from the start
- The **http4k Toolbox** now generates MCP server and MCP App projects - [toolbox.http4k.org](https://toolbox.http4k.org)
- **Passing MCP conformance tests** - we don't consider spec compliance optional

## The Spec, The Whole Spec, and Nothing But The Spec

The 2025-11-25 MCP specification brought a raft of cool new capabilities: Tasks, Elicitations, Sampling upgrades, and expanded Tool support. http4k now supports the lot.

We've also implemented draft spec features - including structured content in tool responses - because we want you to not have to wait for a committee to finish their minutes before they can ship. OAuth auth with resource metadata is now properly supported too, bringing the security model in line with how the rest of the industry does things.

Meanwhile, certain official JVM SDKs are still shipping their wares on... well, let's just say an earlier vintage. But if you're on the JVM and you want the latest spec, as of today there's really only one game in town. You can read the full spec [here](https://modelcontextprotocol.io/specification/2025-11-05).

## MCP Apps - UIs Inside Your AI

If you ask us (and literally nobody does!) [MCP Apps](https://modelcontextprotocol.io/specification/2026-01-26) are the most exciting addition to the MCP ecosystem in a while. They let your MCP server render rich UI components - HTML, CSS, the works - directly inside AI clients like Claude Code. Think interactive dashboards, form-based workflows, or data visualisations, all served from your server and displayed right where the user is chatting.

This is a genuine innovation for building rich agent experiences. No more describing a table in markdown and hoping the LLM formats it correctly - you can just get your MCP server to render it as intended without the user leaving the chat.

<img class="imageMid my-4" src="./mcp_app.webp" alt="An MCP App running inside Claude"/>

An MCP App combines Tools and Resources under the hood, but http4k wraps the wiring into a single `RenderMcpApp` Capability - so building one is just as concise as any other server:





```kotlin
package content.news.an_mcp_server_in_a_tweet

import org.http4k.ai.mcp.model.apps.McpAppResourceMeta
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.capability.extension.RenderMcpApp
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.core.Uri
import org.http4k.routing.mcp

fun MyMcpApp() = mcp(
    ServerMetaData("Greeter", "1.0.0"),
    NoMcpSecurity,
    RenderMcpApp("greet", "say hello", Uri.of("ui://hello"), McpAppResourceMeta()) {
        """
        <html>
        <body>
            <h1>UI goes here!</h1>
        </body>
        </html>
        """
    }
)

```



We've written a full tutorial to get you started: **[Build a Simple MCP App](/tutorial/build_a_simple_mcp_app/)**. Want to see one running? Just add [this](/mcp-sdk/mcp) custom connector to Claude. And if you'd rather skip the reading and get straight to code, [Toolbox](https://toolbox.http4k.org) can generate a complete MCP Server or App project for you in about a minute - or even longer if you actually want to read the full list of http4k modules.

## Test MCP Like You Mean It

And of course, once you've got a working MCP Server or App, you need to test it. We've extracted out all MCP testing utils into the new handy **`http4k-ai-mcp-testing`** module. You shouldn't have to test your MCPs by pointing Claude at it and squinting at the output - that's simply not good enough.

For standard MCP servers, `testMcpClient()` creates a fully in-memory test client. No network, no ports, no flaky CI. Just your server, your tests, and the truth:





```kotlin
package content.news.an_mcp_server_in_a_tweet

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dev.forkhandles.result4k.onFailure
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.model.ToolName
import org.http4k.lens.with
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.junit.jupiter.api.Test

class McpServerTest {

    private val name = Tool.Arg.string().required("name")

    private val mcpServer = mcp(
        ServerMetaData("Greeter", "1.0.0"), NoMcpSecurity,
        Tool("greet", "Say hello", name) bind { Ok(Text("Hello, ${name(it)}!")) }
    )

    @Test
    fun `can call a tool on the server`() {
        val client = mcpServer.testMcpClient().apply { start() }

        val tools = client.tools().list()
            .onFailure { error("") }

        assertThat(tools.size, equalTo(1))

        val result = client.tools()
            .call(ToolName.of("greet"), ToolRequest().with(name of "Bob"))
            .onFailure { error("") } as Ok

        assertThat((result.content?.firstOrNull() as Text).text, equalTo("Hello, Bob!"))
    }
}

```



This is the http4k way: if you can't test it in a unit test, it doesn't ship.

And as a bonus for the keen amongst you, `McpAppsHost` gives you a pure Kotlin host for testing MCP Apps locally - no Claude, no NPX, no "works on my machine" hand-waving. Point it at your MCP App servers (in-memory or remote) and it provides a lightweight web UI for interacting with them directly:





```kotlin
package content.news.an_mcp_server_in_a_tweet

import org.http4k.ai.mcp.apps.McpAppsHost
import org.http4k.ai.mcp.testing.McpClientFactory
import org.http4k.core.Uri
import org.http4k.server.Jetty
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val server = MyMcpApp().asServer(Jetty(9000)).start()

    val client = McpClientFactory.Http(Uri.of("http://localhost:${server.port()}/mcp"))

    val host = McpAppsHost(client).asServer(SunHttp(10000)).start()

    println("MCP Apps Host running on http://localhost:${host.port()}")
}

```



## See Everything with OpenTelemetry

MCP servers that you can't observe are MCP servers you can't trust. We've added support for the [OpenTelemetry MCP Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/), giving you proper server-side OTel convention spans just by plugging in a standardised `McpFilter` which will capture OTel traces across your MCP interactions, so your existing observability stack - Jaeger, Honeycomb, Datadog, whatever you fancy - just works. No custom instrumentation required.

## Ship It

We believe that http4k's MCP SDK is the most complete, most testable, and most spec-current MCP implementation on the JVM. And that's not marketing - it's just what happens when you spend every week shipping spec updates while maintaining the engineering standards your users expect. And our first users are us!

Our MCP libraries are included in the http4k [Pro tier](/pro/) and all [Enterprise Edition subscriptions](/enterprise/), with free use for personal projects, non-profit organisations, non-commercial research, and qualifying small businesses (see our [commercial license](/commercial-license/) for exact terms).

Questions? Feedback? War stories? Find us on GitHub or the [Kotlin Slack](https://kotlinlang.slack.com). We'd love to hear what you're building.

And if you want to hear about the functional design behind the MCP SDK, we'll be talking about it at [KotlinConf 2026](https://kotlinconf.com) in Munich - see you there!

Happy coding!

# /the http4k team

