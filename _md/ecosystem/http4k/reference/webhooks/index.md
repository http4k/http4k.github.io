# Webhooks



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-webhooks")
}
```

### About

This module provides infrastructure for the [Webhook standard](https://www.standardwebhooks.com/), providing infrastructure for 
signing and verifying of Webhook requests (HMAC256 only currently) as per the standard, and support for the defined Webhook event wrapper format.

The example below shows how to use sign and verify filters to automatically provide security and marshalling for the Standard Webhook format.

#### Code





```kotlin
package content.ecosystem.http4k.reference.webhooks

import org.http4k.client.JavaHttpClient
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ClientFilters
import org.http4k.filter.ServerFilters
import org.http4k.filter.SignWebhookPayload
import org.http4k.filter.VerifyWebhookSignature
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.lens.Header
import org.http4k.lens.WEBHOOK_ID
import org.http4k.lens.WEBHOOK_SIGNATURE
import org.http4k.lens.WEBHOOK_TIMESTAMP
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.webhook.EventType
import org.http4k.webhook.WebhookPayload
import org.http4k.webhook.WebhookTimestamp
import org.http4k.webhook.signing.HmacSha256
import org.http4k.webhook.signing.HmacSha256SigningSecret
import java.time.Instant

data class MyEventType(val index: Int)

fun main() {
    val lens = Body.auto<WebhookPayload<MyEventType>>().toLens()

    val signingSecret = HmacSha256SigningSecret.encode("this_is_my_super_secret_secret")

    val secureWebhookSendingClient =
        ClientFilters.SignWebhookPayload(HmacSha256.Signer(signingSecret), Jackson)
            .then(JavaHttpClient())

    val secureWebhookReceiver =
        ServerFilters.VerifyWebhookSignature(HmacSha256.Verifier(signingSecret))
            .then { req: Request ->
                listOf(Header.WEBHOOK_ID, Header.WEBHOOK_SIGNATURE, Header.WEBHOOK_TIMESTAMP)
                    .forEach { println(it.meta.name + ": " + it(req)) }

                println("event received was: " + lens(req))
                Response(OK)
            }

    val server = secureWebhookReceiver.asServer(SunHttp(0)).start()

    // create the webhook event wrapper with event type, timestamp
    val webhookPayload = WebhookPayload(
        EventType.of("foo.bar"),
        WebhookTimestamp.of(Instant.now()),
        MyEventType(123)
    )

    val eventRequest =
        Request(POST, Uri.of("http://localhost:${server.port()}")).with(lens of webhookPayload)

    // send the webhook - the infra signs the request
    secureWebhookSendingClient(eventRequest)

    server.stop()
}

```



[http4k]: https://http4k.org

