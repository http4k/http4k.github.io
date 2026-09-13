# Enterprise Maven


All http4k Enterprise Edition artifacts are available through **[maven.http4k.org](https://maven.http4k.org)**. This includes community (`org.http4k`) and pro (`org.http4k.pro`) modules, each published with cryptographically signed provenance, CycloneDX SBOMs, and license compliance reports.

Access credentials are included with your [http4k Enterprise Edition](/enterprise/) subscription.

### Gradle Configuration





```kotlin
repositories {
    maven {
        url = uri("https://maven.http4k.org")
        credentials {
            username = project.findProperty("http4kMavenUser") as String
            password = project.findProperty("http4kMavenPassword") as String
        }
    }
}

```



Add your credentials to `~/.gradle/gradle.properties`:





```bash
http4kMavenUser=<your-username>
http4kMavenPassword=<your-password>

```



### Maven Configuration

Add the repository to your `pom.xml`:





```xml
<repositories>
    <repository>
        <id>http4k</id>
        <url>https://maven.http4k.org</url>
    </repository>
</repositories>

```



And credentials to `~/.m2/settings.xml`:





```xml
<servers>
    <server>
        <id>http4k</id>
        <username>your-username</username>
        <password>your-password</password>
    </server>
</servers>

```



### Artifactory Configuration

If your organisation uses Artifactory as a repository manager, add **[maven.http4k.org](https://maven.http4k.org)** as a remote repository:

1. Navigate to **Administration > Repositories > Remote**
2. Click **New Remote Repository** and select **Maven**
3. Set the **URL** to `https://maven.http4k.org`
4. Under **Advanced**, configure **Username** and **Password** with your http4k credentials
5. Save and add the remote repository to your virtual repository resolution order

### Nexus Configuration

For Sonatype Nexus, add a proxy repository:

1. Navigate to **Repository > Repositories > Create repository**
2. Select **maven2 (proxy)**
3. Set the **Remote storage URL** to `https://maven.http4k.org`
4. Under **HTTP > Authentication**, enter your http4k credentials
5. Add the proxy repository to your maven-public group


