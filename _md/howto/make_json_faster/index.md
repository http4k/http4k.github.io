# Make JSON Faster



The standard JSON format modules included in Http4k (like Jackson and Moshi) are good enough for most applications.
However there are scenarios where you may want to further optimize them for runtime performance,
or to achieve acceptable cold start times in serverless deployments.

## kotlinx.serialization

[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) is a relatively simple and effective option.
It's fully supported by Http4k, but we'll go over it first as a baseline to compare against the others.
It generates the serialization adapters at compile time, which means it doesn't need reflection during runtime.

kotlinx.serialization is a very fast and efficient module, but there are some caveats to consider:

1. No support for many builtin JVM types (e.g. java.time, UUID)
2. Custom serializers must be registered via annotation
3. No support for values4k classes

### Gradle

```kotlin
plugins {
    kotlin("plugin.serialization") version "<kotlin version>"
}

dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-format-kotlinx-serialization")
}
```

### Example 





```kotlin
package content.howto.make_json_faster

import kotlinx.serialization.Serializable
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization
import java.util.UUID

// You must annotate your class to generate the adapter
@Serializable
data class KotlinXCat(
    val id: String, // UUID not supported
    val name: String
)

// use the builtin http4k module
private val json = KotlinxSerialization

fun main() {
    val cat = KotlinXCat(UUID.randomUUID().toString(), "Kratos")

    // serialize
    val string = json.asFormatString(cat)
        .also(::println)

    // deserialize
    json.asA<KotlinXCat>(string)
        .also(::println)

    // make a lens
    val lens = json.autoBody<KotlinXCat>().toLens()
    Request(Method.GET, "foo").with(lens of cat)
}


```



## Moshi Metadata Reflect

[moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/tree/main/moshi-metadata-reflect) is a plugin for Moshi that replaces the heavyweight `kotlin-reflect` with the lighter `kotlinx-metadata-jvm`.
While this module still uses reflection, eliminating `kotlin-reflect` from your classpath will reduce your binary size by several MB and make a respectable improvement in cold start time.

Moshi Metadata Reflect is very simple, and doesn't require any gradle plugins or finicky code generation.
However, it will bring the smallest performance benefit out of these options.

### Gradle

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-format-moshi") {
        exclude("org.jetbrains.kotlin", "kotlin-reflect") // Exclude kotlin-reflect
    }
    implementation("dev.zacsweers.moshix:moshi-metadata-reflect:<moshi metadata version>")
}
```

### Example 





```kotlin
package content.howto.make_json_faster

import com.squareup.moshi.Moshi
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.ConfigurableMoshi
import org.http4k.format.EventAdapter
import org.http4k.format.ListAdapter
import org.http4k.format.MapAdapter
import org.http4k.format.ThrowableAdapter
import org.http4k.format.asConfigurable
import org.http4k.format.withStandardMappings
import java.util.UUID

private val json = ConfigurableMoshi(
    Moshi.Builder()
        .addLast(EventAdapter)
        .addLast(ThrowableAdapter)
        .addLast(ListAdapter)
        .addLast(MapAdapter)
        .asConfigurable(MetadataKotlinJsonAdapterFactory()) // <-- moshi-metadata-reflect
        .withStandardMappings()
        .done()
)

data class MoshiCat(val id: UUID, val name: String)

fun main() {
    val cat = MoshiCat(UUID.randomUUID(), "Kratos")

    // serialize
    val string = json.asFormatString(cat)
        .also(::println)

    // deserialize
    json.asA<MoshiCat>(string)
        .also(::println)

    // make a lens
    val lens = json.autoBody<MoshiCat>().toLens()
    Request(Method.GET, "foo").with(lens of cat)
}

```



## Kotshi

[Kotshi](https://github.com/ansman/kotshi) is a plugin for Moshi.  It's very similar to [moshi-kotlin-codegen](https://github.com/square/moshi/tree/master/moshi-kotlin-codegen)
in that they both generate Moshi adapters at compile time.
However, since `moshi-kotlin-codegen` still requires `kotlin-reflect` at runtime, `kotshi` can bring much greater cold start performance gains.

Kotshi is a very fast and efficient module, but is the most difficult to set up.
It requires a gradle plugin, several dependencies, and depends on code that only exists after compile.

### Gradle

```kotlin
plugins {
    // The KSP plugin is required to generate the adapters at compile time
    id("com.google.devtools.ksp") version "<ksp plugin version version>"
}

dependencies {
    implementation("org.http4k:http4k-format-moshi") {
        exclude("org.jetbrains.kotlin", "kotlin-reflect") // Exclude kotlin-reflect
    }
    
    // Get the Kotshi runtime library and configure KSP to generate the adapters
    implementation("se.ansman.kotshi:api:<kotshi version>")
    ksp("se.ansman.kotshi:compiler:<kotshi version>")
}
```

### Example 





```kotlin
package content.howto.make_json_faster

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.ConfigurableMoshi
import org.http4k.format.EventAdapter
import org.http4k.format.ListAdapter
import org.http4k.format.MapAdapter
import org.http4k.format.ThrowableAdapter
import org.http4k.format.asConfigurable
import org.http4k.format.withStandardMappings
import se.ansman.kotshi.JsonSerializable
import se.ansman.kotshi.KotshiJsonAdapterFactory
import java.util.UUID

// Annotation is required to generate the adapter
@JsonSerializable
data class KotshiCat(val id: UUID, val name: String)

// Build the kotshi adapter
@KotshiJsonAdapterFactory
private object ExampleJsonAdapterFactory : JsonAdapter.Factory by
    KotshiExampleJsonAdapterFactory // this class will be generated during compile

private val json = ConfigurableMoshi(
    Moshi.Builder()
        .add(ExampleJsonAdapterFactory) // inject kotshi here
        .addLast(EventAdapter)
        .addLast(ThrowableAdapter)
        .addLast(ListAdapter)
        .addLast(MapAdapter)
        .asConfigurable()
        .withStandardMappings()
        .done()
)

fun main() {
    val cat = KotshiCat(UUID.randomUUID(), "Kratos")

    // serialize
    val string = json.asFormatString(cat)
        .also(::println)

    // deserialize
    json.asA<KotshiCat>(string)
        .also(::println)

    // make a lens
    val lens = json.autoBody<KotshiCat>().toLens()
    Request(Method.GET, "foo").with(lens of cat)
}

```



