# Testing: Chaos



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-testing-chaos")
}
```

### About
The http4k Chaos module provides the facility to statically or dynamically inject failure modes into http4k applications, such as random HTTP failures, killing of processes, and extra latency injection. By modelling these modes, it is possible to plan for mitigation of particular scenarios on a wider scale, resulting either from failures within your system boundary, or those caused by dependent remote HTTP services.

The [Principles of Chaos Engineering](http://principlesofchaos.org/) approach was made prominent by Netflix open-sourcing the [Simian Army](https://github.com/Netflix/SimianArmy) libraries. 

### API concepts
To understand the API, these domain-language concepts are important, all modelled as simple Kotlin typealiases and interfaces in order that API users can create their own:

#### Behaviours: `typealias Behaviour = Filter` 
A **Behaviour** applies the failure mode to the HTTP call. This could involve blocking a thread permanently, introducing extra latency into an HTTP service, or even causing a Stack Overflow or Killing the running process.

-----------------------------------
|Behaviour function|Effect|as JSON|
|------------------|------|-------|
|Latency|Adds random latency to a call between the min and max durations|`{"type":"latency","min":"PT0.1S","max":"PT0.3S"}`|
|ThrowException|Throws an uncaught Exception with the supplied message|`{"type":"throw","message":"foo"}`|
|ReturnStatus|Returns an HTTP response with the specified HTTP status code|`{"type":"status","status":404}`|
|ReturnResponse|Returns an HTTP response with the specified response. `body` and `headers` are optional|`{"type":"response","status":500,"body":"a-body","headers":{"x-name":"value","x-other": "value2"}}`|
|NoBody|Completes the call normally, but strips the body content from the response|`{"type":"body"}`|
|EatMemory|Forces an OOM exception|`{"type":"memory"}`|
|KillProcess|Kills the Java process with a 1 error code|`{"type":"kill"}`|
|StackOverflow|Generates a StackOverflow|`{"type":"overflow"}`|
|BlockThread|Permanently blocks the request thread|`{"type":"block"}`|
|None|Requests complete normally|`{"type":"none"}`|

#### Triggers: `typealias Trigger = (req: Request) -> Boolean`
A **Trigger** is just a predicate which determines if an HTTP call should have an `Behaviour` applied to it. `Triggers` can be stateless, based on the request content, or stateful - deadlines or countdowns.

-----------------------------------------------
|Trigger function|Activation condition|as JSON|
|----------------|--------------------|-------|
|Deadline|After an instant in time|`{"type":"deadline","endTime":"1970-01-01T00:00:00Z"}`|
|Delay|After a specified period (since construction)|`{"type":"delay","period":"PT0.1S"}`|
|Countdown|For the first n requests only|`{"type":"countdown","count":"1"}`|
|Request|If the request meets the criteria set out in the specification. All but `method` are Regex patterns, and all are optional|`{"type":"request","method":"get","path":".*bob","queries":{"query":".*query"},"headers":{"header":".*header"},"body":".*body"}`|
|Once|For the first request only|`{"type":"once"}`|
|PercentageBased|Applies to a certain (randomly decided) percentage of requests|`{"type":"percentage", "percentage":100}`|
|Always|For all requests|`{"type":"always"}`|

#### Stages: `interface Stage: (Request) -> Filter?`
A **Stage** provides the lifecycle for applying a behaviour, and applies until a `Trigger` indicates that the stage is complete. `Stages` can be chained with `then()`, or can be produced by combining a `Behaviour` and a `Trigger` using `appliedWhen()`.

----------------------------------------
|Stage function|Lifecycle notes|as JSON|
|--------------|---------------|-------|
|Wait|Does nothing while active|`{"type":"wait","until":<insert trigger json>}`|
|Repeat|Loops through the stages and then repeats|`{"type":"repeat","stages":[<insert stage json elements>],"until":<insert trigger json>}`|
|(Triggered)|Combines a Trigger and a Behaviour |`{"type":"trigger","behaviour":{"type":"body"},"trigger":<insert trigger json>,"until":<insert trigger json>}}`|

### Manually injecting Chaos
For use in automated test suites, it is simple to define the Chaos behaviour programmatically using the API and then use the `ChaosEngine` to add it onto an existing application.

#### Code





```kotlin
package content.ecosystem.http4k.reference.chaos

import org.http4k.chaos.ChaosBehaviours.ReturnStatus
import org.http4k.chaos.ChaosEngine
import org.http4k.chaos.ChaosStages.Wait
import org.http4k.chaos.ChaosTriggers.PercentageBased
import org.http4k.chaos.appliedWhen
import org.http4k.chaos.then
import org.http4k.chaos.until
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.server.SunHttp
import org.http4k.server.asServer

val client = OkHttp()

fun main() {

    // chaos is split into "stages", which can be triggered by specific request or time-based criteria
    val doNothingStage = Wait.until { tx: Request -> tx.method == POST }
    val errorStage = ReturnStatus(INTERNAL_SERVER_ERROR).appliedWhen(PercentageBased(50))

    // chain the stages together with then() and create the Chaos Engine (activated)
    val engine = ChaosEngine(doNothingStage.then(errorStage)).enable()

    val svc: HttpHandler = { Response(OK).body("A normal response") }
    engine.then(svc).asServer(SunHttp(9000)).start().use {
        repeat(10) { performA(GET) }

        // this triggers the change in behaviour
        performA(POST)

        repeat(10) { performA(GET) }

        // disable the chaos
        engine.disable()

        repeat(10) { performA(GET) }
    }
}

fun performA(method: Method) =
    println(method.name + " got a " + client(Request(method, "http://localhost:9000")).status)

```



### Dynamic behaviour injection using Chaos Controls
For use in deployed environments or when experimenting with the reaction of systems to failure, there is the need to vary (and otherwise control) the Chaos behaviour that an application or downstream fake exhibits, in order to simulate periods of failures and then observe the after-effects.

The module contains a simple extension method `HttpHandler.withChaosEngine()` that decorates an existing http4k application with the ability to dynamically inject Chaos behaviour using a set of RPC-style endpoints. This API is presented via an OpenAPI specification, which allows it to be controlled by a simple OpenApi client. 

Apart from being able to turn the Chaos on/off and check the status, the most powerful endpoint in ChaosEngine lives at `/activate/new`. By POSTing a JSON definition of the required behaviour, this JSON is deserialised into actual Chaos behaviours which can be then activated in the application. The supported JSON formats of the various Chaos concepts are defined above, but by way of an example, POSTing this piece of JSON would:

1. Wait for 100 seconds
1. Always return an HTTP 404 (Not Found) status for 10 requests
1. Repeat the above until Big Ben strikes in the New Year 2020.

```json
[
  {
    "type": "repeat",
    "stages": [
      {
        "type": "wait",
        "until": {
          "type": "delay",
          "period": "PT100S"
        }
      },
      {
        "type": "trigger",
        "behaviour": {
          "type": "status",
          "status": 404
        },
        "trigger": {
          "type": "always"
        },
        "until": {
          "type": "countdown",
          "count": "10"
        }
      }
    ],
    "until": {
      "type": "deadline",
      "endTime": "2020-01-01T00:00:00Z"
    }
  }
]
```

#### Code





```kotlin
package content.ecosystem.http4k.reference.chaos

import org.http4k.chaos.withChaosApi
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.CorsPolicy.Companion.UnsafeGlobalPermissive
import org.http4k.filter.ServerFilters
import org.http4k.filter.ServerFilters.Cors
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    Cors(UnsafeGlobalPermissive)
        .then(ServerFilters.CatchAll())
        .then { Response(OK).body("A normal response") }
        .withChaosApi()
        .asServer(SunHttp(9000))
        .start()
        .also { println("Visit the app at http://localhost:9000 or see the OpenApi at https://http4k.org/openapi3/?url=http://localhost:9000/chaos") }
}

```



### Interacting with ChaosEngine using an HTTP client

#### Code





```kotlin
package content.ecosystem.http4k.reference.chaos

import org.http4k.chaos.ChaosBehaviours.ReturnStatus
import org.http4k.chaos.ChaosEngine
import org.http4k.chaos.withChaosApi
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes

fun main() {
    val app = routes("/" bind routes("/" bind GET to { Response(OK).body("hello!") }))

    val appWithChaos = app.withChaosApi(ChaosEngine(ReturnStatus(NOT_FOUND)))

    println(">>chaos is deactivated by default")
    println(appWithChaos(Request(GET, "/chaos/status")).bodyString())
    println(appWithChaos(Request(GET, "/")).status)

    println(">>activate the default chaos")
    println(appWithChaos(Request(POST, "/chaos/activate")).bodyString())
    println(appWithChaos(Request(GET, "/")).status)

    println(">>deactivate the default chaos")
    println(appWithChaos(Request(POST, "/chaos/deactivate")).bodyString())
    println(appWithChaos(Request(GET, "/")).status)

    println(">>set the chaos dynamically")
    val alwaysReturn418 = """[
        {
          "type": "trigger",
          "behaviour": {
            "type": "status",
            "status": 418
          },
          "trigger": {
            "type": "always"
          }
        }]
        """.trimIndent()
    println(
        appWithChaos(
            Request(
                POST,
                "/chaos/activate/new"
            ).body(alwaysReturn418)
        ).bodyString()
    )
    println(appWithChaos(Request(GET, "/")).status)
}

```



