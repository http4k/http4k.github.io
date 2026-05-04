# X402


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-x402")
    implementation("org.http4k:http4k-connect-x402-fake")
}
```

http4k Connect provides support for the [x402 protocol](https://www.x402.org/), enabling HTTP 402 Payment Required flows for APIs. The module includes both client and server-side components for integrating cryptocurrency-based payments into your http4k applications.

The `X402Facilitator` client provides the following Actions:

- **Verify** - Verify a payment payload against payment requirements
- **Settle** - Settle a verified payment and receive a transaction hash
- **Supported** - Query the facilitator for supported payment schemes and networks

### Facilitator Client

Connect to an x402 facilitator service:





```kotlin
package content.ecosystem.connect.reference.x402

import org.http4k.connect.x402.X402Facilitator
import org.http4k.connect.x402.Http
import org.http4k.core.Uri

val facilitatorClient = X402Facilitator.Http(Uri.of("https://x402.org/facilitator"))

```



### Client-side Filter

The `ClientFilters.X402PaymentRequired` filter automatically handles 402 responses by signing payment requirements and retrying the request. You provide an `X402Signer` implementation that signs payment requirements from your wallet:





```kotlin
package content.ecosystem.connect.reference.x402

import dev.forkhandles.result4k.Success
import org.http4k.client.JavaHttpClient
import org.http4k.connect.x402.X402Facilitator
import org.http4k.connect.x402.X402Signer
import org.http4k.connect.x402.model.PaymentPayload
import org.http4k.connect.x402.model.PaymentScheme
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.X402PaymentRequired

fun `using the client filter`() {
    val signer = X402Signer { reqs ->
        val req = reqs.first()
        Success(
            PaymentPayload(
                x402Version = 2,
                scheme = req.scheme,
                network = req.network,
                payload = mapOf("signature" to "0xsigned"),
                resource = "https://api.example.com/data",
                description = "Paid resource"
            )
        )
    }

    val client = ClientFilters.X402PaymentRequired(signer)
        .then(ClientFilters.SetBaseUriFrom(Uri.of("https://api.example.com")))
        .then(JavaHttpClient())

    val response = client(Request(GET, "/premium-data"))
}

```



### Server-side Filter

The `ServerFilters.X402PaymentRequired` filter protects endpoints by requiring payment. It verifies and settles payments via a facilitator before allowing access:





```kotlin
package content.ecosystem.connect.reference.x402

import org.http4k.connect.x402.FakeX402Facilitator
import org.http4k.connect.x402.X402Facilitator
import org.http4k.connect.x402.model.AssetAddress
import org.http4k.connect.x402.model.PaymentAmount
import org.http4k.connect.x402.model.PaymentNetwork
import org.http4k.connect.x402.model.PaymentRequirements
import org.http4k.connect.x402.model.PaymentScheme
import org.http4k.connect.x402.model.WalletAddress
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.filter.X402PaymentRequired

fun `using the server filter`() {
    val facilitator = FakeX402Facilitator().client()

    val requirements = PaymentRequirements(
        scheme = PaymentScheme.of("exact"),
        network = PaymentNetwork.of("base-sepolia"),
        asset = AssetAddress.of("0xUSDC"),
        amount = PaymentAmount.of("100"),
        payTo = WalletAddress.of("0xmerchant"),
        maxTimeoutSeconds = 30
    )

    val app = ServerFilters.X402PaymentRequired(facilitator) { listOf(requirements) }
        .then { Response(OK).body("premium content") }
}

```



### X402Security

For route-level protection, use `X402Security` which wraps the server filter as an http4k `Security` instance:





```kotlin
package content.ecosystem.connect.reference.x402

import org.http4k.connect.x402.FakeX402Facilitator
import org.http4k.connect.x402.model.AssetAddress
import org.http4k.connect.x402.model.PaymentAmount
import org.http4k.connect.x402.model.PaymentNetwork
import org.http4k.connect.x402.model.PaymentRequirements
import org.http4k.connect.x402.model.PaymentScheme
import org.http4k.connect.x402.model.WalletAddress
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.X402Security

fun `using X402Security`() {
    val facilitator = FakeX402Facilitator().client()

    val requirements = PaymentRequirements(
        scheme = PaymentScheme.of("exact"),
        network = PaymentNetwork.of("base-sepolia"),
        asset = AssetAddress.of("0xUSDC"),
        amount = PaymentAmount.of("100"),
        payTo = WalletAddress.of("0xmerchant"),
        maxTimeoutSeconds = 30
    )

    val security = X402Security({ listOf(requirements) }, facilitator)

    val app = routes(
        "/premium" bind security.filter.then { Response(OK).body("paid content") }
    )
}

```



### Default Fake port: 12794

To start:





```kotlin
package content.ecosystem.connect.reference.x402

import org.http4k.chaos.start
import org.http4k.connect.x402.FakeX402Facilitator

val x402Facilitator = FakeX402Facilitator().start()

```



