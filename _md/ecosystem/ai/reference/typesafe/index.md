# TypeSafe


### Installation

```kotlin
dependencies {

    
    implementation(platform("org.http4k:http4k-bom:6.61.0.0"))


    // for the low-level TypeSafe API client
    implementation("org.http4k:http4k-connect-ai-typesafe")

    // for the FakeTypeSafe server
    implementation("org.http4k:http4k-connect-ai-typesafe-fake")
}
```

The http4k-ai TypeSafe integration provides:

- Low-level API Client
- FakeTypeSafe server which can be used as a testing harness for the API Client

## Low-level API Client

The TypeSafe connector provides the following Actions:

* GetModels
* SystemOne

New actions can be created easily using the same transport.

Instead of prompts and free text, the API is driven by typed `Question`s asked about a state object, each of which
returns a matching `Answer`:

| Question | Asks for                              | Answer                                          |
|----------|---------------------------------------|-------------------------------------------------|
| `Choice` | one of a set of named criteria        | the choice, per-criterion probabilities, confidence |
| `Score`  | a position on an ordered scale        | the score, the legend it was scored against, confidence |
| `Noul`   | the truth of a single statement       | a probability                                   |

Questions are answered by the TypeSafe Jev models, whose names are held in `JevModels` - `JevLatest` (the default for
all requests), `JevPreview` and pinned versions such as `Jev_1_13_0`. Pass a different one to any question via the
`model` parameter, and use the `GetModels` action to list what the API currently offers.

The client APIs utilise the TypeSafe API Key (Bearer Auth). There is no reflection used anywhere in the library, so
this is perfect for deploying to a Serverless function.

### Example usage





```kotlin
package content.ecosystem.ai.reference.typesafe

import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.orThrow
import org.http4k.connect.typesafe.FakeTypeSafe
import org.http4k.connect.typesafe.Http
import org.http4k.connect.typesafe.Question
import org.http4k.connect.typesafe.TypeSafe
import org.http4k.connect.typesafe.ask
import org.http4k.connect.typesafe.systemOne
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

val department = Question.Choice(
    "department", "Which team should handle this",
    mapOf(
        "billing" to "Payment or subscription issues",
        "technical" to "Bugs or integration problems",
        "sales" to "Pricing or account questions"
    )
)

val frustration = Question.Score(
    "frustration", "How frustrated the customer appears",
    listOf("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
)

val isUrgent = Question.Noul("is_urgent", "The message conveys urgency or time-sensitivity")

fun main() {
    // we can connect to the real service or the fake (drop in replacement)
    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeTypeSafe()

    // create a client
    val client = TypeSafe.Http(ApiKey.of("my-api-key"), http.debug())

    val complaint = "I've been trying to connect my Stripe account for 3 days and it keeps failing. I'm losing sales."

    // ask a batch of questions about the same state
    val answers = client.systemOne(complaint, department, frustration, isUrgent).orThrow()

    println("route to: " + answers.answerTo(department).choice)
    println("frustration: " + answers.answerTo(frustration).score)
    println("urgency: " + answers.answerTo(isUrgent).noul)

    // or just one, for its typed answer
    println(client.ask(complaint, isUrgent).orThrow().noul)
}

```



State and criteria can be your own domain types instead of strings - use `Entry` to convert the JSON in questions and
answers back into them:





```kotlin
package content.ecosystem.ai.reference.typesafe

import org.http4k.connect.orThrow
import org.http4k.connect.typesafe.Entry
import org.http4k.connect.typesafe.FakeTypeSafe
import org.http4k.connect.typesafe.Question
import org.http4k.connect.typesafe.chosen
import org.http4k.connect.typesafe.invoke
import org.http4k.connect.typesafe.systemOne

data class SupportCase(val message: String, val orderId: Int)

data class Severity(val label: String, val description: String)

val severityLevels = listOf(
    Severity("minor", "Cosmetic or a workaround exists"),
    Severity("major", "Blocks a key workflow"),
    Severity("critical", "The customer cannot operate")
)

val severity = Question.Score("severity", "How severe is the problem", severityLevels)

val impact = Question.Choice(
    "impact", "How badly does this affect the customer",
    severityLevels.associateBy { it.label }
)

fun main() {
    val client = FakeTypeSafe().client()

    val case = SupportCase("The dashboard crashes on login", 4242)

    val response = client.systemOne(case, severity, impact).orThrow()

    val severityLens = Entry<Severity>()

    println("scored against: " + severityLens(response.answerTo(severity).legend))
    println("impact: " + response.answerTo(impact).chosen<Severity>(impact).description)
}

```



Other examples can be
found [here](https://github.com/http4k/http4k/tree/master/connect/ai/typesafe/fake/src/examples/kotlin).

## Fake TypeSafe Server

The Fake TypeSafe provides the below actions and can be spun up as a server, meaning it is perfect for using in test
environments without using up valuable request tokens!

* GetModels
* SystemOne

### Security

The Fake server endpoints are secured with an API key header, but the value is not checked for anything other than
presence.

### Generation of responses

By default, the Fake serves model cards for `JevLatest` and `JevPreview`, and answers with the first criterion of each
question. This behaviour can be overridden to script
answers (eg. to drive a particular test case) by passing a `QuestionAnswerer` to the Fake, which receives the state and
the question being asked.

### Default Fake port: 61761

To start:





```kotlin
package content.ecosystem.ai.reference.typesafe

import org.http4k.chaos.start
import org.http4k.connect.typesafe.FakeTypeSafe

val typeSafe = FakeTypeSafe().start()

```



