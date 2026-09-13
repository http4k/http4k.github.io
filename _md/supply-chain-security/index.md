# Supply Chain Security


As part of the **http4k Enterprise Edition**, every http4k artifact is published with cryptographically signed provenance, complete dependency transparency, and verified build attestations - giving your organisation the supply chain assurance it needs to ship with confidence.

Security teams, customers and auditors increasingly ask for evidence that the third-party components in a build are the ones their authors published. The **EU Cyber Resilience Act** brings vulnerability reporting obligations from **11 September 2026**, and its full requirements - machine-readable SBOMs and due diligence over integrated components - from December 2027. **PCI DSS 4.0.1** and **NIST SSDF** (SP 800-218) push the same way. http4k delivers this out of the box - every build produces **SLSA Level 2 provenance** linking artifacts to the exact source commit and CI pipeline, **CycloneDX SBOMs** detailing all transitive dependencies, and **cosign signatures** with trusted timestamps from the Sigstore Timestamp Authority.

## What Gets Published, When, and Where

For every http4k module, the following provenance artifacts are published to **[maven.http4k.org](https://maven.http4k.org)** as classified Maven artifacts alongside the standard JARs and POMs:

<table class="table" style="max-width: 90%">
<thead><tr><th>Artifact</th><th>Description</th></tr></thead>
<tbody>
<tr><td><strong>CycloneDX SBOM</strong></td><td>Machine-readable bill of materials listing all transitive dependencies</td></tr>
<tr><td><strong>JAR signature</strong></td><td>Cosign signature bundle for the compiled JAR</td></tr>
<tr><td><strong>License report</strong></td><td>Curated license compliance report for all dependencies</td></tr>
<tr><td><strong>SLSA provenance</strong></td><td>Build L2 provenance attestation linking artifact to source commit and CI pipeline</td></tr>
<tr><td><strong>Cosign bundles</strong></td><td>Sigstore signature bundle for each of the above, with trusted timestamps</td></tr>
</tbody>
</table>

Artifacts published to Maven Central carry PGP signatures. See [Distribution & release channels](/distribution/) for how the two channels differ.

See the [Enterprise Repository reference](/ecosystem/enterprise/reference/repository/) for Maven coordinates, repository layout, and setup guides.

## License Compliance

Every http4k module includes a **signed license compliance report** listing the licenses of all transitive dependencies, checked against http4k's curated approved license list. This gives your legal and compliance teams immediate visibility into the licensing of every component in your dependency tree.

Each report is signed with the same cosign key as all other provenance artifacts, and covers the specific module's transitive dependencies - so you get exactly the license information relevant to the http4k modules you actually use.

## SLSA Provenance Format

Each provenance attestation follows the [in-toto Statement v1](https://in-toto.io/Statement/v1) specification with a [SLSA Provenance v1](https://slsa.dev/provenance/v1) predicate. The attestation links each artifact to:

- The **exact git commit** that produced it
- The **GitHub Actions workflow** that built it
- The **build invocation ID** for full traceability
- **SHA-256 digests** of all subject artifacts

## Build Isolation

Provenance signing runs in a dedicated CI job, separate from the job that compiles the code. The build job holds no signing keys; a separate signing job - running no build logic - signs the artifacts and publishes them. This defence-in-depth reduces the risk of a compromised build tampering with signatures or exfiltrating signing material.

## SLSA Level 3 Provenance

All http4k Enterprise Edition artifacts are published with **SLSA Build Level 2** provenance as standard, generated on hosted GitHub Actions infrastructure with signing isolated from the build (see Build Isolation, above).

**SLSA Level 3** raises the bar further, requiring *non-falsifiable* provenance produced by a build platform whose signing identity is unreachable by the build itself. For organisations with enhanced compliance requirements, we offer L3 provenance as a **bespoke engagement** on dedicated, hardened build infrastructure.

To discuss SLSA Level 3 provenance for your organisation, [contact us](mailto:enterprise@http4k.org).

## Verifying Artifacts

The **[http4k Verify](/ecosystem/enterprise/reference/verify/)** plugin provides automatic build-time verification of all http4k artifact signatures. It exports all verification artifacts (SBOMs, provenance, license reports, sigstore bundles) to your project directory for independent inspection, and generates a JSON attestation report as proof that your http4k dependencies were validated.

See the [http4k Verify reference documentation](/ecosystem/enterprise/reference/verify/) for full setup, configuration, and usage details - including manual verification with cosign and Gradle's built-in dependency verification.



