# Use HTML Forms


HTML form support is provided on 2 levels:

1. Through the use of `form()` extension methods on `Request` to get/set String values.
1. Using the Lens system, which adds the facility to define form fields in a typesafe way, and to validate form contents (in either a strict (400) or "feedback" mode).

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
}
```

### Standard (non-typesafe) API 





```kotlin
package content.howto.use_html_forms

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

fun main() {
    // form(name: String, value: String?) parses the request body on each invocation
    val request = Request(GET, "/").form("name", "rita").form("age", "55")

    // form(vararg formData: Pair<String, String>) allows you to add multiple form fields to
    // the request while only parsing the request body once
    val allInOneRequest = Request(GET, "/").form("name" to "rita", "age" to "55")

    // form(name: String) parses the request body on each invocation
    assertEquals("rita", request.form("name"))
    assertEquals("55", request.form("age"))
    assertNull(request.form("height"))

    assertEquals("rita", allInOneRequest.form("name"))
    assertEquals("55", allInOneRequest.form("age"))
    assertNull(allInOneRequest.form("height"))

    // toParametersMap() gives form as map
    val parameters: Map<String, List<String?>> = request.form().toParametersMap()
    assertEquals("rita", parameters.getFirst("name"))
    assertEquals(listOf("55"), parameters["age"])
    assertNull(parameters["height"])
}

```



### Lens (typesafe, validating) API 





```kotlin
package content.howto.use_html_forms

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.lens.FormField
import org.http4k.lens.Header
import org.http4k.lens.LensFailure
import org.http4k.lens.Validator
import org.http4k.lens.WebForm
import org.http4k.lens.int
import org.http4k.lens.webForm

data class Name(val value: String)

fun main() {

    // define fields using the standard lens syntax
    val ageField = FormField.int().required("age")
    val nameField = FormField.map(::Name, Name::value).optional("name")

    // add fields to a form definition, along with a validator
    val strictFormBody = Body.webForm(Validator.Strict, nameField, ageField).toLens()
    val feedbackFormBody = Body.webForm(Validator.Feedback, nameField, ageField).toLens()

    val invalidRequest = Request(GET, "/")
        .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)

    // the "strict" form rejects (throws a LensFailure) because "age" is required
    try {
        strictFormBody(invalidRequest)
    } catch (e: LensFailure) {
        println(e.message)
    }

    // the "feedback" form doesn't throw, but collects errors to be reported later
    val invalidForm = feedbackFormBody(invalidRequest)
    println(invalidForm.errors)

    // creating valid form using "with()" and setting it onto the request
    val webForm = WebForm().with(ageField of 55, nameField of Name("rita"))
    val validRequest = Request(GET, "/").with(strictFormBody of webForm)

    // to extract the contents, we first extract the form and then extract the fields from it
    // using the lenses
    val validForm = strictFormBody(validRequest)
    val age = ageField(validForm)
    println(age)
}

```



