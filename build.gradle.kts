plugins {
    java
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val shade: Configuration = configurations.create("shade")
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.0.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    // Folia library repository
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Shaded and relocated — no server-side drivers required
    shade("com.zaxxer:HikariCP:7.1.0")
    shade("org.mariadb.jdbc:mariadb-java-client:3.5.9")   // compatible with MySQL 5.7+ and 8.x
    shade("org.postgresql:postgresql:42.7.11")
    // Read-only driver for migrating from H2-backed AxVaults installs (matches AxVaults' H2 version).
    shade("com.h2database:h2:2.4.240")
    shade("com.tcoded:FoliaLib:0.5.2")
    shade("org.bstats:bstats-bukkit:3.2.1")

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
    relocate("org.h2",           "com.enhancedechest.libs.h2")
    relocate("com.ongres",       "com.enhancedechest.libs.ongres")
    relocate("com.tcoded.folialib", "com.enhancedechest.libs.folialib")
    relocate("org.bstats",          "com.enhancedechest.libs.bstats")

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
//    downloadPlugins {
//        modrinth("luckperms", "v5.5.53-bukkit")
//        modrinth("axvaults", "2.15.0")
//    }
    minecraftVersion("1.21.11")
}