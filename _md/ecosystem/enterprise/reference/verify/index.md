# Verify


The **http4k Verify** plugin automatically verifies cosign signatures on every http4k dependency before your code compiles. It covers JARs, CycloneDX SBOMs, SLSA provenance attestations, and license compliance reports — and exports all verification artifacts to your project for independent inspection and attestation.

### Installation (Gradle)

### Gradle setup

```kotlin
plugins {
    id("org.http4k.verify") <version>
}
```

The plugin automatically downloads the http4k [signing key list](https://http4k.org/.well-known/cosign-keys.json), resolves sigstore bundles for all http4k dependencies, and verifies every signature using the correct key for each artifact. If any artifact has been tampered with, the build fails. Verification results are cached locally — subsequent builds have zero overhead until dependencies change.

### (Optional) Configuration

The plugin works out of the box with no configuration required, but if you want to customize the plugin, you can configure it in `build.gradle.kts`:





```kotlin
http4kVerify {
    // Fail the build if any signature is invalid (default: true)
    failOnError = true

    // Override the key list URL (default: https://http4k.org/.well-known/cosign-keys.json)
    keyListUrl = "https://http4k.org/.well-known/cosign-keys.json"

    // Or pin a single public key instead of using the key list
    publicKey = file("path/to/cosign.pub")
}

```



### Running Verification

Run verification explicitly with:





```bash
./gradlew verifyHttp4kDependencies

```



Example output:





```bash
Downloading key list from https://http4k.org/.well-known/cosign-keys.json
Verifying 3 http4k module(s)...
  org.http4k:http4k-core:0.0.0.0              jar ✓   sbom ✓   provenance ✓   license ✓
  org.http4k:http4k-format-jackson:0.0.0.0    jar ✓   sbom ✓   provenance ✓   license ✓
  org.http4k:http4k-server-undertow:0.0.0.0   jar ✓   sbom ✓   provenance ✓   license ✓
Verified: 3 modules, 12 signatures
Verification artifacts exported to build/http4k-verify

```



### When Verification Fails

If any artifact has been tampered with or a signature does not match, the plugin reports the failure and stops the build:





```bash
Downloading key list from https://http4k.org/.well-known/cosign-keys.json
Verifying 3 http4k module(s)...
  org.http4k:http4k-core:0.0.0.0              jar ✗   sbom ✓   provenance ✓   license ✓
    FAIL: jar — Artifact digest mismatch — file may have been tampered with
  org.http4k:http4k-format-jackson:0.0.0.0    jar ✓   sbom ✓   provenance ✓   license ✓
  org.http4k:http4k-server-undertow:0.0.0.0   jar ✓   sbom ✓   provenance ✓   license ✓
Verified: 3 modules, 11 signatures, 1 failed
Verification artifacts exported to build/http4k-verify

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':verifyHttp4kDependencies'.
> http4k artifact verification failed for 1 signature(s)

```



Each failed check is marked with **✗** and a `FAIL` line showing the reason. The build will fail with a `GradleException` by default. To continue the build despite failures (e.g. for CI reporting), set `failOnError` to `false` in the plugin configuration.

### Exported Artifacts

Every time verification runs, all resolved verification artifacts are exported to `build/http4k-verify/` in your project directory. This gives you full visibility into exactly what was verified, and allows you to run your own independent checks.





```bash
build/http4k-verify/
    cosign-keys.json
    verification-report.json
    org.http4k/
        http4k-core/0.0.0.0/
            http4k-core-0.0.0.0.jar.sha256
            http4k-core-0.0.0.0-jar-sigstore.json
            http4k-core-0.0.0.0-cyclonedx.json
            http4k-core-0.0.0.0-cyclonedx-sigstore.json
            http4k-core-0.0.0.0-provenance.json
            http4k-core-0.0.0.0-provenance-sigstore.json
            http4k-core-0.0.0.0-license-report.json
            http4k-core-0.0.0.0-license-report-sigstore.json
        http4k-format-jackson/0.0.0.0/
            ...

```



For each http4k module, the following are exported:

- **`.jar.sha256`** — SHA-256 hash of the JAR file
- **`-jar-sigstore.json`** — Cosign signature bundle for the JAR
- **`-cyclonedx.json`** — CycloneDX SBOM listing all dependencies
- **`-cyclonedx-sigstore.json`** — Cosign signature bundle for the SBOM
- **`-provenance.json`** — SLSA Build L2 provenance attestation
- **`-provenance-sigstore.json`** — Cosign signature bundle for the provenance
- **`-license-report.json`** — Curated license compliance report
- **`-license-report-sigstore.json`** — Cosign signature bundle for the license report
- **`cosign-keys.json`** — The key list used for verification (multi-key mode)
- **`cosign.pub`** — The public key used for verification (single-key mode only)

You can use these exported files to independently verify any artifact with cosign:





```bash
cosign verify-blob http4k-core-0.0.0.0.jar \
    --key cosign.pub \
    --bundle http4k-core-0.0.0.0-jar-sigstore.json \
    --private-infrastructure

```



### Verification Report

A JSON verification report is always generated at `build/http4k-verify/verification-report.json`. This report serves as an attestation record — proof that specific http4k dependencies with specific hashes were verified against specific signatures at a specific time.





```json
{
  "timestamp": "2026-04-05T14:30:00Z",
  "plugin": {
    "version": "0.0.0.0",
    "jar_sha256": "a1b2c3d4e5f6..."
  },
  "modules": [
    {
      "group": "org.http4k",
      "module": "http4k-core",
      "version": "0.0.0.0",
      "jar_sha256": "e3b0c44298fc1c14...",
      "signing_key_fingerprint": "sha256:2841234f0c9fa04c5efdf0e55f5f45afe37445f3689d831d7d2c440b2d15af60",
      "checks": {
        "jar": { "bundle": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-jar-sigstore.json", "verification": "passed" },
        "sbom": { "file": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-cyclonedx.json", "bundle": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-cyclonedx-sigstore.json", "verification": "passed" },
        "provenance": { "file": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-provenance.json", "bundle": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-provenance-sigstore.json", "verification": "passed" },
        "license": { "file": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-license-report.json", "bundle": "org.http4k/http4k-core/0.0.0.0/http4k-core-0.0.0.0-license-report-sigstore.json", "verification": "passed" }
      }
    }
  ]
}

```



The report includes:

- **`timestamp`** — When verification was performed
- **`plugin`** — Version and SHA-256 hash of the verify plugin JAR that performed the verification
- **`modules`** — For each http4k dependency:
    - GAV coordinates (group, module, version)
    - SHA-256 hash of the JAR
    - **`signing_key_fingerprint`** — Fingerprint of the key that signed this module's artifacts
    - Verification result for each artifact type (`passed`, `failed`, or `not_available`)
    - Relative paths to all exported artifact and bundle files

This report can be included in your compliance documentation, audit trails, or CI/CD pipeline artifacts as evidence that your http4k dependencies were validated.

### Clearing the Verification Cache

Verification results are cached locally so that subsequent builds have zero overhead. To force re-verification of all artifacts (e.g. after rotating the public key), clear the cache:

```shell
./gradlew clearHttp4kVerificationCache
```

### Key Rotation

The plugin supports key rotation via the [signing key list](https://http4k.org/.well-known/cosign-keys.json) — a JSON document listing all currently valid signing keys with their fingerprints and validity windows. Each artifact's provenance includes the fingerprint of the signing key, which the plugin uses to select the correct key for verification.

When http4k rotates signing keys, a new key is added to the key list and the old key is marked as `retired`. Artifacts signed with the old key continue to verify correctly because the old key remains in the list.

### Manual Verification with cosign

All verification artifacts can also be verified manually using [Cosign](https://docs.sigstore.dev/cosign/overview/). Download the http4k public key from the [signing key list](https://http4k.org/.well-known/cosign-keys.json), or use the `cosign.pub` file exported by the plugin.

#### Verify a JAR





```bash
cosign verify-blob http4k-core-0.0.0.0.jar \
    --key cosign.pub \
    --bundle http4k-core-0.0.0.0-jar-sigstore.json \
    --private-infrastructure

```



#### Verify an SBOM





```bash
cosign verify-blob http4k-core-0.0.0.0-cyclonedx.json \
    --key cosign.pub \
    --bundle http4k-core-0.0.0.0-cyclonedx-sigstore.json \
    --private-infrastructure

```



#### Verify Provenance





```bash
cosign verify-blob http4k-core-0.0.0.0-provenance.json \
    --key cosign.pub \
    --bundle http4k-core-0.0.0.0-provenance-sigstore.json \
    --private-infrastructure

```



The `--private-infrastructure` flag tells cosign to skip public transparency log verification, which is expected for privately distributed artifacts. All signatures include trusted timestamps from the Sigstore Timestamp Authority.

### Gradle Dependency Verification

Gradle also has built-in support for verifying dependency checksums without any extra tooling. To pin SHA-256 checksums for all dependencies (including http4k artifacts from **[maven.http4k.org](https://maven.http4k.org)**):





```bash
./gradlew --write-verification-metadata sha256,pgp

```



This generates a `gradle/verification-metadata.xml` file containing the expected checksums for every dependency:





```xml
<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata
    xmlns="https://schema.gradle.org/dependency-verification"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://schema.gradle.org/dependency-verification
        https://schema.gradle.org/dependency-verification/dependency-verification-1.3.xsd">
    <configuration>
        <verify-metadata>true</verify-metadata>
        <verify-signatures>true</verify-signatures>
    </configuration>
    <components>
        <component group="org.http4k" name="http4k-core" version="0.0.0.0">
            <artifact name="http4k-core-0.0.0.0.jar">
                <sha256 value="4396c4e8542e8180fc7d967c0d8ca3e4a1b800b74e1b92b0336b869b565c5fac"/>
            </artifact>
            <artifact name="http4k-core-0.0.0.0.pom">
                <sha256 value="a7f2b3c1d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1"/>
            </artifact>
        </component>
        <!-- checksums for all dependencies are generated automatically -->
    </components>
</verification-metadata>

```



Gradle will verify these checksums on every build, failing if any artifact has been tampered with. Commit `verification-metadata.xml` to your repository. When upgrading http4k versions, re-run the command to update the checksums.

This is also the recommended way to verify the http4k Verify plugin itself. The plugin records its own version and JAR hash in the verification report, but for maximum assurance, pinning the plugin's SHA-256 in `verification-metadata.xml` ensures Gradle verifies the plugin before it even loads.

