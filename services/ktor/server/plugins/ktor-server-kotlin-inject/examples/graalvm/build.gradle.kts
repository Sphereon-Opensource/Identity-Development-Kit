plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.io.ktor.plugin)
    id("org.graalvm.buildtools.native")
    application
}
metro {
    
}

group = "com.sphereon.example"
version = "1.0.0"

repositories {
    mavenCentral()

    // Sphereon Nexus for SNAPSHOT versions
    maven {
        url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/")
    }

    // Sonatype for other SNAPSHOT dependencies
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        url = uri("https://aws.oss.sonatype.org/content/repositories/snapshots/")
    }

    // Keep maven local at the end
    mavenLocal()
}

dependencies {
    // Ktor - using explicit versions since we can't access the version catalog from examples
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation(libs.bundles.app.platform.di)

    // IDK Core - using project dependencies
    implementation(project(":lib-core-api-public"))
    implementation(project(":lib-core-api-default"))

    // Ktor Kotlin-Inject Plugin
    implementation(project(":ktor-server-kotlin-inject"))

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

application {
    mainClass.set("com.sphereon.example.graalvm.ApplicationKt")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("com.sphereon.example.graalvm.ApplicationKt")

            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-build-time=kotlinx.coroutines,kotlin,org.slf4j,ch.qos.logback")
            buildArgs.add("--initialize-at-run-time=kotlin.uuid.SecureRandomHolder")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("-O3")
            buildArgs.add("--gc=serial")  // Use serial GC for GraalVM Community Edition
            buildArgs.add("--enable-http")
            buildArgs.add("--enable-https")
        }
    }
}
