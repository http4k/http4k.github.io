# Security: WebAuthn


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.56.0.0"))

    implementation("org.http4k:http4k-security-webauthn")
}
```

### About

Passwordless authentication using [WebAuthn](https://www.w3.org/TR/webauthn-2/)/FIDO2 **passkeys**. The module handles
the full registration and authentication ceremonies, session management, and cryptographic verification of credentials,
so users can sign in with a fingerprint, face, security key, or device PIN instead of a password.

### Usage

Everything is orchestrated through the `Passkeys` type, which exposes two ways to wire passkeys into your app:

- **`Passkeys.passwordless(...)`** - passkey-only signup and login, with no prior session required.
- **`Passkeys.onTopOfExistingLogin(...)`** - let an already-logged-in user add a passkey to their account.

Either way you get a pre-wired set of `routes` for the browser-side ceremonies:

| Route                        | Purpose                                                                |
|------------------------------|------------------------------------------------------------------------|
| `POST /register/options`     | Begin registration - issue a challenge and creation options            |
| `POST /register`             | Complete registration - verify and store the new credential            |
| `POST /authenticate/options` | Begin authentication - issue a challenge                               |
| `POST /authenticate`         | Complete authentication - verify the signature and establish a session |

Plus an `authFilter` to protect routes and a `logout` handler to clear sessions.

### Extension points

The behaviour is composed from three pluggable interfaces, each with a production and a testing implementation:

| Interface            | Responsibility                                         | Production                  | Testing                         |
|----------------------|--------------------------------------------------------|-----------------------------|---------------------------------|
| `PasskeyVerifier`    | Cryptographic verification of attestation & signatures | `WebAuthn4jPasskeyVerifier` | `InsecurePasskeyVerifier`       |
| `PasskeyPersistence` | Storing & retrieving credentials and ceremony state    | _your store_                | `InMemoryPasskeyPersistence`    |
| `Principals`         | Reading/writing/clearing the user session              | _your session_              | `InsecureCookieBasedPrincipals` |

Supporting model types include `RelyingParty` (your server identity), `PasskeyUser` (the user identity),
`WebAuthnPolicy` (configurable security policy - user verification, attestation, resident key, etc.) and the sealed
`PasskeyError` describing ceremony failures (bad signature, challenge/origin mismatch, sign-counter regression for clone
detection, and so on).

#### Code





```kotlin
package content.ecosystem.pro.reference.webauthn

import org.http4k.connect.model.Base64UriBlob
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.lens.RequestKey
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.passkeys.Passkeys
import org.http4k.security.passkeys.model.PasskeyUser
import org.http4k.security.passkeys.model.RelyingParty
import org.http4k.security.passkeys.randomHandle
import org.http4k.security.passkeys.testing.InMemoryPasskeyPersistence
import org.http4k.security.passkeys.testing.InsecureCookieBasedPrincipals
import org.http4k.security.passkeys.webauthn4j.WebAuthn4jPasskeyVerifier
import org.http4k.server.SunHttp
import org.http4k.server.asServer

/**
 * Simplest possible passkey demo: register, then log in. Open: http://localhost:9000,
 */
fun main() {
    val rp = RelyingParty(id = "localhost", name = "http4k passkeys demo", origin = Uri.of("http://localhost:9000"))
    val handle = RequestKey.required<Base64UriBlob>("handle")

    val passkeys = Passkeys.passwordless(
        rp = rp,
        verifier = WebAuthn4jPasskeyVerifier(),
        persistence = InMemoryPasskeyPersistence(),
        principals = InsecureCookieBasedPrincipals("http4k", handle),
        // the registering user comes straight off the signup request - here just a ?username=
        user = { req ->
            req.query("username")?.takeIf(String::isNotBlank)?.let { PasskeyUser(Base64UriBlob.randomHandle(), it, it) }
        }
    )

    val app = routes(
        "/" bind GET to { Response(OK).header("content-type", "text/html; charset=utf-8").body(page) },
        "/passkeys" bind passkeys.routes
    )

    app.asServer(SunHttp(9000)).start().also { println("passkeys demo: http://localhost:9000") }.block()
}

```



[http4k]: https://http4k.org

