package cookbook.multipart_forms

import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.server.SunHttp
import org.http4k.server.asServer

data class Name(val value: String)

fun main(args: Array<String>) {
    // define fields using the standard lens syntax
    val nameField = MultipartFormField.map(::Name, Name::value).required("name")
    val imageFile = MultipartFormFile.optional("image")

    // add fields to a form definition, along with a validator
    val strictFormBody = Body.multipartForm(Validator.Strict, nameField, imageFile).toLens()

    val server = { r: Request ->
        // to extract the contents, we first extract the form and then extract the fields from it using the lenses
        val validForm = strictFormBody.extract(r)
        println(nameField.extract(validForm))
        println(imageFile.extract(validForm))

        Response(Status.OK)
    }.asServer(SunHttp(8000)).start()

    // creating valid form using "with()" and setting it onto the request
    val multipartform = MultipartForm().with(
        nameField of Name("rita"),
        imageFile of FormFile("image.txt", ContentType.OCTET_STREAM, "somebinarycontent".byteInputStream()))
    val validRequest = Request(Method.POST, "http://localhost:8000").with(strictFormBody of multipartform)

    println(ApacheClient()(validRequest))

    server.stop()
}