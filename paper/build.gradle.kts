plugins {
    kotlin("jvm")
    id("dev.detekt")
}

/** The `:core` jar, unpacked into the plugin jar so the server needs no extra library. */
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(project(":core"))
    bundled(project(":core"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

detekt {
    baseline = rootProject.file("config/detekt/baseline.xml")
}

val fileVersion: String by rootProject.extra

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveFileName = "Metro-Paper-$fileVersion.jar"
        destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
        from(bundled.elements.map { files -> files.map { zipTree(it) } }) {
            exclude("META-INF/MANIFEST.MF")
        }
    }
}
