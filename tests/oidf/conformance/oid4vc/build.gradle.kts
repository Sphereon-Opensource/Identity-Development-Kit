/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Shared JVM infrastructure for driving the OpenID Foundation OID4VCI,
 * OID4VP, and HAIP 1.0 conformance plans. Product-specific adapters live in
 * their owning modules; this module owns suite lifecycle, API access, the
 * required plan matrix, and evidence export.
 */

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
}

// Consumers (wallet-runner jvmTest) resolve on the conventions' JVM 17 toolchain; without this
// pin the plain kotlin-jvm plugin publishes JVM-21 variants and dependency resolution fails.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
    implementation(sphereonlib.org.testcontainers.testcontainers)

    testImplementation(kotlin("test"))
    testImplementation(sphereonlib.org.junit.jupiter.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val evidenceDirectory =
        System.getProperty("oidf.evidence.dir")
            ?: providers.gradleProperty("oidf.evidence.dir").orNull
            ?: layout.buildDirectory.dir("oidf-evidence").get().asFile.absolutePath
    systemProperty("oidf.evidence.dir", evidenceDirectory)
    listOf("oidf.suite.mode").forEach { key ->
        val value = System.getProperty(key) ?: providers.gradleProperty(key).orNull
        if (!value.isNullOrBlank()) systemProperty(key, value)
    }
}

tasks.processResources {
    from(layout.projectDirectory.file("suite/suite.lock.properties"))
}

val buildOidfSuiteImages by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds the pinned, patched OIDF conformance server and nginx images."
    workingDir(layout.projectDirectory.dir("suite"))
    commandLine("docker", "buildx", "bake", "--load")
}
