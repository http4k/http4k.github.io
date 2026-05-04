# AWS: Scheduler


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-scheduler")
    implementation("org.http4k:http4k-connect-amazon-scheduler-fake")
}
```

The Scheduler connector provides the following Actions:

    * CreateSchedule
    * CreateScheduleGroup
    * DeleteSchedule
    * DeleteScheduleGroup
    * GetSchedule
    * GetScheduleGroup
    * ListScheduleGroups
    * ListSchedules
     *  CreateInvalidation

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Default Fake port: 35165

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.scheduler

import org.http4k.chaos.start
import org.http4k.connect.amazon.scheduler.FakeScheduler

val scheduler = FakeScheduler().start()

```



