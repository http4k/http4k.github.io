# Create a custom JSON marshaller


### Gradle setup

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-jackson")
}
```

### Custom auto-mapping JSON configurations

**http4k** declares an extended set of "primitive" types which it can marshall out of the box - this includes the
various http4k primitives (Uri, Status), as well as a bunch of common types from the JDK such as the DateTime classes
and Exceptions. These primitives types cannot be marshalled as top-level JSON structures on their own so should be
contained in a custom wrapper class before transmission.

You can declare your own custom marshaller by reimplementing the Json instance and adding mappings for your own types -
either uni or bi-directional.

This ability to render custom types through different JSON marshallers allows API users to provide different "views" for
different purposes - for example we may wish to hide the values of some fields in the output, as below:

### Example - Representing MicroTypes/TinyTypes as Strings in JSON

MicroTypes (aka Tiny Types) are popular in Kotlin providing type-safety throughout a codebase, ensuring amongst other things that method 
parameters are not accidentally permuted. An example of a simple microtype might be:





```kotlin
package content.howto.create_a_custom_json_marshaller

data class CustomerName(val value: String)
data class Customer(val name: CustomerName)

```



Using the standard mapper, a `Customer` "Bob", would be represented as the json





```kotlin
package content.howto.create_a_custom_json_marshaller

val bob = Customer(name = CustomerName("Bob"))

```



```json
{
    "name": {
        "value": "Bob"
    }
}
```

However, it might be preferable to represent `CustomerName` as a plain string:

```json
{
    "name": "Bob"
}
```

To achieve this, there are a few simple steps - this example uses Jackson, but there are equivalent configuration
schemes for the other supported JSON libraries

1. Use the http4k `ConfigurableJackson` to get a base configuration





```kotlin
package content.howto.create_a_custom_json_marshaller

import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.http4k.format.ConfigurableJackson
import org.http4k.format.asConfigurable
import org.http4k.format.withStandardMappings

object MyJacksonSkeleton : ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable()
        .withStandardMappings()
        .done()
)

```



2. Modify it to meet your needs, registering type adapters for your types





```kotlin
package content.howto.create_a_custom_json_marshaller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.http4k.format.ConfigurableJackson
import org.http4k.format.asConfigurable
import org.http4k.format.text
import org.http4k.format.withStandardMappings

object MyJacksonConfig : ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable()
        .withStandardMappings()
        .text(::CustomerName, CustomerName::value)
        // .text(...) - repeat the registration for each type
        .done()
        .deactivateDefaultTyping()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
)

```



3. Reference this configuration in your code - particularly where using the `Body.auto<xxx>` pattern





```kotlin
package content.howto.create_a_custom_json_marshaller

import org.http4k.core.Body
import content.howto.create_a_custom_json_marshaller.MyJackson.auto

val lens = Body.auto<Customer>().toLens()

```



### Example - Representing MicroTypes using Values4k as Strings in JSON

This example uses value types from [Values4k](https://github.com/fork-handles/forkhandles/tree/trunk/values4k)

Firstly, define a value type using the standard values4k mechanism - note that the companion
object extends ValueFactory - this will be referenced in the type adapter later. The ValueFactory
also provides a number of convenience methods `CustomerName.of()`, `parse()`, `unwrap()`, and a mechanism
to validate the format of strings - very convenient to ensure that values are semantically valid throughout the entire system.





```kotlin
package content.howto.create_a_custom_json_marshaller

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory

class CustomerNameV4k(value: String) : StringValue(value) {
    companion object : StringValueFactory<CustomerNameV4k>(::CustomerNameV4k)
}

```



Then, define a `ConfigurableJackson` (Moshi...) with a type adaptor for your type





```kotlin
package content.howto.create_a_custom_json_marshaller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.http4k.format.ConfigurableJackson
import org.http4k.format.asConfigurable
import org.http4k.format.value
import org.http4k.format.withStandardMappings

object MyJacksonV4k : ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable()
        .withStandardMappings()
        .value(CustomerNameV4k)
        // .value(...) - repeat the registration for each type
        .done()
        .deactivateDefaultTyping()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
)

```



A full worked example is shown below.

#### Code





```kotlin
package content.howto.create_a_custom_json_marshaller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
// this import is important so you don't pick up the standard auto method!
import content.howto.create_a_custom_json_marshaller.MyJackson.auto
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.ConfigurableJackson
import org.http4k.format.asConfigurable
import org.http4k.format.text
import org.http4k.format.withStandardMappings

object MyJackson : ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable()
        .withStandardMappings()
        // declare custom mapping for our own types - this one represents our type as a
        // simple String
        .text(::PublicType, PublicType::value)
        // ... and this one shows a masked value and cannot be deserialised
        // (as the mapping is only one way)
        .text(SecretType::toString)
        .done()
        .deactivateDefaultTyping()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
)

data class PublicType(val value: String)
data class SecretType(val value: String) {
    override fun toString() = "****"
}

data class MyType(val public: PublicType, val hidden: SecretType)

fun main() {
    println(
        Response(OK).with(
            Body.auto<MyType>().toLens() of MyType(
                PublicType("hello"),
                SecretType("secret")
            )
        )
    )

    /** Prints:

    HTTP/1.1 200 OK
    content-type: application/json; charset=utf-8

    {"public":"hello","hidden":"****"}

     */
}

```



