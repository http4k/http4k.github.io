# Wiretap


## What is Wiretap?

When something goes wrong in a distributed system, the hardest part is understanding what actually happened. Which services were called? What did the requests and responses look like? Where did the time go? Where did the error originate?

**http4k Wiretap** answers these questions by capturing everything — OpenTelemetry traces, HTTP traffic, logs, and events — and presenting it in rich, shareable reports. It has two components:

**[Intercept](#intercept-junit-extension)** is a JUnit extension that instruments your tests. Add one annotation and every test automatically captures traces, traffic, and console output. On failure (or always, if you prefer), Wiretap generates a self-contained HTML report with trace timelines, sequence diagrams, interaction topology, error isolation, and critical path analysis. It also produces a [Living Test Document](#living-test-document) — a markdown specification of what your test actually exercised, with HTTP request/response contracts you can commit to your repo.

**[Wiretap Console](#wiretap-console)** is a pure Kotlin reverse proxy that sits in front of your running application. All traffic flows through it and is captured automatically. The console UI is served at `/_wiretap/` on the same port — real-time traffic monitoring, OpenTelemetry trace visualisation, chaos engineering, MCP debugging, and an embedded HTTP client. Every feature is also available as [MCP tools](#mcp-tools) at `/_wiretap/mcp` for AI-assisted debugging.

### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k.pro:http4k-wiretap")
}
```

## Intercept: JUnit Extension

### Zero-Config

Add `@ExtendWith(Intercept::class)` to any test class. OpenTelemetry traces, logs, and events are captured automatically. On test failure, a self-contained HTML report is generated.





```kotlin
package content.ecosystem.http4k.reference.wiretap

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.wiretap.junit.Intercept
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(Intercept::class)
class ZeroConfigTest {

    fun App() = ServerFilters.OpenTelemetryTracing()
        .then(routes("/{path:.*}" bind GET to { Response(OK).body("hello!") }))

    @Test
    fun `can capture otel traces with zero config`() {
        App()(Request(GET, "/api"))
    }
}

```



### HTTP Traffic Capture

Use `@RegisterExtension` to capture all HTTP traffic flowing through your app:





```kotlin
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.wiretap.junit.Intercept
import org.http4k.wiretap.junit.RenderMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HttpTrafficTest {

    private val app = routes(
        "/api" bind GET to { Response(OK).body("hello") }
    )

    // Captures all HTTP traffic through the app
    @RegisterExtension
    @JvmField
    val intercept = Intercept(RenderMode.Always) { app }

    @Test
    fun `requests are captured`(http: HttpHandler) {
        val response = http(Request(GET, "/api"))
        assertThat(response.bodyString(), equalTo("hello"))
    }
}

```



### Multi-Service with OTel

For apps that make outbound HTTP calls, instrument the client with `http()` and `otel()` from the `Context` receiver. This captures both inbound and outbound traffic with full OpenTelemetry trace context across service boundaries.





```kotlin
package content.ecosystem.http4k.reference.wiretap

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.opentelemetry.api.OpenTelemetry
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.ServerFilters
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.wiretap.junit.Intercept
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MyAppTest {

    @RegisterExtension
    @JvmField
    val intercept = Intercept {
        // http() wraps an outbound client with traffic recording and OTel tracing
        val next = http { Response(OK).body("from downstream") }

        // Build your app with the instrumented client
        val openTelemetry = otel("my-service")

        MyApp(next, openTelemetry)
    }

    private fun MyApp(httpClient: HttpHandler, otel: OpenTelemetry): RoutingHttpHandler {
        val downstreamClient = ClientFilters.OpenTelemetryTracing(otel).then(httpClient)

        return ServerFilters.OpenTelemetryTracing(otel)
            .then(routes("/api" bind GET to { downstreamClient(Request(GET, "http://downstream/data")) }))
    }

    // HttpHandler is injected — sends requests through Intercept's recording filters
    @Test
    fun `requests are captured with full trace context`(http: HttpHandler) {
        assertThat(http(Request(GET, "/api")).bodyString(), equalTo("from downstream"))
    }
}

```



The `Context` receiver provides:
- `http()` — wraps an outbound HTTP client with traffic recording and OTel tracing
- `otel(serviceName)` — creates an OpenTelemetry instance that records to the trace store
- `clock()` — deterministic clock
- `random()` — deterministic random

By default, `GlobalOpenTelemetry` is used for trace capture. Use `otel()` for explicit control.

### Parameter Injection

Intercept injects test parameters based on their type:

**`ChaosEngine`** — controls failure injection on outbound calls:





```kotlin
package content.ecosystem.http4k.reference.wiretap

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.chaos.ChaosBehaviours.ReturnStatus
import org.http4k.chaos.ChaosEngine
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.wiretap.junit.Intercept
import org.http4k.wiretap.junit.RenderMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ChaosTest {

    @RegisterExtension
    @JvmField
    val intercept = Intercept(RenderMode.Always) {
        MyApp(http { Response(OK).body("downstream") }, otel("my-service"))
    }

    // ChaosEngine is injected — controls failure injection on outbound calls
    @Test
    fun `outbound calls fail when chaos is enabled`(http: HttpHandler, chaos: ChaosEngine) {
        chaos.enable(ReturnStatus(INTERNAL_SERVER_ERROR))

        val response = http(Request(GET, "/api"))
        assertThat(response.status, equalTo(INTERNAL_SERVER_ERROR))
    }
}

```



**`McpClient`** — connects to an MCP server under test (use `Intercept.poly` for `PolyHandler` apps):





```kotlin
package content.ecosystem.http4k.reference.wiretap

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.greaterThan
import org.http4k.ai.mcp.client.McpClient
import org.http4k.ai.mcp.coerce
import org.http4k.ai.mcp.protocol.messages.McpTool
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.wiretap.junit.Intercept
import org.http4k.wiretap.junit.RenderMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class McpTest {

    @RegisterExtension
    @JvmField
    val intercept = Intercept.poly(RenderMode.Always) {
        MyMcpServer(http { Response(OK) }, otel("mcp-server"))
    }

    // McpClient is injected — connects to the MCP server under test
    @Test
    fun `can list tools via MCP`(mcpClient: McpClient) {
        mcpClient.start()
        val tools = mcpClient.tools().list().coerce<List<McpTool>>()
        assertThat(tools.size, greaterThan(0))
    }
}

```



### RenderMode

- `RenderMode.OnFailure` (default) — generate reports only when tests fail
- `RenderMode.Always` — generate reports for every test
- `RenderMode.Never` — disable report generation

## Test Reports

Reports are written to `build/reports/http4k/wiretap/`.

### HTML Report

Self-contained HTML report with three main sections:

**Trace Detail** — Gantt-style span timeline with expandable attributes, events, and logs. Below the timeline, a set of diagrams for each trace:
- **Sequence** — temporal flow across services with HTTP status codes and duration
- **Interactions** — service topology with call count and total duration per edge
- **Errors** — filtered sequence showing only paths to error spans
- **Critical Path** — filtered sequence showing only the slowest root-to-leaf path
- **Timing** — span breakdown table sorted by duration with percentage bars

**HTTP Traffic** — full request/response detail with one-click cURL copy

**Stdout/Stderr** — captured console output from the test

### Living Test Document

Auto-generated markdown file alongside the HTML report. Contains:
- Mermaid sequence diagrams per trace
- MCP operation payloads (when [detail span modifiers](/ecosystem/ai/reference/mcp/#opentelemetry-span-modifiers) are enabled)
- Span events (exceptions, state transitions)
- HTTP transactions with headers table and collapsible bodies

The document uses deterministic labels — MCP operations show as `tools/call show_ui`, `resources/read ui://a-ui` etc.

## Trace Diagrams

All diagrams are available in both the Wiretap web UI and Intercept reports.

| Diagram | Description |
|---------|-------------|
| **Sequence** | Temporal flow with HTTP status codes and duration annotations |
| **Interactions** | Service topology with call count and total duration per edge |
| **Timing** | Span breakdown sorted by duration with percentage bars |
| **Errors** | Filtered sequence showing only paths to error spans |
| **Critical Path** | Filtered sequence showing only the slowest root-to-leaf path |

## Wiretap Console

Add the full Wiretap console to your app using either a **LocalTarget** (in-process) or **RemoteTarget** (proxy to a running server):





```kotlin
package content.ecosystem.http4k.reference.wiretap

import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.server.uri
import org.http4k.wiretap.LocalTarget
import org.http4k.wiretap.RemoteTarget
import org.http4k.wiretap.Wiretap

// Local: app runs in-process, Wiretap instruments it directly
val local = Wiretap(LocalTarget {
    routes("/api" bind GET to { Response(OK) })
}).asServer(Jetty(9000)).start()

// Remote: app already running on a server, Wiretap proxies to it
val remoteApp = routes("/api" bind GET to { Response(OK) })
    .asServer(Jetty(0)).start()

val remote = Wiretap(RemoteTarget(remoteApp.uri()))
    .asServer(Jetty(9001)).start()
// Console at http://localhost:9001/_wiretap/

```



### MCP Tools

All Wiretap features are exposed as MCP tools at `/_wiretap/mcp`:
- `get_trace_diagrams` — all diagrams for a trace
- `export_trace_markdown` — living document for a trace
- `list_traces`, `list_transactions` — browse captured data
- Traffic replay, chaos control, and more

### Markdown Export

Click **Export Markdown** in the trace detail toolbar to download a living document for any trace. Also available via the `export_trace_markdown` MCP tool.

