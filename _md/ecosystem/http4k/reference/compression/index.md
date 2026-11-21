# Compression


### Installation (Gradle)

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.61.0.0"))


    implementation("org.http4k:http4k-compression")

    // if you need Brotli, add the native library for each platform you run on:
    // native-linux-x86_64, native-linux-aarch64, native-osx-x86_64, native-osx-aarch64, native-windows-x86_64
    runtimeOnly("com.aayushatharva.brotli4j:native-linux-x86_64:1.24.0")
}
```

### About

http4k Core ships negotiating `Compression` and `Decompression` filters which work with any `ContentEncoding`. Gzip and Deflate are built in. This module adds two more:

- `ContentEncoding.Brotli(quality)` - quality from 0 (fastest) to 11 (smallest). Default is 4.
- `ContentEncoding.Zstd(level)` - level from 1 (fastest) to 22 (smallest). Default is 3. Decoding enforces the 8MB window limit from RFC 9659, which bounds decoder memory per request.

Pass encodings in order of preference. The server picks the best one the client accepts (by `accept-encoding`) and decompresses requests sent in any of them.

The filters are:

- **Server:** `ServerFilters.Compression`, or `ResponseFilters.Compression` and `RequestFilters.Decompression` separately. Pass `shouldCompress` to choose which responses get compressed, such as `compressibleContentTypes(...)`.
- **Client:** `ClientFilters.Compression` compresses requests with the first encoding and decompresses responses. `ClientFilters.Decompression` only decompresses responses.
- **SSE:** `ServerFilters.SseCompression` (in `http4k-realtime-core`) compresses event streams message by message. `PolyFilters.Compression` applies HTTP and SSE compression together.

Decompression is capped by `maxDecompressedSize`. The deprecated `GZip`/`GunZip` filters map to these filters with `ContentEncoding.Gzip()`.

#### Code





```kotlin
package content.ecosystem.http4k.reference.compression

import org.http4k.core.Brotli
import org.http4k.core.ContentEncoding
import org.http4k.core.Gzip
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Zstd
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ServerFilters

fun main() {
    // encodings are offered in order of server preference
    val encodings = arrayOf(ContentEncoding.Zstd(), ContentEncoding.Brotli(), ContentEncoding.Gzip())

    val server = ServerFilters.Compression(*encodings)
        .then { Response(OK).body("hello world ".repeat(100)) }

    val raw = server(Request(GET, "/").header("accept-encoding", "br, gzip"))
    println(raw.header("content-encoding"))
    println(raw.body.length)

// Produces:
//    br
//    24

    val client = ClientFilters.Decompression(*encodings).then(server)
    val decoded = client(Request(GET, "/").header("accept-encoding", "zstd"))
    println(decoded.bodyString().length)

// Produces:
//    1200
}

```



