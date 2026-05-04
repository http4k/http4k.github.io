# Platform: K8S



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-platform-k8s")
}
```

http4k applications are naturally at home operating in distributed, Kubernetes (k8s) environments. Whilst simple to create, this module 
provides requisite tooling to get apps up and running with the minimum of effort to enable the following operational aspects:

#### Quick start
Because http4k does not use reflection or annotation process for application startup, all of the supported Server-backends 
start up and shutdown very quickly - this is crucial for K8S-based environments where an orchestration framework might move 
instances around to redistribute load or avoid problematic server/rack/DCs.

#### Observability
Orchestration software such as Kubernetes regularly query a set of diagnostic endpoints to monitor the state of an 
application. This module provides standardised `HttpHandler` implementations to model the following endpoints:

- Liveness - used to determine if the application is actually alive.
- Readiness - used to determine if the application is available to receive production traffic from the cloud Load Balancer. This 
endpoint performs a series of diagnostic checks against it's dependencies (such as database connectivity) and collates the 
results to report back to the orchestrator. http4k provides the `ReadinessCheck` interface which can be implemented as required 
and plugged into the endpoint.

In Kubernetes, this set of endpoints is generally hosted on a second port to avoid the API clashes, so http4k provides the machinery to 
easily start these services on a different port to the main application API via the `Http4kK8sServer` object.
 
#### Code





```kotlin
package content.ecosystem.http4k.reference.k8s

import org.http4k.client.JavaHttpClient
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.config.Secret
import org.http4k.core.Filter
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.k8s.Http4kK8sServer
import org.http4k.k8s.asK8sServer
import org.http4k.k8s.health.Completed
import org.http4k.k8s.health.Health
import org.http4k.k8s.health.ReadinessCheck
import org.http4k.k8s.health.ReadinessCheckResult
import org.http4k.lens.Lens
import org.http4k.lens.secret
import org.http4k.routing.bind
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import kotlin.random.Random

// the entire k8s application consists of 2 servers - the main and the health
object App {
    // settings
    private val otherServiceUri: Lens<Environment, Uri> =
        EnvironmentKey.k8s.serviceUriFor("otherservice")
    private val dbRole = EnvironmentKey.required("database.user.role")
    private val dbPassword = EnvironmentKey.secret().required("database.user.password")

    operator fun invoke(env: Environment): Http4kK8sServer {
        // define the main app API - it proxies to the "other" service
        val mainApp = ClientFilters.SetHostFrom(otherServiceUri(env))
            .then(rewriteUriToLocalhostAsWeDoNotHaveDns) // this line only here to make the example work!
            .then(JavaHttpClient())

        // define the health app API
        val healthApp = Health(
            "/config" bind GET to { Response(OK).body(env.keys().toString()) },
            checks = listOf(
                DatabaseCheck(
                    RandomlyFailingDatabase(
                        dbRole(env), dbPassword(env)
                    )
                )
            )
        )
        return mainApp.asK8sServer(::SunHttp, env, healthApp)
    }

    private val rewriteUriToLocalhostAsWeDoNotHaveDns = Filter { next ->
        {
            println("Rewriting ${it.uri} so we can proxy properly")
            next(it.uri(it.uri.authority("localhost:9000")))
        }
    }
}

// this is a database client that we are going to health check
class RandomlyFailingDatabase(private val user: String, password: Secret) {
    init {
        // the secret is a single-shot value whose value will be discarded after use
        password.use { println("setting up the database connection with creds: $user/$it") }
    }

    fun insertARecord() {
        if (Random(1).nextBoolean()) throw Exception("oh no! $user has no access")
    }
}

// implements the check which will determine if this service is ready to go
class DatabaseCheck(private val db: RandomlyFailingDatabase) : ReadinessCheck {
    override val name = "database"
    override fun invoke(): ReadinessCheckResult {
        db.insertARecord()
        return Completed(name)
    }
}

/** file app.properties contains
database.user.role=admin
database.user.password=myPassword
 */

fun main() {
    val defaultConfig = Environment.defaults(
        EnvironmentKey.k8s.SERVICE_PORT of 8000,
        EnvironmentKey.k8s.HEALTH_PORT of 8001,
        EnvironmentKey.k8s.serviceUriFor("otherservice") of Uri.of("https://localhost:8000")
    )

    // standard chaining order for properties is local file -> JVM -> Environment -> defaults -> boom!
    val k8sPodEnv = Environment.fromResource("app.properties") overrides
        Environment.JVM_PROPERTIES overrides
        Environment.ENV overrides
        defaultConfig

    // the end-server that we will proxy to
    val upstream =
        { _: Request -> Response(OK).body("HELLO!") }.asServer(SunHttp(9000)).start()

    val server = App(k8sPodEnv).start()

    performHealthChecks()

    server.stop()
    upstream.stop()
}

private fun performHealthChecks() {
    val client = DebuggingFilters.PrintResponse().then(JavaHttpClient())

    // health checks
    client(Request(GET, "http://localhost:8001/liveness"))
    client(Request(GET, "http://localhost:8001/readiness"))
    client(Request(GET, "http://localhost:8001/config"))

    // proxied call
    client(Request(GET, "http://localhost:8000"))
}

```



