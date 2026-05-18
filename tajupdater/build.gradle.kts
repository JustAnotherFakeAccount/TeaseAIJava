plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(14))
    }
}

tasks.jar {
    archiveFileName.set("TAJUpdater.jar")
    manifest {
        attributes("Main-Class" to "Main")
    }
}
