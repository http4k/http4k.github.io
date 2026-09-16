# Postbox


This module provides a simple mechanism to introduce async processing for HTTP messages.

The most common use-case for this mechanism is the implementation of the [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html) pattern to prevent data inconsistencies and bugs when sending messages between services or external systems.

The name `Postbox` referes to the fact it follows the `HttpHandler` uniform design, which means it can be used both for incoming (Inbox) or outgoing (Outbox) requests.

### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.59.0.0"))

    implementation("org.http4k.pro:http4k-postbox")
}
```

## Usage

### Postbox as an HttpHandler

The main application of a Postbox is to use it as an `HttpHandler` to intercept requests (either incoming or outgoing) in your application.

For instance, if you have an existing adapter such as:

```kotlin
class SmsNotificationClient(val client: HttpHandler) {
    fun sendSms(messageId: String, destination: String, message: String) {
        client(Request(POST, "/sms")
            .header("x-message-id", messageId)
            .header("destination", destination)
            .body(message))
    }
}
```

You then have options to replace the client with a transactional outbox to process the message asynchronously:

Before:
```kotlin
val client = SetBaseUriFrom(Uri.of("https://sms-service.external")).then(OkHttp())
val smsClient = SmsNotificationClient(client)
```

After:
```kotlin
val outbox: PostboxTransactor = ...
val smsClient = SmsNotificationClient(outbox.intercepting(fromHeader("x-message-id")))
```

### Transactional storage

The Postbox requires a transactional storage to keep and process requests reliably. For that, we use a `Transactor` to manage the transaction lifecycle.

The JDBC storage needs its schema to be created before the Postbox is used:

```kotlin
import org.http4k.postbox.storage.jdbc.JdbcPostboxSchema
import org.http4k.postbox.storage.jdbc.PostboxTransactor

val datasource = HikariDataSource(HikariConfig().apply {
    driverClassName = "org.postgresql.Driver"
    username = "postgres"
    password = "mysecretpassword"
    jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
})

JdbcPostboxSchema.create(datasource)

val transactor = PostboxTransactor(datasource)
```

Alternatively, `JdbcPostboxSchema.create(datasource, prefix)` can be used to isolate multiple Postboxes within the same database by prefixing their table names.

The currently supported transactors are:

* In-memory - for testing purposes, manages the transaction by using simple locks
* JDBC DataSource - for SQL databases, manages the transaction using a JDBC `DataSource`

### Idempotency

Idempotency for the Postbox is achieved by having a deterministic `requestId` for each received request.

Request ids must be unique and are limited to 64 characters.

Out-of-the-box options are:

* Header - uses a header value to identify the request
* Path - uses a path parameter to identify the request

Alternatively, you can customise this by implementing the `RequestIdResolver` function, like this:

```kotlin
val myResolver: RequestIdResolver = { request: Request ->
    request.header("X-Request-Id") ?: error("No request ID found")
}
```

In this example, the `X-Request-Id` header is used to identify the request and clients are expect to send that header for each request.

This resolver can then be passed to the Postbox interceptor:

```kotlin
val postbox: PostboxTransactor = ...
routes("/sms" bind POST to postbox.intercepting(myResolver))
```

### Filtering request/response data

When storing requests, you may want to remove sensitive data or manipulate the request/response data.

This can be achieved using any http4k `Filter`. For instance, using one of the built-in filters:

```kotlin
val outbox: PostboxTransactor = ...
val outboxClient = RequestFilters.ExcludeHeaders("Authorization")
    .then(outbox.intercepting(fromHeader("x-message-id")))

val smsClient = SmsNotificationClient(outboxClient)
```

### Background Processing

The Postbox provides a simple mechanism to process the requests in the background. Here's an example:

```kotlin
val myRequestHandler: HttpHandler = { request -> // this is the request stored in the postbox
    val success: Boolean = // result of processing the request
    if (success) Response(OK) else Response(INTERNAL_SERVER_ERROR) // indicates if the request was processed successfully
}

val postbox: PostboxTransactor = ...

PostboxProcessing(postbox, myRequestHandler).start()
```

This will start a single background (virtual) thread to process the requests in the Postbox using polling.

It'll do so by periodically claiming a small batch of pending requests and processing them.

The responses for those requests are stored so they can be consumed or served later.

By default, a request will be marked as processed if the handler returns a `2xx` status code. Otherwise, it'll be marked as failed and reprocessed later using an incremental backoff strategy. If it exceeds the maximum retry attempts, the request is marked as "dead" and won't be reprocessed.

To avoid multiple instances of `PostboxProcessing` processing the same request, each batch of pending requests is claimed before being processed. A claimed request is marked as being processed for the duration of a lease; if the request isn't finalised within that lease (for instance because the processor crashed), it will be reclaimed and reprocessed by another or subsequent run.

You can configure `PostboxProcessing` by providing the following options:

| Option | Description | Default |
|--------|-------------|---------|
| `batchSize` | The number of requests to process in a single batch | 10 |
| `lease` | The duration for which a claimed request is reserved before it can be reclaimed by another processor | 30 seconds |
| `maxPollingTime` | The maximum time to wait between polling requests | 5 seconds |
| `shutdownGracePeriod` | How long `stop()` waits for in-flight work to finish before returning. If it elapses with work still in flight, a `ShutdownTimedOut` event is emitted | 30 seconds |
| `successCriteria` | A `(Response) -> Boolean` function to determine if the request was processed successfully | `response.status.successful` (i.e. status code 2xx) |
| `maxFailures` | The maximum number of failures before marking the request as "dead" | 3 |
| `backoffStrategy` | A function to calculate the delay before trying to reprocess a request again | `(2 ^ (# failures)) * 5 seconds + random(10) seconds` |

### Configuring response for pending requests

By default, the Postbox will return a `202 Accepted` response for requests that are still pending.

Out-of-the-box options are:

* Empty - returns a `202 Accepted` response with no body
* Link - returns a `202 Accepted` response with a `Link` header pointing to the status endpoint
* Redirect - returns a `303 See Other` response with a `Location` header pointing to the status endpoint

Alternatively, you can customize this response by providing a custom `PendingResponseGenerator` for the Postbox:

```kotlin
val myCustomResult = Response(ACCEPTED).body("Your request is being processed. Please check back later")

val transactor: PostboxTransactor = ...
val postbox = PostboxHandlers(transactor, myCustomResult)
```

### Checking the status of postbox requests

The Postbox provides a separate `HttpHandler` to check the status or retrieve the response of processed requests.

Here's an example:

```kotlin
val transactor: PostboxTransactor = ...
val handlers = PostboxHandlers(transactor)

routes("/status/{requestId}" bind GET to handlers.status(fromPath("requestId")))
```

## Transactional Outbox example

A complete transactional outbox that intercepts outgoing requests, stores them in the Postbox and processes them against a slow third-party service:





```kotlin
package content.ecosystem.pro.reference.postbox

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.events.StdOutEvents
import org.http4k.postbox.PostboxHandlers
import org.http4k.postbox.RequestIdResolvers.fromHeader
import org.http4k.postbox.RequestIdResolvers.fromPath
import org.http4k.postbox.processing.PostboxProcessing
import org.http4k.postbox.storage.jdbc.JdbcPostboxSchema
import org.http4k.postbox.storage.jdbc.PostboxTransactor
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    ThirdPartySlowSmsService().asServer(SunHttp(8000)).start()

    val dataSource = HikariDataSource(HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        username = "postgres"
        password = "mysecretpassword"
        jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    })
    JdbcPostboxSchema.create(dataSource)

    val transactor = PostboxTransactor(dataSource).also { transactor ->
        PostboxProcessing(transactor, JavaHttpClient(), events = StdOutEvents).start()
    }

    val outbox = PostboxHandlers(transactor)

    routes(
        "/api/{orderId}/notify" bind POST to NotificationHandler(outbox.intercepting(fromHeader("x-order-id"))),
        "/api/smsNotificationStatus/{orderId}" bind GET to outbox.status(fromPath("orderId"))
    ).asServer(SunHttp(9000)).start()
}

fun ThirdPartySlowSmsService() = routes("/sms/send" bind POST to
    { req: Request -> Thread.sleep(10000); Response(OK).body("SMS sent. Message= ${req.bodyString()}") })

private fun NotificationHandler(smsClient: HttpHandler) = { request: Request ->
    val message = request.bodyString()

    // Makes the request to the third party service.
    // If the client is a transactional outbox, rather than the real thing:
    // On first call it'll store the request and return a 202 with a Link header to check the status of the request.
    // On subsequent calls it'll return either 202 again or the response from the third party service.
    smsClient(
        Request(POST, "http://localhost:8000/sms/send")
            .header("x-order-id", request.path("orderId")!!)
            .body("Notified a user with message '$message'")
    )

    Response(OK).body("Notification accepted")
}

```



## Transactional Inbox

The Postbox can also be used to capture requests and serve the responses after processing them. Here's a complete example:





```kotlin
package content.ecosystem.pro.reference.postbox

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.UriTemplate.Companion.from
import org.http4k.events.StdOutEvents
import org.http4k.postbox.PendingResponseGenerators.redirect
import org.http4k.postbox.PostboxHandlers
import org.http4k.postbox.RequestIdResolvers.fromPath
import org.http4k.postbox.processing.PostboxProcessing
import org.http4k.postbox.storage.jdbc.JdbcPostboxSchema
import org.http4k.postbox.storage.jdbc.PostboxTransactor
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val dataSource = HikariDataSource(HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        username = "postgres"
        password = "mysecretpassword"
        jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    })
    JdbcPostboxSchema.create(dataSource)

    val transactor = PostboxTransactor(dataSource).also { transactor ->
        PostboxProcessing(transactor, SlowInternalHandler, events = StdOutEvents).start()
    }

    val inbox = PostboxHandlers(transactor, redirect("taskId", from("http://localhost:9000/workload/status/{taskId}")))

    routes(
        "/workload/submit/{taskId}" bind POST to inbox.intercepting(fromPath("taskId")),
        "/workload/status/{taskId}" bind GET to inbox.status(fromPath("taskId"))
    ).asServer(SunHttp(9000)).start()
}

private val SlowInternalHandler =
    { request: Request -> Thread.sleep(10000); Response(OK).body("work for ${request.uri.path} is done") }

```



