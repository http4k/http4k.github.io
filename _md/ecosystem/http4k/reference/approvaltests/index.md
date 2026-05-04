# Testing: Approval


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-testing-approval")
}
```

### About
[Approval testing](http://approvaltests.com/) is a form of testing which allows the expected output of 
a test to be specified in a non-code but still source-controlled format, such as a text file. This is a powerful alternative to traditional assertion-based tests for a number of reasons:

1. It is often inconvenient and/or error prone to attempt to write assertions to cover the entirety of 
test output - examples of this include JSON, HTML or XML documents.
1. Output may not always be in a format that can be created easily in a test.
1. In case of a mismatch, output can be more efficiently diagnosed by the human eye.
1. The output of a test may change significantly in a short period (this is especially true for HTML 
content), but we also want to tightly control the contract.

The general idea for implementing this style of testing in http4k is based on the excellent 
[okeydoke](https://github.com/dmcg/okey-doke) library, and is centered around the idea of comparing 
the output of an HTTP operation - this is generally the `Response` content, but it can also be the 
`Request` if we are interested in testing construction of request content. 

For each test-case, a named `<test name>.approved` file is committed (under the `src/test/resources` 
folder), against which the test output can be compared by an `Approver` object injected into the test 
method. In case of a mismatch, an equivalent `<test name>.actual` file is written. This file can then 
be verified and if ok, renamed to become the approved file. To make this operation easier in the IDE, we
recommend the usage of the 
[IntelliJ OkeyDoke plugin](https://plugins.jetbrains.com/plugin/9424-okey-doke-support) which adds a 
mouse and keyboard shortcut to rename the file. 

The `http4k-testing-approval` module implements this functionality as a JUnit5 extension that 
will inject the `Approver` automatically into test methods.

### Standard Approval tests
By using the `ApprovalTest` extension, an instance of an `Approver` is injected into each test.

#### Code





```kotlin
package content.ecosystem.http4k.reference.approvaltests

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasStatus
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.http4k.testing.assertApproved
import org.http4k.testing.hasApprovedContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApprovalTest::class)
class ExampleApprovalTest {

    private val app: HttpHandler = { Response(OK).body("hello world") }

    @Test
    fun `check response content`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/url")))
    }

    @Test
    fun `check response content with expected status`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/url")), OK)
    }

    @Test
    fun `check request content`(approver: Approver) {
        approver.assertApproved(Request(GET, "/url").body("foobar"))
    }

    @Test
    fun `combine approval with hamkrest matcher`(approver: Approver) {
        assertThat(app(Request(GET, "/url")), hasStatus(OK).and(approver.hasApprovedContent()))
    }
}

```



### Content-type specific Approval tests
Because so many APIs are based around messages with a particular content type, the 
module also provides Junit 5 extensions that will:

1. Check for the presence of the a particular `content-type` on the `HttpMessage` under test and fail if it is not valid.
1. Validate that the `HttpMessage` actually contains valid content for the content type.
1. Format and compare the approval output as pretty-printed version. Note that by default the http4k format modules use compact printing to conserve message space.

The module also provides the following built-in extensions:

- `HtmlApprovalTest`
- `JsonApprovalTest`
- `XmlApprovalTest`

#### Code





```kotlin
package content.ecosystem.http4k.reference.approvaltests

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Header.CONTENT_TYPE
import org.http4k.testing.Approver
import org.http4k.testing.JsonApprovalTest
import org.http4k.testing.assertApproved
import org.http4k.testing.hasApprovedContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JsonApprovalTest::class)
class ExampleJsonApprovalTest {

    private val app: HttpHandler = {
        Response(OK)
            .with(CONTENT_TYPE of APPLICATION_JSON)
            .body("""{"message":"value"}""")
    }

    @Test
    fun `check response content`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/url")))
    }

    @Test
    fun `check response content with expected status`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/url")), OK)
    }

    @Test
    fun `check request content`(approver: Approver) {
        approver.assertApproved(
            Request(GET, "/url").with(CONTENT_TYPE of APPLICATION_JSON)
                .body("""{"message":"value"}""")
        )
    }

    @Test
    fun `combine approval with hamkrest matcher`(approver: Approver) {
        assertThat(app(Request(GET, "/url")), hasStatus(OK).and(approver.hasApprovedContent()))
    }
}

```



### Implementing custom JUnit Extensions
As with the rest of http4k, a base implementation, `BaseApprovalTest` of the Junit5 Extension is 
provided, allowing API users to implement custom approval schemes or non-FS based approaches for 
storing the approval files.

[http4k]: https://http4k.org

