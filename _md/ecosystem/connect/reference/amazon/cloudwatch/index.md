# AWS: CloudWatch


```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-amazon-cloudwatch")
    implementation("org.http4k:http4k-connect-amazon-cloudwatch-fake")
}
```


The CloudWatch connector provides the following Actions:

* DeleteAlarms
* DescribeAlarms
* DescribeAlarmsForMetric
* DisableAlarmActions
* EnableAlarmActions
* GetMetricData
* GetMetricStatistics
* ListMetrics
* ListTagsForResource
* PutCompositeAlarm
* PutMetricAlarm
* PutMetricData
* SetAlarmState
* TagResource
* UntagResource

The client APIs utilise the `http4k-platform-aws` module for request signing, which means no dependencies on the incredibly fat
Amazon-SDK JARs. This means this integration is perfect for running Serverless Lambdas where binary size is a
performance factor.

### Example usage





```kotlin
package content.ecosystem.connect.reference.amazon.cloudwatch

import dev.forkhandles.result4k.Result
import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.amazon.cloudwatch.CloudWatch
import org.http4k.connect.amazon.cloudwatch.FakeCloudWatch
import org.http4k.connect.amazon.cloudwatch.Http
import org.http4k.connect.amazon.cloudwatch.action.Metrics
import org.http4k.connect.amazon.cloudwatch.listMetrics
import org.http4k.connect.amazon.cloudwatch.model.MetricName
import org.http4k.connect.amazon.cloudwatch.model.Namespace
import org.http4k.connect.amazon.core.model.Region
import org.http4k.core.HttpHandler
import org.http4k.filter.debug

const val USE_REAL_CLIENT = false

fun main() {

    val http: HttpHandler = if (USE_REAL_CLIENT) JavaHttpClient() else FakeCloudWatch()

    // create a client
    val cloudWatch =
        CloudWatch.Http(Region.US_EAST_1, { AwsCredentials("accessKeyId", "secretKey") }, http.debug())

    // all operations return a Result monad of the API type
    val result: Result<Metrics, RemoteFailure> = cloudWatch.listMetrics(
        Dimensions = emptyList(),
        true,
        MetricName.of("foobar"),
        Namespace.of("foobar")
    )

    println(result)
}

```



### Default Fake port: 57564

To start:





```kotlin
package content.ecosystem.connect.reference.amazon.cloudwatch

import org.http4k.chaos.start
import org.http4k.connect.amazon.cloudwatch.FakeCloudWatch

val cloudWatch = FakeCloudWatch().start()

```



