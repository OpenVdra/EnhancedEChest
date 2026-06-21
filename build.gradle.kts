plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val shade: Configuration by configurations.creating
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.0.1"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")

    // Shaded and relocated — no server-side drivers required
    shade("com.zaxxer:HikariCP:7.1.0")
    shade("org.mariadb.jdbc:mariadb-java-client:3.5.9")   // compatible with MySQL 5.7+ and 8.x
    shade("org.postgresql:postgresql:42.7.11")
    shade("com.tcoded:FoliaLib:0.5.2")

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
    archiveBaseName.set("EnhancedEchestPlain")
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("EnhancedEchest")
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
    relocate("org.postgresql",   "com.enhancedechest.libs.postgresql")
    relocate("com.ongres",       "com.enhancedechest.libs.ongres")
    relocate("com.tcoded.folialib", "com.enhancedechest.libs.folialib")

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

tasks.runServer {
    minecraftVersion("26.1.2")
}