import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

/**
 * The version from the latest `v*` tag: the tag itself on a tagged commit, else `<tag>-dev.<commits since>+<hash>`
 * (semver). Without tags or git it is `0.0.0-dev`.
 */
fun gitVersion(): String {
    val describe =
        providers
            .exec {
                commandLine("git", "describe", "--tags", "--long", "--match", "v[0-9]*")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    val parts = Regex("""v(.+)-(\d+)-g([0-9a-f]+)""").matchEntire(describe)?.destructured ?: return "0.0.0-dev"
    val (tag, commits, hash) = parts
    return if (commits == "0") tag else "$tag-dev.$commits+$hash"
}

val metroVersion = gitVersion()

/** The version for file names: `+` is awkward in URLs and release asset names. */
val fileVersion by extra(metroVersion.replace('+', '-'))

subprojects {
    group = "net.craftgame"
    version = metroVersion

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        // The server provides the stdlib at runtime (plugin.yml `libraries`).
        "compileOnly"(kotlin("stdlib"))

        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.11.4")
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }

    configure<KtlintExtension> {
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
