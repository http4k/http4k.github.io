# Google: Analytics GA4


### Installation

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-google-analytics-ga4")
    implementation("org.http4k:http4k-connect-google-analytics-ga4-fake")
}
```

The GA connector provides the following Actions:

     *  PageView
     *  Event

### Default Fake port: 35628

To start:





```kotlin
package content.ecosystem.connect.reference.google.analytics_ga4

import org.http4k.chaos.start
import org.http4k.connect.google.analytics.ga4.FakeGoogleAnalytics

val googleAnalytics = FakeGoogleAnalytics().start()

```




