# AWS: IAM Identity Center


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-iamidentitycenter")
    implementation("org.http4k:http4k-connect-amazon-iamidentitycenter-fake")
}
```


The IAMIdentityCenter connector provides the following Fakes:

## OIDC

Actions:
* RegisterClient
* StartDeviceAuthentication
* CreateToken

### Default Fake port: 34160

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.iamidentitycenter

import org.http4k.chaos.start
import org.http4k.connect.amazon.iamidentitycenter.FakeOIDC

val oidc = FakeOIDC().start()

```



## SSO

Actions:
* SSO: GetFederatedCredentials

### Default Fake port: 25813

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.iamidentitycenter

import org.http4k.chaos.start
import org.http4k.connect.amazon.iamidentitycenter.FakeSSO

val sso = FakeSSO().start()

```



## Interactive CLI login

The module provides a CredentialsProvider to do interactive login to





```kotlin
package content.ecosystem.connect.reference.amazon.iamidentitycenter

import org.http4k.connect.amazon.CredentialsProvider
import org.http4k.connect.amazon.core.model.AwsAccount
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.iamidentitycenter.SSO
import org.http4k.connect.amazon.iamidentitycenter.model.RoleName
import org.http4k.connect.amazon.iamidentitycenter.model.SSOProfile
import org.http4k.core.Uri

val provider = CredentialsProvider.SSO(
    SSOProfile(
        AwsAccount.of("01234567890"),
        RoleName.of("hello"),
        Region.US_EAST_1,
        Uri.of("http://foobar"),
    )
)

```



