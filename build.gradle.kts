plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.goddragon.teaseai"
version = "1.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(14))
    }
}

javafx {
    version = "17.0.14"
    modules = listOf(
        "javafx.controls",
        "javafx.fxml",
        "javafx.base",
        "javafx.media",
        "javafx.graphics",
        "javafx.swing",
        "javafx.web"
    )
}

// Pull the built TAJUpdater.jar into resources so it gets bundled at runtime
val copyUpdaterJar by tasks.registering(Copy::class) {
    dependsOn(":tajupdater:jar")
    from(project(":tajupdater").tasks.named<Jar>("jar"))
    into(layout.buildDirectory.dir("updaterJar"))
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("updaterJar"))
        }
    }
}

tasks.processResources {
    dependsOn(copyUpdaterJar)
}

application {
    mainClass.set("me.goddragon.teaseai.Main")
}

repositories {
    mavenCentral()
}

// The build machine is arm64 so the javafx plugin pulls mac-aarch64 natives by default.
// The runtime JDK (jdk-14.0.2) is x86_64 under Rosetta, so we need the mac (x86_64) natives instead.
// We pull them into a separate configuration and swap them in during shadow jar assembly.
val javafxMac by configurations.creating

val javafxVersion = "17.0.14"
val javafxModuleNames = listOf("base", "controls", "fxml", "graphics", "media", "swing", "web")

dependencies {
    javafxModuleNames.forEach { mod ->
        javafxMac("org.openjfx:javafx-$mod:$javafxVersion:mac")
    }
}

dependencies {
    // Apache Commons
    implementation("commons-collections:commons-collections:3.2.1")
    implementation("commons-configuration:commons-configuration:1.7")
    implementation("commons-lang:commons-lang:2.6")
    implementation("commons-logging:commons-logging:1.1.1")

    // Google
    implementation("com.google.code.gson:gson:2.6.2")
    implementation("com.google.guava:guava:22.0")

    // Apache HttpComponents
    implementation("org.apache.httpcomponents:httpclient:4.3.6")
    implementation("org.apache.httpcomponents:httpcore:4.3.3")
    implementation("org.apache.httpcomponents:httpmime:4.3.3")

    // JSON / HTML parsing
    implementation("org.json:json:20140107")
    implementation("org.jsoup:jsoup:1.8.1")

    // MaryTTS text-to-speech — kept as local files because their transitive deps
    // (fast-md5, jtok-core, Jampack) were on JCenter which is now shut down.
    // The fat jar bundles everything needed.
    implementation(files(
        "Resources/marytts-builder-5.2-jar-with-dependencies.jar",
        "Resources/marytts-lang-en-5.2.jar",
        "Resources/voice-dfki-prudence-hsmm-5.2.jar"
    ))

    // EstimAPI — unpublished SNAPSHOT project, no public Maven artifact
    implementation(files(
        "Resources/estimAPI.jar",
        "Resources/uber-EstimAPI-0.0.1-SNAPSHOT.jar"
    ))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("all")

    manifest {
        attributes("Main-Class" to "me.goddragon.teaseai.Main")
    }

    // Merge META-INF/services files so ServiceLoader-based discovery works
    // (MaryTTS registers its components this way across multiple jars)
    mergeServiceFiles()

    // Exclude aarch64 JavaFX jars at the dependency level — build machine is arm64
    // so the javafx plugin resolves mac-aarch64 artifacts, but runtime JDK is x86_64.
    dependencies {
        exclude(dependency("org.openjfx::"))
    }

    // Include x86_64 JavaFX natives from the separate javafxMac configuration
    from(javafxMac.filter { it.name.endsWith(".jar") }.map { zipTree(it) })
}
