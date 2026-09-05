# AWS: IoT Jobs Data Plane


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.58.0.0"))

    implementation("org.http4k:http4k-connect-amazon-iotjobsdataplane")
    implementation("org.http4k:http4k-connect-amazon-iotjobsdataplane-fake")
}
```


The IoT Jobs Data Plane connector is the device side of Jobs - the API a Thing uses to find out what work it has been
given and to report back on it. It provides the following Actions:

     *  DescribeJobExecution
     *  GetPendingJobExecutions
     *  StartNextPendingJobExecution
     *  UpdateJobExecution

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

The service signs as `iot-jobs-data` rather than `iot`, and is addressed at
`https://data.jobs.iot.<region>.amazonaws.com` - derived from the Region, but overridable for accounts using a
custom endpoint.

`DescribeJobExecution` accepts the reserved `JobId.NEXT` (`$next`) to peek at the job the device would be given
next. It is read-only, so it is safe to poll on every connect, unlike `StartNextPendingJobExecution` which claims
the execution.

This module is the device half of a pair: the cloud-side API which creates the Jobs lives in
[IoT Core](/ecosystem/connect/reference/amazon/iot/). `FakeIotJobsDataPlane` takes the same `Storage<StoredJob>` as `FakeIot`, so one store passed
to both gives the control plane and the device API a single jobs state to work against.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.iotjobsdataplane

import org.http4k.aws.AwsCredentials
import org.http4k.connect.amazon.core.model.ARN
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.iot.FakeIot
import org.http4k.connect.amazon.iot.StoredJob
import org.http4k.connect.amazon.iot.createJob
import org.http4k.connect.amazon.iotjobsdataplane.FakeIotJobsDataPlane
import org.http4k.connect.amazon.iotjobsdataplane.Http
import org.http4k.connect.amazon.iotjobsdataplane.IotJobsDataPlane
import org.http4k.connect.amazon.iotjobsdataplane.describeJobExecution
import org.http4k.connect.amazon.iotjobsdataplane.model.JobExecutionStatus.SUCCEEDED
import org.http4k.connect.amazon.iotjobsdataplane.model.JobId
import org.http4k.connect.amazon.iotjobsdataplane.model.ThingName
import org.http4k.connect.amazon.iotjobsdataplane.startNextPendingJobExecution
import org.http4k.connect.amazon.iotjobsdataplane.updateJobExecution
import org.http4k.connect.storage.InMemory
import org.http4k.connect.storage.Storage
import org.http4k.core.HttpHandler
import org.http4k.filter.debug
import org.http4k.connect.amazon.iot.model.JobId as IotJobId

fun main() {
    val region = Region.of("us-east-1")
    val thingName = ThingName.of("my-thing")

    // one shared store means the control plane and the device API see the same jobs state
    val store = Storage.InMemory<StoredJob>()

    // the cloud side creates a job through the (fake) control plane...
    FakeIot(store).client().createJob(
        jobId = IotJobId.of("firmware-update-1"),
        targets = listOf(ARN.of("arn:aws:iot:$region:000000000000:thing/${thingName.value}")),
        document = """{"operation":"firmware-update","url":"https://example.com/firmware.bin"}"""
    )

    // ...and the device walks it through the jobs data plane
    val http: HttpHandler = FakeIotJobsDataPlane(store)
    val device = IotJobsDataPlane.Http(region, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // $next is read-only: safe to poll on every connect
    val next = device.describeJobExecution(thingName, JobId.NEXT)

    // claim it, then report the terminal status
    device.startNextPendingJobExecution(thingName, statusDetails = mapOf("step" to "downloading"))
    device.updateJobExecution(thingName, JobId.of("firmware-update-1"), SUCCEEDED)
}

```



### Default Fake port: 34570

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.iotjobsdataplane

import org.http4k.chaos.start
import org.http4k.connect.amazon.iotjobsdataplane.FakeIotJobsDataPlane

val iotJobsDataPlane = FakeIotJobsDataPlane().start()

```



