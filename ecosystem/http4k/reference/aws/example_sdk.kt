package content.ecosystem.http4k.reference.aws

import org.http4k.aws.AwsSdkClient
import org.http4k.client.OkHttp
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest

fun main() {
    val http4kClient = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())

    val s3 = S3Client.builder()
        .region(EU_WEST_1)
        .credentialsProvider { AwsBasicCredentials.create("accessKey", "secret") }
        .httpClient(AwsSdkClient(http4kClient))
        .build()

    s3.createBucket(CreateBucketRequest.builder().bucket("hello").build())
}
