# AWS: Simple Email Service


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-ses")
    implementation("org.http4k:http4k-connect-amazon-ses-fake")
}
```


The SES connector provides the following Actions:

* SendEmail

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage

### Default Fake port: 59920

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.ses

import org.http4k.chaos.start
import org.http4k.connect.amazon.ses.FakeSES

val ses = FakeSES().start()

```



