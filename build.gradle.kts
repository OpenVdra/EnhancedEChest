plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

val shade: Configuration by configurations.creating
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Shaded and relocated — no server-side drivers required
    shade("com.zaxxer:HikariCP:7.1.0")
    shade("org.mariadb.jdbc:mariadb-java-client:3.5.3")   // compatible with MySQL 5.7+ and 8.x

    // Paper bundles sqlite-jdbc on the server classpath; compileOnly is sufficient
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:-deprecation"))
}

// Plain jar is not the deliverable; use shadowJar instead
tasks.jar {
    archiveBaseName.set("EnhancedEChestPlain")
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("EnhancedEChest")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    configurations = listOf(shade)

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    exclude("org/slf4j/**")

    relocate("com.zaxxer.hikari", "com.enhancedechest.libs.hikari")
    relocate("org.mariadb.jdbc", "com.enhancedechest.libs.mariadb")

    mergeServiceFiles()
    // destinationDirectory.set(file("C:\\Users\\Admin\\Desktop\\TestServer\\plugins"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}
