# Templating



### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))


    // Dust: 
    implementation("org.http4k:http4k-template-dust")
    
    // Freemarker: 
    implementation("org.http4k:http4k-template-freemarker")
    
    // Handlebars: 
    implementation("org.http4k:http4k-template-handlebars")
    
    // HTML Flow: 
    implementation("org.http4k:http4k-template-htmlflow")
    
    // Pug4j: 
    implementation("org.http4k:http4k-template-pug4j")
    
    // JTE: 
    implementation("org.http4k:http4k-template-jte")
    
    // Pebble: 
    implementation("org.http4k:http4k-template-pebble")
    
    // Rocker: 
    implementation("org.http4k:http4k-template-rocker")
    
    // Thymeleaf: 
    implementation("org.http4k:http4k-template-thymeleaf")
}
```

### About
The http4k templating API provides a standard mechanism for rendering using common templating libraries. Simply implement the `ViewModel` interface on a model class and pass it to the renderer to get a string. All of the implementations support view rendering using the following strategies:

* Cached on the classpath
* Cached from the filesystem
* Hot-Reloading from the filesystem

The examples below are for Handlebars, but the others have the same APIs:

#### Code  





```kotlin
package content.ecosystem.http4k.reference.templating

import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import java.io.File

data class Person(val name: String, val age: Int) : ViewModel

fun main() {

    // first, create a Renderer - this can be a Caching instance or a HotReload for development
    val renderer = HandlebarsTemplates().HotReload("src/test/resources")

    // first example uses a renderer to create a string
    val app: HttpHandler = {
        val viewModel = Person("Bob", 45)
        val renderedView = renderer(viewModel)
        Response(OK).body(renderedView)
    }
    println(app(Request(GET, "/someUrl")))

    // the lens example uses the Body.viewModel to also set the content type, and avoid using Strings
    val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()

    val appUsingLens: HttpHandler = {
        Response(OK).with(viewLens of Person("Bob", 45))
    }

    println(appUsingLens(Request(GET, "/someUrl")))

    // overwrite the content - this will prove the hot reload works!
    File("src/test/resources/content.ecosystems.http4k.reference/templating/Person.hbs").writer()
        .use { it.write("{{name}} is not {{age}} years old") }

    println(appUsingLens(Request(GET, "/someUrl")))
}

```



### Notes for Rocker
Rocker differs slightly from the dynamic templating engines in that it generates Java classes at compile time. In order to fit this into the http4k model, we have created a special superclass `RockerViewModel` (which combines the Rocker and the http4k `ViewModel` interfaces into a common supertype). This should be used as the `extendsModelClass` property in the generation process by configuration. Note that as the generated classes are Java and NOT Kotlin, Java syntax should be used inside the view files (which need to be named `Xyz.rocker.html`).

### Notes for Pebble
The way the underlying model is exposed for Pebble templates differs from the rest of the templating engines.
The properties of the `ViewModel` object are bound to the view context under a `model` key, rather than directly into the template context itself.  

This means that the `model.` prefix is required to access the properties of the underlying object in a Pebble template.

In other words, rendering a `firstName` property, for example, is done using:
```
{{ model.firstName }}
```
instead of 
```
{{ firstName }}
```

[http4k]: https://http4k.org

