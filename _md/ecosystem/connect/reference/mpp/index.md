# Machine Payments Protocol


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-mpp")
}
```

http4k Connect provides support for the [Machine Payments Protocol (MPP)](https://paymentauth.org/), enabling HTTP 402 Payment Required flows for APIs. MPP supports multiple payment methods (Stripe, crypto, etc.) and provides a protocol-level standard for machine-to-machine payments.

Unlike x402, MPP has no facilitator service — `MppSigner` and `MppVerifier` are fun interfaces that you implement directly to handle signing and verification for your chosen payment method.

### Client-side Filter

The `ClientFilters.MppPaymentRequired` filter automatically handles 402 responses by signing the challenge from the server and retrying the request. You provide an `MppSigner` implementation that creates a `Credential` from a `Challenge`:





```kotlin
package content.ecosystem.connect.reference.mpp

import dev.forkhandles.result4k.Success
import org.http4k.client.JavaHttpClient
import org.http4k.connect.mpp.MppSigner
import org.http4k.connect.mpp.model.Credential
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.MppPaymentRequired

fun `using the client filter`() {
    val signer = MppSigner { challenge ->
        Success(
            Credential(
                challenge = challenge,
                payload = mapOf("token" to "payment-token-123")
            )
        )
    }

    val client = ClientFilters.MppPaymentRequired(signer)
        .then(ClientFilters.SetBaseUriFrom(Uri.of("https://api.example.com")))
        .then(JavaHttpClient())

    val response = client(Request(GET, "/premium-data"))
}

```



### Server-side Filter

The `ServerFilters.MppPaymentRequired` filter protects endpoints by requiring payment. It verifies credentials directly via your `MppVerifier` implementation before allowing access:





```kotlin
package content.ecosystem.connect.reference.mpp

import dev.forkhandles.result4k.Success
import org.http4k.connect.mpp.MppVerifier
import org.http4k.connect.mpp.model.Challenge
import org.http4k.connect.mpp.model.ChallengeId
import org.http4k.connect.mpp.model.ChargeRequest
import org.http4k.connect.mpp.model.Currency
import org.http4k.connect.mpp.model.PaymentAmount
import org.http4k.connect.mpp.model.PaymentIntent
import org.http4k.connect.mpp.model.PaymentMethod
import org.http4k.connect.mpp.model.PaymentReference
import org.http4k.connect.mpp.model.Realm
import org.http4k.connect.mpp.model.Receipt
import org.http4k.connect.mpp.model.ReceiptStatus
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.MppPaymentRequired
import org.http4k.filter.ServerFilters
import java.time.Instant

fun `using the server filter`() {
    val challenge = Challenge(
        id = ChallengeId.of("challenge-123"),
        realm = Realm.of("api.example.com"),
        method = PaymentMethod.of("stripe"),
        intent = PaymentIntent.of("charge"),
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
                challengeId = credential.challenge.id,
                reference = PaymentReference.of("tx-456")
            )
        )
    }

    val app = ServerFilters.MppPaymentRequired(verifier) { challenge }
        .then { Response(OK).body("premium content") }
}

```



### MppSecurity

For route-level protection, use `MppSecurity` which wraps the server filter as an http4k `Security` instance:





```kotlin
package content.ecosystem.connect.reference.mpp

import dev.forkhandles.result4k.Success
import org.http4k.connect.mpp.MppVerifier
import org.http4k.connect.mpp.model.Challenge
import org.http4k.connect.mpp.model.ChallengeId
import org.http4k.connect.mpp.model.ChargeRequest
import org.http4k.connect.mpp.model.Currency
import org.http4k.connect.mpp.model.PaymentAmount
import org.http4k.connect.mpp.model.PaymentIntent
import org.http4k.connect.mpp.model.PaymentMethod
import org.http4k.connect.mpp.model.PaymentReference
import org.http4k.connect.mpp.model.Realm
import org.http4k.connect.mpp.model.Receipt
import org.http4k.connect.mpp.model.ReceiptStatus
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.MppSecurity
import java.time.Instant

fun `using MppSecurity`() {
    val verifier = MppVerifier { credential ->
        Success(
            Receipt(
                status = ReceiptStatus.success,
                method = credential.challenge.method,
                timestamp = Instant.now(),
                challengeId = credential.challenge.id,
                reference = PaymentReference.of("tx-456")
            )
        )
    }

    val challenge = Challenge(
        id = ChallengeId.of("challenge-123"),
        realm = Realm.of("api.example.com"),
        method = PaymentMethod.of("stripe"),
        intent = PaymentIntent.of("charge"),
        request = ChargeRequest(
            amount = PaymentAmount.of("100"),
            currency = Currency.of("USD")
        )
    )

    val security = MppSecurity({ challenge }, verifier)

    val app = routes(
        "/premium" bind security.filter.then { Response(OK).body("paid content") }
    )
}

```



