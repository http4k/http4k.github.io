# Building an MCP App with http4k


This tutorial walks through building **Repo Health Checker** — an MCP App that displays an interactive UI inside an MCP host (like Claude Desktop), fetches
health metrics from the GitHub API, and lets users select metrics and a focus mode for analysis.

We'll use the [http4k MCP SDK](https://mcp.http4k.org) — a Kotlin-first MCP implementation that stays up to date with the latest protocol spec, embraces functional simplicity over annotation magic, and is totally testable from top to bottom. It also has first-class support for MCP Apps, which most other MCP SDKs don't offer yet.

By the end you will have:

- A Handlebars-based UI that runs inside the MCP host
- Two MCP tools — one visible only to the UI, one visible only to the model
- An MCP prompt that provides structured analysis instructions to the model
- A streaming MCP server wired together by composing MCP Capabilities
- A local test harness for development

> **Prerequisites:** Kotlin, Gradle, Java 21. Familiarity with [http4k](https://http4k.org) basics.

# MCP in 60 seconds

[MCP (Model Context Protocol)](https://modelcontextprotocol.io/specification) is a protocol for connecting AI models to external capabilities. An MCP **server**
exposes capabilities to a **host** application (such as Claude Desktop), which in turn makes them available to the model. See
the [http4k MCP SDK docs](/ecosystem/ai/reference/mcp/) for the full integration guide.

The protocol defines four standard capability types:

- **Tools** — functions the model can call to perform actions or retrieve information. This is the capability everyone obsesses about, but it's only one piece
  of the protocol.
- **Resources** — data the model can read (files, API responses, rendered content)
- **Prompts** — reusable message templates the model can expand
- **Completions** — argument auto-complete suggestions for tool and prompt parameters, so the host can offer typeahead as users fill in values

In http4k these are `ToolCapability`, `ResourceCapability`, `PromptCapability`, and `CompletionCapability` — all implement the `ServerCapability` interface.

### What is an MCP App?

[MCP Apps](https://modelcontextprotocol.io/docs/extensions/apps) extend the protocol with **interactive UIs that render inside the host**. An MCP App is
composed of two capabilities working together:

1. A **Tool** — when the model calls it, the host knows to display the associated UI
2. A **Resource** — serves the HTML content that the host renders in a sandboxed iframe

The UI can call back to the server using the MCP App SDK. These calls go through the host via the MCP protocol — not via direct HTTP requests to the server.

**Visibility** controls who can see and call each tool:

- `model` — only the LLM can call it (the default for standard tools)
- `app` — only the embedded UI can call it (for UI → server communication)

Both can be set on a single tool if needed.

### Composition all the way down

http4k is built on a single composability rule: small, typed pieces combine into larger pieces of the same shape. An `HttpHandler` is a function; a `Filter`
wraps one `HttpHandler` to produce another; `routes()` combines multiple handlers into one. The result is always the same type, so you can keep composing.

MCP in http4k follows the same principle. A `ServerCapability` is the atomic unit — a single tool, resource, or prompt. Capabilities compose into larger
groups (we'll see how in step 7). And an **MCP server** is a composition of three things:

1. **Server identity** (`ServerMetaData`) — name, version, and supported extensions
2. **Security** (`McpSecurity`) — how clients authenticate
3. **Capabilities** — one or more `ServerCapability` instances

That's the whole server. No registration step, no lifecycle hooks — just function composition.

# 1. Generate your project

Use the [http4k Toolbox](https://toolbox.http4k.org) to generate a project with these modules. Choose "Server-based application", and select the following on the subsequent screens - everything else can be left at default:

- HTTP server backend - **Jetty** - MCP server backend with support for SSE
- Templating library - **Handlebars** - HTML templating engine
- http4k AI integrations - **Model Context Protocol SDK**  - MCP SDK and MCP App SDK dependencies
- Testing & Tooling - **MCP SDK Testing utilities** — local test harness for MCP Apps

Finish the Wizard and download the generated project. Your `build.gradle.kts` will include:

```kotlin
dependencies {
    implementation(platform("org.http4k:http4k-bom:${http4kVersion}"))

    implementation("org.http4k:http4k-ai-mcp-sdk")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-template-handlebars")
    implementation("org.http4k:http4k-ai-mcp-testing")
}
```

You can delete all of the pre-existing content in the source directories.

# 2. Domain model

Start with an enum for the analysis focus mode, a data class to hold the user's selection, and a `ViewModel` for the Handlebars template:





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.template.ViewModel

enum class AnalysisFocus {
    contributor, dependency, benchmarking
}

data class RepoHealthSelection(val repo: String, val metrics: Map<String, String>, val focus: AnalysisFocus)

class HealthChecker : ViewModel

```



- `AnalysisFocus` defines three focus modes — contributor, dependency, and benchmarking — which weight the health analysis differently.
- `RepoHealthSelection` captures a repo name, a map of selected metric names to their values, and the chosen focus mode.
- `HealthChecker` is a `ViewModel` — the http4k interface that Handlebars uses to resolve the template by class name (`HealthChecker` maps to `HealthChecker.hbs`).

# 3. Save tool (UI → server)

This tool is called **from the UI** when the user clicks "Save". Because it should only be callable by the app (not by the LLM), we set
`visibility = app`. This means the host hides this tool from the model's tool list and only makes it available to the embedded app iframe via
`callServerTool()`.





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Meta
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.apps.McpAppMeta
import org.http4k.ai.mcp.model.apps.McpAppVisibility.app
import org.http4k.ai.mcp.model.enum
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.server.capability.ToolCapability
import org.http4k.ai.mcp.util.auto
import org.http4k.format.Moshi
import org.http4k.lens.MetaKey
import org.http4k.routing.bind

fun SaveHealthSelectionTool(selections: MutableMap<String, RepoHealthSelection>): ToolCapability {
    val repo = Tool.Arg.string().required("repo", "The GitHub repo in owner/name format")
    val metrics = Tool.Arg.string().required("metrics", "JSON-encoded map of selected metric names to values")
    val focus = Tool.Arg.enum<AnalysisFocus>().required("focus", "Analysis focus: contributor, dependency, or benchmarking")

    return Tool(
        name = "save_health_selection",
        description = "Save the selected repo health metrics for Claude to analyse",
        repo, metrics, focus,
        meta = Meta(MetaKey.auto(McpAppMeta).toLens() of McpAppMeta(visibility = listOf(app)))
    ) bind {
        val repoName = repo(it)
        val metricsMap = Moshi.asA<Map<String, String>>(metrics(it))
        val focusValue = focus(it)

        selections[repoName] = RepoHealthSelection(repoName, metricsMap, focusValue)

        Ok(listOf(Text("Saved ${metricsMap.size} metrics for $repoName with focus: $focusValue")))
    }
}

```



Key points:

- **`Tool.Arg` DSL** — `string()`, `enum<>()`, `.required()` build a typed schema for the tool's arguments. The `enum<AnalysisFocus>()` arg type automatically generates a schema with the valid enum values.
- **`visibility = app`** — the host shows this tool to the embedded app only; the LLM cannot call it.
- **`Moshi.asA<Map<String, String>>()`** — the metrics are passed as a JSON-encoded string from the UI and parsed server-side using Moshi. This is why we need the `http4k-format-moshi` dependency.
- **`bind`** — connects the `Tool` definition to a handler lambda. The `it` parameter is a typesafe map extracted using the arg lenses (`repo(it)`,
  `metrics(it)`, `focus(it)`).
- The `selections` map is a closure — no global state, no framework magic.

# 4. Get tool (model-only)

This tool lets the LLM read the saved selection for a given repo. It has `visibility = model` so the UI cannot call it — only the model can. This is the default for normal MCP
tools, but we set it explicitly here since we're in an MCP App context where both visibilities exist.





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.ToolResponse.Ok
import org.http4k.ai.mcp.model.Content.Text
import org.http4k.ai.mcp.model.Meta
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.apps.McpAppMeta
import org.http4k.ai.mcp.model.apps.McpAppVisibility.model
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.server.capability.ToolCapability
import org.http4k.ai.mcp.util.auto
import org.http4k.lens.MetaKey
import org.http4k.routing.bind

fun GetHealthSelectionTool(selections: MutableMap<String, RepoHealthSelection>): ToolCapability {
    val repo = Tool.Arg.string().required("repo", "The GitHub repo in owner/name format")

    return Tool(
        name = "get_health_selection",
        description = "Get the repo health metrics selected by the user.",
        repo,
        meta = Meta(MetaKey.auto(McpAppMeta).toLens() of McpAppMeta(visibility = listOf(model)))
    ) bind { args ->
        val repoName = repo(args)
        val current = selections[repoName]
        if (current == null) {
            Ok(listOf(Text("No health metrics have been saved for $repoName. Ask the user to open the repo health checker and select some metrics.")))
        } else {
            val metricsText = current.metrics.entries.joinToString("\n") { (name, value) -> "- $name: $value" }
            Ok(listOf(Text("Repo: ${current.repo}\nFocus: ${current.focus}\n\nSelected metrics:\n$metricsText")))
        }
    }
}

```



The tool takes a `repo` argument so the model can query for a specific repository's saved metrics. If no selection exists for that repo, it returns a helpful message prompting the user to open the health checker UI.

# 5. HTML UI

Create the Handlebars template at `src/main/resources/HealthChecker.hbs`. This is standard HTML that uses the [MCP App SDK](https://www.npmjs.com/package/@modelcontextprotocol/ext-apps) to call back to the server. You can put it in `src/main/resources`:





```html
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Repo Health Checker</title>
    <link href="https://unpkg.com/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        .metric-card { cursor: pointer; transition: all 0.2s; user-select: none; }
        .metric-card.selected { border-color: #0d6efd !important; background-color: #e7f1ff; }
        .metric-card .value { font-size: 1.5rem; font-weight: bold; }
        .focus-btn { flex: 1; }
        .focus-btn.active { background-color: #0d6efd; color: white; border-color: #0d6efd; }
    </style>
</head>
<body class="p-3" style="max-width: 600px;">
<h2>Repo Health Checker</h2>

<div class="input-group mb-3">
    <input type="text" class="form-control" id="repo" placeholder="owner/repo">
    <button class="btn btn-primary" id="fetchBtn">Fetch</button>
</div>

<div id="metrics" class="row g-2 mb-3" style="display: none;"></div>

<div id="focusSelector" class="d-flex gap-2 mb-3" style="display: none !important;">
    <button class="btn btn-outline-secondary focus-btn" data-focus="contributor">I want to contribute</button>
    <button class="btn btn-outline-secondary focus-btn" data-focus="dependency">Evaluating as dependency</button>
    <button class="btn btn-outline-secondary focus-btn" data-focus="benchmarking">General health check</button>
</div>

<button class="btn btn-success w-100 mb-3" id="analyseBtn" style="display: none;" disabled>Save</button>

<div id="status" class="mt-2 text-muted"></div>

<script type="module">
    import {App} from 'https://unpkg.com/@modelcontextprotocol/ext-apps@1.0.1/dist/src/app-with-deps.js';

    const METRICS = [
        {key: 'stars', label: 'Stars'},
        {key: 'open_issues', label: 'Open Issues'},
        {key: 'forks', label: 'Forks'},
        {key: 'open_prs', label: 'Open PRs'},
        {key: 'days_since_last_commit', label: 'Days Since Commit'},
        {key: 'days_since_last_release', label: 'Days Since Release'},
        {key: 'repo_age_days', label: 'Repo Age (days)'},
        {key: 'total_releases', label: 'Total Releases'}
    ];

    const app = new App({name: 'Repo Health Checker', version: '1.0.0'});
    await app.connect();

    const status = document.getElementById('status');
    const metricsDiv = document.getElementById('metrics');
    const focusSelector = document.getElementById('focusSelector');
    const analyseBtn = document.getElementById('analyseBtn');
    let selectedFocus = null;

    function el(tag, className, attrs) {
        const e = document.createElement(tag);
        e.className = className;
        Object.entries(attrs || {}).forEach(function(kv) { e.setAttribute(kv[0], kv[1]); });
        return e;
    }

    METRICS.forEach(function(m) {
        const col = el('div', 'col-3');
        const card = el('div', 'card metric-card p-2 text-center', {'data-metric': m.key});
        const label = el('div', 'text-muted small');
        label.textContent = m.label;
        const value = el('div', 'value', {id: 'val-' + m.key});
        value.textContent = '-';
        card.append(label, value);
        col.appendChild(card);
        metricsDiv.appendChild(col);
    });

    function daysSince(dateStr) {
        if (!dateStr) return 'N/A';
        return Math.floor((Date.now() - new Date(dateStr).getTime()) / (1000 * 60 * 60 * 24)).toString();
    }

    function updateAnalyseButton() {
        analyseBtn.disabled = !(document.querySelectorAll('.metric-card.selected').length > 0 && selectedFocus);
    }

    document.querySelectorAll('.metric-card').forEach(card => {
        card.addEventListener('click', () => {
            card.classList.toggle('selected');
            updateAnalyseButton();
        });
    });

    document.querySelectorAll('.focus-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.focus-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            selectedFocus = btn.dataset.focus;
            updateAnalyseButton();
        });
    });

    document.getElementById('fetchBtn').addEventListener('click', async () => {
        const repo = document.getElementById('repo').value.trim();
        if (!repo) return;

        status.textContent = 'Fetching metrics...';

        try {
            const base = 'https://api.github.com/repos/' + repo;
            const [repoData, prsData, commitsData, releasesResp] = await Promise.all([
                fetch(base).then(r => r.json()),
                fetch(base + '/pulls?state=open&per_page=1').then(r => r.json()),
                fetch(base + '/commits?per_page=1').then(r => r.json()),
                fetch(base + '/releases?per_page=1')
            ]);
            const releasesData = await releasesResp.json();

            const lastCommitDate = commitsData?.[0]?.commit?.committer?.date;
            const lastReleaseDate = releasesData?.[0]?.published_at;

            const linkHeader = releasesResp.headers.get('link') || '';
            const lastPart = linkHeader.split(',').filter(function(p) { return /rel=.last/.test(p); })[0];
            const lastMatch = lastPart && lastPart.match(/&page=(\d+)/);
            const totalReleases = lastMatch ? parseInt(lastMatch[1]) : (Array.isArray(releasesData) ? releasesData.length : 0);

            const values = {
                stars: (repoData.stargazers_count ?? '-').toString(),
                open_issues: (repoData.open_issues_count ?? '-').toString(),
                forks: (repoData.forks_count ?? '-').toString(),
                open_prs: Array.isArray(prsData) ? prsData.length.toString() : '0',
                days_since_last_commit: daysSince(lastCommitDate),
                days_since_last_release: daysSince(lastReleaseDate),
                repo_age_days: daysSince(repoData.created_at),
                total_releases: totalReleases.toString()
            };

            METRICS.forEach(function(m) {
                document.getElementById('val-' + m.key).textContent = values[m.key];
            });

            document.querySelector('.focus-btn').click();

            metricsDiv.style.display = '';
            focusSelector.style.cssText = '';
            analyseBtn.style.display = '';
            status.textContent = 'Select metrics, pick a focus, then click Save.';
        } catch (e) {
            status.textContent = 'Error: ' + e.message;
        }
    });

    analyseBtn.addEventListener('click', async () => {
        const repo = document.getElementById('repo').value.trim();
        const selected = {};

        document.querySelectorAll('.metric-card.selected').forEach(card => {
            const value = card.querySelector('.value').textContent;
            if (value !== '-') selected[card.dataset.metric] = value;
        });

        status.textContent = 'Saving selection...';

        await app.callServerTool({
            name: 'save_health_selection',
            arguments: {repo, metrics: JSON.stringify(selected), focus: selectedFocus}
        });

        status.textContent = 'Done. Ask Claude for your health report!';
    });
</script>
</body>
</html>

```



The UI displays eight metric cards (stars, open issues, forks, open PRs, days since commit, days since release, repo age, total releases) and three focus mode buttons (contributor, dependency, benchmarking). Users enter a repo name, fetch metrics from the GitHub API, select the cards they want analysed, pick a focus, and save.

Key implementation details:

1. **`new App(...).connect()`** — establishes a message channel between the embedded iframe and the MCP host.
2. **`app.callServerTool(...)`** — calls our `save_health_selection` tool on the MCP server, through the host. This is how the UI communicates back to the
   server — it goes through the MCP protocol, not a direct HTTP call.
3. **Multiple GitHub API endpoints** — the UI fetches from `/repos`, `/pulls`, `/commits`, and `/releases` in parallel to gather all metrics.
4. **Link header parsing** — total releases are extracted from GitHub's pagination `Link` header rather than fetching all pages.

The GitHub API calls (`fetch`) are direct browser requests — this works because we declare the appropriate Content Security Policy domains in the next step.

# 6. UI renderer

`RenderMcpApp` is a convenience that bundles two capabilities together:

1. A **Tool** — when the model calls it, the host knows to display the associated UI (via the `uri` in the tool's metadata)
2. A **Resource** — serves the actual HTML content at that URI

This is the core pattern of an MCP App: a tool triggers display, a resource provides content. `RenderMcpApp` also declares CSP metadata so the host knows which
external domains the UI needs:





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.model.Domain
import org.http4k.ai.mcp.model.apps.Csp
import org.http4k.ai.mcp.model.apps.McpAppResourceMeta
import org.http4k.ai.mcp.server.capability.extension.RenderMcpApp
import org.http4k.core.Uri
import org.http4k.template.TemplateRenderer

fun HealthCheckerUi(templates: TemplateRenderer) = RenderMcpApp(
    name = "show_repo_health_checker",
    description = "Display the repo health checker UI",
    uiUri = Uri.of("ui://repo-health-checker"),
    meta = McpAppResourceMeta(
        csp = Csp(
            connectDomains = listOf(Domain.of("https://unpkg.com"), Domain.of("https://api.github.com")),
            resourceDomains = listOf(Domain.of("https://unpkg.com"))
        )
    )
) { templates(HealthChecker()) }

```



- **`resourceDomains`** — domains the UI can load stylesheets/scripts from (Bootstrap CSS, MCP App SDK JS).
- **`connectDomains`** — domains the UI can make fetch requests to (GitHub API, unpkg for ES module imports).
- The function takes a `TemplateRenderer` parameter — this is the compiled Handlebars renderer, injected from the app composition step.
- The trailing lambda `{ templates(HealthChecker()) }` renders the Handlebars template to an HTML string.

Note that `RenderMcpApp` returns a `CapabilityPack` (not a single `ServerCapability`) because it bundles both a tool and a resource.

# 7. Prompt capability

The `AnalyseRepoHealth` prompt provides structured instructions to the model for analysing a repo's health metrics. This is the third MCP capability type we use — after tools and resources (via MCP Apps).





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.PromptResponse
import org.http4k.ai.mcp.model.Prompt
import org.http4k.ai.mcp.server.capability.PromptCapability
import org.http4k.ai.model.Role
import org.http4k.lens.string
import org.http4k.routing.bind

fun AnalyseRepoHealth(): PromptCapability {
    val repo = Prompt.Arg.string().required("repo", "The GitHub repo in owner/name format")

    return Prompt(
        "analyse_repo_health",
        "Instructions for analysing a GitHub repo's health metrics. Call get_health_selection first to get the focus and metrics values.",
        repo
    ) bind { req ->
        val repoName = repo(req)
        PromptResponse.Ok(
            Role.User, """
You are a GitHub repository health analyst. Analyse **$repoName** using the focus mode and metrics from the most recent get_health_selection tool response to produce a health report.

## Focus Modes

Weight your analysis based on the focus:
- **contributor**: Prioritise activity recency (days since commit), issue volume, PR count, and community signals (stars, forks). A healthy project for contributors has recent commits, manageable open issues, and active PRs.
- **dependency**: Prioritise release recency (days since release), issue count (indicates bugs), and stability signals (stars as social proof). A healthy dependency has recent releases, few open issues, and broad adoption.
- **benchmarking**: Weight all metrics equally for a balanced overview. No single metric is prioritised.

## Scoring Instructions

For each metric, assign:
1. A traffic-light rating: GREEN, AMBER, RED, or SKULL (for truly alarming values)
2. A score out of 10
3. A one-line verdict explaining the rating

Use these thresholds as guidance:

| Metric              | GREEN        | AMBER         | RED           | SKULL           |
|---------------------|--------------|---------------|---------------|-----------------|
| stars               | > 1000       | 100 - 1000    | < 100         | < 10            |
| forks               | > 200        | 50 - 200      | < 50          | < 5             |
| open_issues         | < 50         | 50 - 200      | > 200         | > 1000          |
| open_prs            | < 10         | 10 - 30       | > 30          | > 100           |
| days_since_commit   | < 7          | 7 - 30        | > 30          | > 365           |
| days_since_release  | < 30         | 30 - 90       | > 90          | > 365           |
| repo_age_days       | > 365        | 90 - 365      | < 90          | < 14            |
| total_releases      | > 50         | 10 - 50       | < 10          | 0               |

**Note:** When scoring `total_releases`, consider it relative to `repo_age_days`. A young repo with few releases is fine; an old repo with few releases is a red flag.

## Output Format

Produce:
1. A table of metrics with traffic light, score/10, and one-line verdict
2. An overall score out of 10 (weighted by focus mode)
3. A summary paragraph with your overall assessment
""".trimIndent()
        )
    }
}

```



Key points:

- **`Prompt.Arg` DSL** — similar to `Tool.Arg`, this defines typed arguments for the prompt. Here we take the repo name so the prompt can reference it.
- **`PromptResponse`** — returns a message with a role (`Role.User`) and content. The model receives this as a user message containing detailed scoring instructions.
- **Structured analysis** — the prompt defines focus mode weightings, scoring thresholds for each metric, and an output format. This gives the model consistent, repeatable instructions rather than relying on ad-hoc prompting.

When a user selects this prompt in Claude Desktop, the model receives the full scoring rubric and knows to call `get_health_selection` to retrieve the saved metrics.

# 8. App composition

A `CapabilityPack` is a container that groups multiple `ServerCapability` instances into a single unit. It enables modularity — you can compose an app from independent capability packs and combine them freely.

Here we combine the UI renderer (itself a pack of tool + resource), the save tool, the get tool, and the prompt capability. The closure pattern is idiomatic http4k — capabilities
share state through captured variables, no DI container needed:





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.server.capability.CapabilityPack
import org.http4k.template.HandlebarsTemplates

fun RepoHealthCheckerApp(): CapabilityPack {
    val templates = HandlebarsTemplates().CachingClasspath()
    val selections = mutableMapOf<String, RepoHealthSelection>()

    return CapabilityPack(
        HealthCheckerUi(templates),
        SaveHealthSelectionTool(selections),
        GetHealthSelectionTool(selections),
        AnalyseRepoHealth()
    )
}

```



The `selections` map is captured by both tool closures. When the UI saves a selection, the model can immediately read it. The `templates` renderer is passed to `HealthCheckerUi` so it can render the Handlebars template. No database, no DI framework — just closures. (This in-memory approach is fine for a tutorial; in production you'd persist state to a database or external store.)

# 9. MCP server

This is where the three parts of an MCP server come together: server identity, security, and capabilities. `mcp` composes them into a
`PolyHandler` — http4k's type for services that speak multiple protocol types (here HTTP + SSE for the streaming transport):





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.model.apps.McpApps
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.withExtensions
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.core.then
import org.http4k.filter.PolyFilters
import org.http4k.routing.mcp

fun RepoHealthChecker() =
    PolyFilters.CatchAll()
        .then(
            mcp(
                ServerMetaData("Repo Health Checker", "0.0.0").withExtensions(McpApps),
                NoMcpSecurity,
                RepoHealthCheckerApp(),
            )
        )

```



- **`PolyFilters.CatchAll()`** — a filter that catches any unhandled exceptions and returns a 500 response instead of crashing the server. This wraps the entire MCP handler for robustness.
- **`mcp`** — creates a `PolyHandler` that speaks
  the [MCP Streamable HTTP transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports) (HTTP for requests, SSE for server-pushed
  events).
- **`withExtensions(McpApps)`** — advertises MCP Apps support in the server metadata so the host knows to look for app resources.
- **`NoMcpSecurity`** — no auth for local development. Replace with real security for production.

# 10. Main entry point





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val server = RepoHealthChecker().asServer(Jetty(9000)).start()

    println("Server started on " + server.port())
}

```



Run this and your MCP server is listening on `http://localhost:9000`.

# 11. Local test harness

In production, an MCP App renders inside a host like Claude Desktop. During development you don't want to restart Claude Desktop every time you change a
template or tweak a tool — you need a faster feedback loop.

`McpAppsHost` solves this. It is a lightweight standalone web server that acts as an MCP host: it connects to your MCP server, discovers its capabilities, and
renders any MCP Apps in a browser tab. You get the full MCP protocol stack (tool calls, visibility rules, CSP enforcement) without needing a real AI host in the
loop.

This uses the `http4k-ai-mcp-testing` dependency we added in step 1.





```kotlin
package content.tutorial.build_a_simple_mcp_app

import org.http4k.ai.mcp.apps.McpAppsHost
import org.http4k.ai.mcp.testing.McpClientFactory
import org.http4k.core.Uri
import org.http4k.server.Jetty
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val server = RepoHealthChecker().asServer(Jetty(9000)).start()

    val client = McpClientFactory.Http(Uri.of("http://localhost:${server.port()}/mcp"))

    val host = McpAppsHost(client).asServer(SunHttp(10000)).start()

    println("MCP Apps Host running on http://localhost:${host.port()}")
}

```



Run this and open `http://localhost:10000` in your browser. You will see the Health Checker UI rendered in the test host. You can fetch metrics, select cards, pick a focus mode, and save — all going through the full MCP protocol stack.

# 12. Connect to Claude Desktop

Claude Desktop connects to remote MCP servers via **Settings > Connectors**. Since it requires HTTPS, the simplest way to expose your local server is with a [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/):

```bash
npx cloudflared tunnel --url http://localhost:9000
```

This prints a public URL like `https://xxx-yyy-zzz.trycloudflare.com`.

Then:

1. Start the MCP server (`RepoHealthCheckerMain.kt`)
2. Start the Cloudflare tunnel
3. In Claude Desktop, go to **Settings > Connectors** and add a new connector with the tunnel URL (appending `/mcp` — e.g. `https://xxx-yyy-zzz.trycloudflare.com/mcp`). 
4. Start a new chat and ask Claude to analyse a Github repo: "Let's analyse a github repo using the analyser tool"
5. Use the UI to fetch metrics for a repo, select the cards you want analysed, pick a focus mode, and save (using the `save_health_selection` tool)
6. Outside of the MCP App UI in the chat, select the prompt `analyse_repo_health` from the Claude connectors prompt list, and input the name of the repo you just saved. It will call the `get_health_selection` tool to retrieve the saved metrics and then perform the analysis.

> **Note:** The tunnel URL changes every time you restart `cloudflared`, so you'll need to update the connector each time.

# Recap

| Piece            | File                         | Role                                                           |
|------------------|------------------------------|----------------------------------------------------------------|
| Domain model     | `model.kt`                   | `AnalysisFocus` enum, `RepoHealthSelection` data class, `HealthChecker` ViewModel |
| Save tool        | `SaveHealthSelectionTool.kt` | UI → server (visibility: `app`)                                |
| Get tool         | `GetHealthSelectionTool.kt`  | Model reads state (visibility: `model`)                        |
| HTML UI          | `HealthChecker.hbs`          | Handlebars template with metric cards, focus selector, MCP App SDK |
| Renderer         | `HealthCheckerUi.kt`         | Registers UI as MCP App resource with CSP                      |
| Composition      | `RepoHealthCheckerApp.kt`    | `CapabilityPack` wiring with closure state                     |
| Prompt           | `AnalyseRepoHealth.kt`       | Structured analysis instructions for the model                 |
| MCP server       | `RepoHealthChecker.kt`       | Streaming HTTP transport with CatchAll filter                  |
| Entry point      | `RepoHealthCheckerMain.kt`   | `main()`                                                       |
| Test harness     | `RunMcpAppAndHost.kt`        | `McpAppsHost` for local browser testing                        |

