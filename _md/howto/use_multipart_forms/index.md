# Use Multipart Forms


Multipart form support is provided on 2 levels:

1. Through the creation of a `MultipartFormBody` which can be set on a `Request`.
1. Using the Lens system, which adds the facility to define form fields in a typesafe way, and to validate form contents (in either a strict (400) or "feedback" mode).

### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-multipart")
}
```

### Standard (non-typesafe) API 





```kotlin
package content.howto.use_multipart_forms

import org.http4k.client.ApacheClient
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {

    // extract the body from the request and then the fields/files from it
    val server = { r: Request ->
        val receivedForm = MultipartFormBody.from(r)
        println(receivedForm.fieldValues("field"))
        println(receivedForm.field("field2"))
        println(receivedForm.files("file"))

        Response(OK)
    }.asServer(SunHttp(8000)).start()

    // add fields and files to the multipart form body
    val body = MultipartFormBody()
        .plus("field" to "my-value")
        .plus("field2" to MultipartFormField("my-value2", listOf("my-header" to "my-value")))
        .plus(
            "file" to MultipartFormFile(
                "image.txt",
                ContentType.OCTET_STREAM,
                "somebinarycontent".byteInputStream()
            )
        )

    // we need to set both the body AND the correct content type header on the the request
    val request = Request(POST, "http://localhost:8000")
        .header("content-type", "multipart/form-data; boundary=${body.boundary}")
        .body(body)

    println(ApacheClient()(request))

    server.stop()
}

```



### Lens (typesafe, validating) API - reads ALL contents onto disk/memory 





```kotlin
package content.howto.use_multipart_forms

import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.server.SunHttp
import org.http4k.server.asServer

data class Name(val value: String)

fun main() {
    // define fields using the standard lens syntax
    val nameField = MultipartFormField.string().map(::Name, Name::value).required("name")
    val imageFile = MultipartFormFile.optional("image")

    // add fields to a form definition, along with a validator
    val strictFormBody =
        Body.multipartForm(Validator.Strict, nameField, imageFile, diskThreshold = 5).toLens()

    val server = ServerFilters.CatchAll().then { r: Request ->

        // to extract the contents, we first extract the form and then extract the fields from
        // it using the lenses
        // NOTE: we are "using" the form body here because we want to close the underlying
        // file streams
        strictFormBody(r).use {
            println(nameField(it))
            println(imageFile(it))
        }

        Response(OK)
    }.asServer(SunHttp(8000)).start()

    // creating valid form using "with()" and setting it onto the request. The content type
    // and boundary are taken care of automatically
    val multipartform = MultipartForm().with(
        nameField of Name("rita"),
        imageFile of MultipartFormFile(
            "image.txt",
            ContentType.OCTET_STREAM,
            "somebinarycontent".byteInputStream()
        )
    )
    val validRequest = Request(POST, "http://localhost:8000")
        .with(strictFormBody of multipartform)

    println(ApacheClient()(validRequest))

    server.stop()
}

```



### Streaming - iterate over Multiparts 





```kotlin
package content.howto.use_multipart_forms

import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.MultipartEntity
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.multipartIterator
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {

    val server = ServerFilters.CatchAll().then { r: Request ->

        // here we are iterating over the multiparts as we read them out of the input
        val fields = r.multipartIterator().asSequence()
            .fold(emptyList<MultipartEntity.Field>()) { memo, next ->
                when (next) {
                    is MultipartEntity.File -> {
                        // do something with the file right here...
                        // like stream it to another server
                        memo
                    }

                    is MultipartEntity.Field -> memo.plus(next)
                }
            }

        println(fields)

        Response(OK)
    }.asServer(SunHttp(8000)).start()

    println(ApacheClient()(buildMultipartRequest()))

    server.stop()
}

private fun buildMultipartRequest(): Request {
    // define fields using the standard lens syntax
    val nameField = MultipartFormField.string().map(::Name, Name::value).required("name")
    val imageFile = MultipartFormFile.optional("image")

    // add fields to a form definition, along with a validator
    val strictFormBody =
        Body.multipartForm(Validator.Strict, nameField, imageFile, diskThreshold = 5).toLens()

    val multipartform = MultipartForm().with(
        nameField of Name("rita"),
        imageFile of MultipartFormFile(
            "image.txt",
            ContentType.OCTET_STREAM,
            "somebinarycontent".byteInputStream()
        )
    )
    return Request(POST, "http://localhost:8000")
        .with(strictFormBody of multipartform)
}

```



### Processing Files with a Filter and convert to standard form 





```kotlin
package content.howto.use_multipart_forms

import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.MultipartEntity
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ProcessFiles
import org.http4k.filter.ServerFilters
import org.http4k.lens.FormField
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.lens.webForm
import org.http4k.server.SunHttp
import org.http4k.server.asServer

data class AName(val value: String)

fun main() {

    val server = ServerFilters.ProcessFiles { multipartFile: MultipartEntity.File ->
        // do something with the file right here... like stream it to another server and
        // return the guide.reference
        println(String(multipartFile.file.content.readBytes()))
        multipartFile.file.filename
    }.then { req: Request ->
        // this is the web-form definition - it is DIFFERENT to the multipart form definition,
        // because the fields and content-type have been replaced in the ProcessFiles filter
        val nameField = FormField.map(::AName, AName::value).required("name")
        val imageFile = FormField.optional("image")
        val body = Body.webForm(Validator.Strict, nameField, imageFile).toLens()

        println(body(req))

        Response(OK)
    }.asServer(SunHttp(8000)).start()

    println(ApacheClient()(buildValidMultipartRequest()))

    server.stop()
}

private fun buildValidMultipartRequest(): Request {
    // define fields using the standard lens syntax
    val nameField = MultipartFormField.string().map(::AName, AName::value).required("name")
    val imageFile = MultipartFormFile.optional("image")

    // add fields to a form definition, along with a validator
    val strictFormBody =
        Body.multipartForm(Validator.Strict, nameField, imageFile, diskThreshold = 5).toLens()

    val multipartform = MultipartForm().with(
        nameField of AName("rita"),
        imageFile of MultipartFormFile(
            "image.txt",
            ContentType.OCTET_STREAM,
            "somebinarycontent".byteInputStream()
        )
    )
    return Request(POST, "http://localhost:8000").with(strictFormBody of multipartform)
}

```



### Multipart combined with typesafe contract (OpenApi) 





```kotlin
package content.howto.use_multipart_forms


import org.http4k.contract.PreFlightExtraction
import org.http4k.contract.contract
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator.Strict
import org.http4k.lens.instant
import org.http4k.lens.multipartForm
import org.http4k.server.ApacheServer
import org.http4k.server.asServer


fun main() {
    val documentPart = MultipartFormFile.required("document")
    val ownerPart = MultipartFormField.string().required("owner")
    val signaturePart = MultipartFormField.string().instant().required("signedAt")

    val formLens = Body.multipartForm(Strict, documentPart, ownerPart, signaturePart).toLens()

    val handler = contract {
        renderer = OpenApi3(ApiInfo("My great API", "v1.0"), Jackson)
        descriptionPath = "/openapi.json"

        routes += "/api/document-upload" meta {
            summary = "Uploads a document including the owner name and when it was signed"

            // required to avoid reading the multipart stream twice!
            preFlightExtraction = PreFlightExtraction.IgnoreBody

            receiving(formLens)
            returning(OK)
        } bindContract POST to { req ->
            formLens(req).use {
                val doc = documentPart(it)
                val owner = ownerPart(it)
                val signatureDate = signaturePart(it)
                //process file...
                Response(OK).body("${doc.filename} by $owner, signed at $signatureDate")
            }
        }
    }

    /**
     * example request:
     * curl -v -H 'Content-Type: multipart/form-data' \
     *      -F owner="John Doe" \
     *      -F signedAt="2011-12-03T10:15:30Z" \
     *      -F document=@README.md \
     *      http://localhost:8081/api/document-upload
     */

    handler.asServer(ApacheServer(8081)).start()
}


```



