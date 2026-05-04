# Connect Overview


The main bulk of non-operationally focussed application code in a modern Server-based HTTP microservice can be broken
down into a few broad areas:

1. Inbound Server-side APIs, routing and unmarshalling of incoming requests
2. Business logic functions
3. Data-access querying and mutations
4. API Client code for outbound remote API communication

#### Structuring our inbound APIs

For **1)** - the Server-side - we tend to model the application as a set of separate HTTP entrypoint classes/functions which are composed into a whole to
represent
the incoming HTTP API, either explicitly or via some meta-programming such as annotations. So for example, using http4k, we might create and start our server
with:





```kotlin
package content.ecosystem.connect.concepts.pattern.pre

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.filter.ServerFilters.BearerAuth
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer

fun MySecureApp(): HttpHandler = BearerAuth("my-very-secure-and-secret-bearer-token").then(
    routes(
        echo(), health()
    )
)

fun echo() = "/echo" bind POST to { req: Request -> Response(OK).body(req.bodyString()) }

fun health() = "/health" bind GET to { req: Request -> Response(OK).body("alive!") }

val server = MySecureApp().asServer(Netty(8080)).start()

```



In this case, the splitting up of the server-side API into separate functions allows us to maintain a decent grip on our
application as a whole and also to be able to easily test the various endpoints in the application independently of
the rest - e.g. we don't need to provide a Bearer token to access our API calls if we have access to directly test `echo()` and `health()`.

Additionally, because we have modularised the code in this way, it is also reusable in other contexts - we can put common endpoint code such as `health()` into
a shared location and reuse them across our fleet of microservices.

#### Structuring our outbound APIs

When it comes to **4)** of the list above - API Client code for other remote APIs - we don't generally have a pattern in place to use the same structure. HTTP
clients to remote systems are usually constructed as monolithic classes with many methods, all built around a singularly configured HTTP API Client. Let's say we
want to talk to the GitHub API, we would normally build an API Client like so:





```kotlin
package content.ecosystem.connect.concepts.pattern.pre

import content.ecosystem.connect.concepts.pattern.Commit
import content.ecosystem.connect.concepts.pattern.UserDetails
import content.ecosystem.connect.concepts.pattern.authorFrom
import content.ecosystem.connect.concepts.pattern.userNameFrom
import content.ecosystem.connect.concepts.pattern.userOrgsFrom
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.RequestFilters.SetHeader

class GitHubApi(client: HttpHandler) {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    fun getUser(username: String): UserDetails {
        val response = http(Request(GET, "/users/$username"))
        return UserDetails(userNameFrom(response), userOrgsFrom(response))
    }

    fun getRepoLatestCommit(owner: String, repo: String) = Commit(
        authorFrom(http(Request(GET, "/repos/$owner/$repo/commits").query("per_page", "1")))
    )
}

val gitHub: GitHubApi = GitHubApi(OkHttp())

val user: UserDetails = gitHub.getUser("octocat")

```



This is all quite sensible - there is a shared HTTP client which is configured to send requests to the API with the correct `Accept` header. Unfortunately
though, as our usage of the API grows, so will the size of the `GitHubApi` class - it may gain many (10s or even 100s of individual) functions, all of which
generally provide singular access to a single API call. We end up with a monolith object which can be thousands of lines long if left unchecked.

As there is generally no interaction between these functions - it would be desirable to structure the code in a similar way to how we structured our inbound
API - in a modular, easily testable and reusable fashion. Even so, we also want to find a way to build functions which combine one or more calls to the API. 

#### Introducing http4k Connect

This is where the Connect pattern and http4k Connect will help us. In essence, Connect allows the splitting of an API Client monolith into individual Actions and a shared API Client object which centralises the communication with the API.

#### Action
The fundamental unit of work in http4k Connect is the `Action` interface, which represents a single interaction with the remote system, generified by the type of the return object `R`. Each action contains the state of the data that needs to be transmitted, and also how to marshall the data within the action to and from the underlying HTTP API.

```kotlin
interface Action<R> {
    fun toRequest(): Request
    fun toResult(response: Response): R
}
```

For our `GitHubApi` API client, we create the superinterface using a [Result4k](https://github.com/fork-handles/forkhandles/tree/trunk/result4k) result monad type to catch exceptions:





```kotlin
package content.ecosystem.connect.concepts.pattern.post

import dev.forkhandles.result4k.Result4k
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.connect.Action

interface GitHubApiAction<R> : Action<Result4k<R, RemoteRequestFailed>>

```



... and  implementations of the Actions for the API along with the result type. Note that
the Actions are modelled as Kotlin data classes - this allows us to easily compare them if we want to in tests:





```kotlin
package content.ecosystem.connect.concepts.pattern.post

import content.ecosystem.connect.concepts.pattern.Commit
import content.ecosystem.connect.concepts.pattern.UserDetails
import content.ecosystem.connect.concepts.pattern.authorFrom
import content.ecosystem.connect.concepts.pattern.userNameFrom
import content.ecosystem.connect.concepts.pattern.userOrgsFrom
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK

data class GetUser(val username: String) : GitHubApiAction<UserDetails> {
    override fun toRequest() = Request(GET, "/users/$username")

    override fun toResult(response: Response) = when (response.status) {
        OK -> Success(UserDetails(userNameFrom(response), userOrgsFrom(response)))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}

data class GetRepoLatestCommit(val owner: String, val repo: String) : GitHubApiAction<Commit> {
    override fun toRequest() = Request(GET, "/repos/$owner/$repo/commits").query("per_page", "1")

    override fun toResult(response: Response): Result4k<Commit, RemoteRequestFailed> = when (response.status) {
        OK -> Success(Commit(authorFrom(response)))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}

```



#### Reimagining API Clients

A Connect API Client represents the common base protocol for interacting with the remote API - it will deal with server host location, authorisation and other
headers, and perform the actual HTTP interactions. Each API Client is modelled as a simple interface with a single generic method accepting the generic Action type.

Note here the presence of the Kotlin `companion object` - it is meant to be empty and is there precisely to give us a point to hook other code onto in a moment. This is to make life easier for the API user.





```kotlin
package content.ecosystem.connect.concepts.pattern.post

import dev.forkhandles.result4k.Result4k
import org.http4k.cloudnative.RemoteRequestFailed

interface GitHubApi {
    operator fun <R : Any> invoke(action: GitHubApiAction<R>): Result4k<R, RemoteRequestFailed>

    companion object
}

```



Our first usage of the companion object is to rewrite our previous version as an anonymous implementation of the `GitHubApi` and attach it to our API Client interface,
returned by the `Http()` factory function. All dependencies required by the API Client are passed in here and closed over. Note that we explicitly pass in the HTTP
client instead of constructing it inside the function - access to this is critical if we want to be able to decorate the API Client with call logging or other operational concerns:





```kotlin
package content.ecosystem.connect.concepts.pattern.post

import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.RequestFilters.SetHeader

fun GitHubApi.Companion.Http(client: HttpHandler) = object : GitHubApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    override fun <R : Any> invoke(action: GitHubApiAction<R>) =
        action.toResult(http(action.toRequest()))
}

```



#### Using the API Client

Apart from the usage of the Companion Object as a hook, construction of our API Clien looks similar to the previous version - we have not exposed any more
concrete types (there is still just `GitHubApi`). However, calling the API does look different - because of the operator function `invoke()`, we now treat the
Server as a simple function which takes Action instances:





```kotlin
package content.ecosystem.connect.concepts.pattern.post

import content.ecosystem.connect.concepts.pattern.Commit
import content.ecosystem.connect.concepts.pattern.UserDetails
import dev.forkhandles.result4k.Result4k
import org.http4k.client.OkHttp
import org.http4k.cloudnative.RemoteRequestFailed

val gitHub: GitHubApi = GitHubApi.Http(OkHttp())

val user: Result4k<UserDetails, RemoteRequestFailed> = gitHub(GetUser("octocat"))
val commit: Result4k<Commit, RemoteRequestFailed> = gitHub(GetRepoLatestCommit("http4k", "http4k-connect"))

```



This change may leave a slight bad taste in the mouth as the API is no longer as IDE discoverable. Luckily, Kotlin has another trick up it's sleeve here which will help us...

#### Extension Functions

We can get back our old API very simply by creating an extension function for each Action that mimics the signature of the Action itself and delegates to the `invoke()` call in the client:





```kotlin
import content.ecosystem.connect.concepts.pattern.Commit
import content.ecosystem.connect.concepts.pattern.UserDetails
import content.ecosystem.connect.concepts.pattern.post.GetRepoLatestCommit
import content.ecosystem.connect.concepts.pattern.post.GetUser
import content.ecosystem.connect.concepts.pattern.post.GitHubApi
import content.ecosystem.connect.concepts.pattern.post.gitHub
import dev.forkhandles.result4k.Result4k
import org.http4k.cloudnative.RemoteRequestFailed

fun GitHubApi.getUser(username: String) = invoke(GetUser(username))
fun GitHubApi.getRepoLatestCommit(owner: String, repo: String) = invoke(GetRepoLatestCommit(owner, repo))

val user2: Result4k<UserDetails, RemoteRequestFailed> = gitHub.getUser("octocat")
val commit2: Result4k<Commit, RemoteRequestFailed> = gitHub.getRepoLatestCommit("http4k", "http4k-connect")

```



Even better, for actions which consist more than one API call such as `getLatestUser()` below, we can just create more extension functions which delegate down to the individual actions. These functions can be added to `GitHubApi` instances at the global level, or just in the contexts or modules which make sense. The
extension function effectively allow us to compose our own custom `GitHubApi` Adapter out of the individual Action parts that we are interested in:





```kotlin
import content.ecosystem.connect.concepts.pattern.UserDetails
import content.ecosystem.connect.concepts.pattern.post.GitHubApi
import content.ecosystem.connect.concepts.pattern.post.gitHub
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.flatMap
import org.http4k.cloudnative.RemoteRequestFailed

fun GitHubApi.getLatestUser(owner: String, repo: String): Result<UserDetails, RemoteRequestFailed> =
    getRepoLatestCommit(owner, repo).flatMap { getUser(it.author) }

val latestUser: Result<UserDetails, RemoteRequestFailed> = gitHub.getLatestUser("http4k", "http4k-connect")

```



#### Summary
Actions and API Clients are the core building blocks of http4k Connect. They allow us to structure our API Client code in a modular, testable and reusable way, and also to compose our own custom API Clients out of the individual parts that we are interested in. The Connect pattern is a powerful way to structure our API Client code in a way that is similar to how we structure our inbound APIs, and also to build functions which combine one or more calls to the API.

