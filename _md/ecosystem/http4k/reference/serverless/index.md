# Serverless



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // AWS Lambda: 
    implementation("org.http4k:http4k-serverless-lambda")

    // Google Cloud Functions: 
    implementation("org.http4k:http4k-serverless-gcf")

    // Apache OpenWhisk (IBM Cloud Functions): 
    implementation("org.http4k:http4k-serverless-openwhisk")

    // Azure Functions: 
    implementation("org.http4k:http4k-serverless-azure")

    // Alibaba Function Compute: 
    implementation("org.http4k:http4k-serverless-alibaba")

    // Tencent Serverless Cloud Functions: 
    implementation("org.http4k:http4k-serverless-tencent")
}
```

### About
These modules provide integration with Serverless deployment environments, such as AWS Lambda or Google Cloud Functions by implementing a single interface. 

#### AWS Lambda integration (HTTP apps)
Since http4k is server independent, it turns out to be fairly trivial to deploy full applications to [AWS Lambda](https://aws.amazon.com/lambda), and then call them by setting up the [API Gateway](https://aws.amazon.com/api-gateway) to proxy requests to the function. Effectively, the combination of these two services become just another Server back-end supported by the library. This has the added bonus that you can test your applications in a local environment and then simply deploy them to AWS Lambda via S3 upload.

In order to achieve this, only a single interface `AppLoader` needs to be implemented and a simple extension of `AwsLambdaFunction` supplied depending on which invocation type is required - Direct, ApiGateway V1/2 or ApplicationLoadBalancer.

This is far from a complete guide, but configuring AWS Lambda and the API Gateway involves several stages:

1. Users, Roles and Policies for the API Gateway and Lambda.
2. API Gateway to proxy all requests to your Lambda.
3. Building your http4k application into a standard UberJar.
4. Optionally using Proguard to minify the JAR.
5. Package up the (minified) JAR into a standard Zip distribution.
6. Create and configure the Lambda function, and at the same time:
    - Upload the standard Zip file to S3.
    - Set the function execution to call the main http4k entry point: `guide.modules.serverless.lambda.FunctionsExampleEntryClass`

We hope to soon provide some tools to automate at least some of the above process, or at least document it somewhat. However, AWS is a complicated beast and many people have a preferred way to set it up: CloudFormation templates, Serverless framework, Terraform, etc. In the meantime, here is an example of how the `AppLoader` is created and how to launch the app locally:

#### Code





```kotlin
package content.ecosystem.http4k.reference.serverless.lambda

import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.ApiGatewayV1LambdaFunction
import org.http4k.serverless.AppLoader

// This AppLoader is responsible for building our HttpHandler which is supplied to AWS
// It is the only actual piece of code that needs to be written.
object TweetEchoLambda : AppLoader {
    override fun invoke(env: Map<String, String>): HttpHandler = {
        Response(OK).body(it.bodyString().take(17))
    }
}

// This class is the entry-point for the function call - configure it when deploying
class FunctionsExampleEntryClass : ApiGatewayV1LambdaFunction(TweetEchoLambda)

fun main() {
    // Launching your Lambda Function locally - by simply providing the operating ENVIRONMENT map as would
    // be configured on AWS.
    fun runLambdaLocally() {
        println("RUNNING LOCALLY:")

        val app: HttpHandler = TweetEchoLambda(mapOf())
        val localLambda = app.asServer(SunHttp(8000)).start()
        val response = ApacheClient()(
            Request(
                POST,
                "http://localhost:8000/"
            ).body("hello hello hello, i suppose this isn't 140 characters anymore..")
        )

        println(response)
        localLambda.stop()
    }

    // the following code is purely here for demonstration purposes, to explain exactly what is happening at AWS.
    fun runLambdaAsAwsWould() {
        println("RUNNING AS LAMBDA:")

//        val response = FunctionsExampleEntryClass().handleRequest(ServerlessMoshi.asInputStream(
//            mapOf(
//                "path" to "/",
//                "queryStringParameters" to emptyMap<String, String>(),
//                "body" to "hello hello hello, i suppose this isn't 140 characters anymore..",
//                "headers" to emptyMap<String, String>(),
//                "isBase64Encoded" to false,
//                "httpMethod" to "GET"
//            )
//        ), mock())
//        println(response)
    }

    runLambdaLocally()
    runLambdaAsAwsWould()
}

```



#### AWS Lambda integration (Event-based apps)
http4k also supports writing Event-based functions to receive AWS events from services like SQS and Dynamo. One advantage of using http4k version is that it uses the AWS SDK RequestStreamHandler instead of the standard RequestHandler - which avoids the heavyweight Jackson deserialisation process (we use Moshi under the covers) utilised by the standard AWS runtime. To use this events functionality, you should also import the AWS Events JAR:

```kotlin
implementation("com.amazonaws:aws-lambda-java-events:3.8.0")
```

Similarly to HttpHandler, for event processing in a functional style, the main body of the Lambda function is encapsulated in a single interface `FnHandler`. This typesafe class is created by an `FnLoader` function and simply passed into an extension of `AwsLambdaEventFunction` - which is the class configured as the entry point of your AWS lambda.

The process of configuration is the same as for HTTP apps above.

#### Code


```kotlin
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import dev.forkhandles.mock4k.mock
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.format.AwsLambdaMoshi
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.AwsLambdaEventFunction
import org.http4k.serverless.FnHandler
import org.http4k.serverless.FnLoader
import org.http4k.serverless.ServerlessFilters.ReportFnTransaction
import org.http4k.serverless.then
import java.io.ByteArrayOutputStream

// This is the handler for the incoming AWS SQS event. It's just a function so you can call it without any infrastructure
fun EventFnHandler(http: HttpHandler) =
    FnHandler { e: SQSEvent, _: Context ->
        e.records.forEach {
            http(Request(POST, "http://localhost:8080/").body(it.body.reversed()))
        }
        "processed ${e.records.size} messages"
    }

// We can add filters to the FnHandler if we want to - in this case print the transaction (with the letency).
val loggingFunction = ReportFnTransaction<SQSEvent, Context, String> { tx ->
    println(tx)
}

// The FnLoader is responsible for constructing the handler and for handling the serialisation of the request and response
fun EventFnLoader(http: HttpHandler) = FnLoader { env: Map<String, String> ->
    loggingFunction.then(EventFnHandler(http))
}

// This class is the entry-point for the Lambda function call - configure it when deploying
class EventFunction : AwsLambdaEventFunction(EventFnLoader(JavaHttpClient()))

fun main() {
    // this server receives the reversed event
    val receivingServer = { req: Request ->
        println(req.bodyString())
        Response(OK)
    }.asServer(SunHttp(8080)).start()

    val sqsEvent = SQSEvent().apply {
        records = listOf(
            SQSMessage().apply { body = "hello world" },
            SQSMessage().apply { body = "goodbye world" }
        )
    }

    fun runLambdaInMemoryOrForTesting() {
        println("RUNNING In memory:")
        val app = EventFnHandler(JavaHttpClient())
        app(sqsEvent, mock())
    }

    fun runLambdaAsAwsWould() {
        println("RUNNING as AWS would invoke the function:")

        val out = ByteArrayOutputStream()

        EventFunction().handleRequest(AwsLambdaMoshi.asInputStream(sqsEvent), out, mock())

        // the response is empty b
        println(out.toString())
    }

    runLambdaInMemoryOrForTesting()
    runLambdaAsAwsWould()

    receivingServer.stop()
}

```



#### Google Cloud Functions integration
Google Cloud Functions are triggered in the cloud by calling an entry point class which implements their `HttpFunction` interface.

In order to achieve this in http4k, only a single interface `AppLoader` needs to be implemented, and then a simple extension class needs to be written which accepts this interface.

You can compose filters and handlers as usual and pass them to the constructor of the `GoogleCloudFunction` and make your entry point class extend from it.
Here is an example:

#### Code





```kotlin
package content.ecosystem.http4k.reference.serverless.gcf

import org.http4k.client.ApacheClient
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.AppLoader
import org.http4k.serverless.GoogleCloudHttpFunction

// This AppLoader is responsible for building our HttpHandler which is supplied to GCF
// Along with the extension class below, is the only actual piece of code that needs to be written.
object TweetEchoLambda : AppLoader {
    private val timer = Filter { next: HttpHandler ->
        { request: Request ->
            val start = System.currentTimeMillis()
            val response = next(request)
            val latency = System.currentTimeMillis() - start
            println("I took $latency ms")
            response
        }
    }

    override fun invoke(env: Map<String, String>): HttpHandler =
        timer
            .then(
                routes(
                    "/echo" bind POST to { Response(OK).body(it.bodyString().take(18)) }
                )
            )
}

// This class is the entry-point for the function call - configure it when deploying
class FunctionsExampleEntryClass : GoogleCloudHttpFunction(TweetEchoLambda)

fun main() {

    // Launching your Function locally - by simply providing the operating ENVIRONMENT map as would
    // be configured in GCP.
    fun runFunctionLocally() {
        println("RUNNING LOCALLY:")

        val app: HttpHandler = TweetEchoLambda(System.getenv())
        val localLambda = app.asServer(SunHttp(8000)).start()

        println(
            ApacheClient()(
                Request(
                    POST,
                    "http://localhost:8000/echo"
                ).body("hello hello hello, i suppose this isn't 140 characters anymore..")
            )
        )
        localLambda.stop()
    }

    // the following code is purely here for demonstration purposes, to explain exactly what is happening at GCP.
    fun runFunctionAsGCFWould() {
        println("RUNNING AS GCF:")

        val response = FakeGCFResponse()
        FunctionsExampleEntryClass().service(
            FakeGCFRequest
                (
                Request(
                    POST,
                    "http://localhost:8000/echo"
                ).body("hello hello hello, i suppose this isn't 140 characters anymore..")
            ), response
        )
        println(response.status)
        println(response.headers)
        println(response.body)
    }

    runFunctionLocally()
    runFunctionAsGCFWould()
}

```



If you are using gradle, gcloud can't deploy the function directly from the project, you must build the fat jar first.
Applying this plugin [shadow jar](https://gradleup.com/shadow/) will provide you with appropriate gradle task to build the fat jar.

After building, and having your jar as the only file in the `libs/` folder you can deploy the function from the parent folder with : 

```gcloud functions deploy example-function --runtime=java11 --entry-point=guide.modules.serverless.gcf.FunctionsExampleEntryClass --trigger-http --source=libs/```

If you want to invoke functions locally you can do it with this gradle setup and passing a `-PrunFunction.target` parameter to the build task : 
```kotlin
configurations {
    invoker
}

dependencies {
    invoker 'com.google.cloud.functions.invoker:java-function-invoker:1.0.0-alpha-2-rc5'
}

tasks.register("runFunction", JavaExec) {
    main = 'com.google.cloud.functions.invoker.runner.Invoker'
    classpath(configurations.invoker)
    inputs.files(configurations.runtimeClasspath, sourceSets.main.output)
    args(
            '--target', project.findProperty('runFunction.target'),
            '--port', project.findProperty('runFunction.port') ?: 8080
    )
    doFirst {
        args('--classpath', files(configurations.runtimeClasspath, sourceSets.main.output).asPath)
    }
}
```

If you are using Maven, you do not have to build the fat JAR and can deploy the function from the project folder.
Simple example on how to setup `pom.xml` to run functions locally and deploy Maven project to the cloud is shown [here](https://cloud.google.com/functions/docs/first-java)

#### Apache OpenWhisk integration
OpenWhisk has a Java runtime which is triggered by calling an entry point class which contains a static `main()` function receiving a GSON `JsonObject`.

In order to achieve this in http4k, only a single interface `AppLoader` needs to be implemented, and then a simple class needs to be written which uses the `OpenWhiskFunction` wrapper. Because of the OpenWhisk runtime usage of the library, a `compileOnly` dependency also needs to be added on GSON to ensure that your function can build correctly.

#### Code





```kotlin
package content.ecosystem.http4k.reference.serverless.openwhisk

import com.google.gson.JsonObject
import org.http4k.client.ApacheClient
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.format.Gson
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.AppLoader
import org.http4k.serverless.OpenWhiskFunction

// This AppLoader is responsible for building our HttpHandler which is supplied to OpenWhisk
// Along with the extension class below, is the only actual piece of code that needs to be written.
object TweetEchoLambda : AppLoader {
    private val timer = Filter { next: HttpHandler ->
        { request: Request ->
            val start = System.currentTimeMillis()
            val response = next(request)
            val latency = System.currentTimeMillis() - start
            println("I took $latency ms")
            response
        }
    }

    override fun invoke(env: Map<String, String>): HttpHandler =
        timer
            .then(
                routes(
                    "/echo" bind POST to { Response(OK).body(it.bodyString().take(18)) }
                )
            )
}

// This class is the entry-point for the function call - configure it when deploying
object FunctionsExampleEntryClass {
    @JvmStatic
    fun main(request: JsonObject) = OpenWhiskFunction(TweetEchoLambda)(request)
}

fun main() {

    // Launching your Function locally - by simply providing the operating ENVIRONMENT map as would
    // be configured in OpenWhisk.
    fun runFunctionLocally() {
        println("RUNNING LOCALLY:")

        val app: HttpHandler = TweetEchoLambda(System.getenv())
        val localLambda = app.asServer(SunHttp(8000)).start()

        println(
            ApacheClient()(
                Request(
                    POST,
                    "http://localhost:8000/echo"
                ).body("hello hello hello, i suppose this isn't 140 characters anymore..")
            )
        )
        localLambda.stop()
    }

    // the following code is purely here for demonstration purposes, to explain exactly what is happening in OpenWhisk.
    fun runFunctionAsOpenWhiskWould() {
        println("RUNNING AS OpenWhisk:")

        val fakeOpenWhiskRequest = FakeOpenWhiskRawRequest(
            "POST", "/echo", "", emptyMap(),
            "hello hello hello, i suppose this isn't 140 characters anymore.."
        )

        val response =
            FunctionsExampleEntryClass.main(Gson.asJsonObject(fakeOpenWhiskRequest) as JsonObject)
        println(response)
    }

    runFunctionLocally()
    runFunctionAsOpenWhiskWould()
}

```



Packaging of the app should be done using [ShadowJar](https://gradleup.com/shadow/) and then an action created with the `wsk` CLI:

```
wsk -i action create myFunctionName myApp.jar --main org.http4k.example.MyFunctionClass --web true
```

Locally, you can then just call the function with `curl`:
```
curl -k `wsk -i action get test --url | tail -1`
```

[http4k]: https://http4k.org

