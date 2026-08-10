/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.testsOidfConformanceOid4vc)
    implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(sphereonlib.org.junit.jupiter.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // The suite permits one active module per alias. Every scenario gets a unique alias, then its
    // modules run sequentially while independent scenario plans execute concurrently, matching
    // the EUDIPLO plan lifecycle without its module skips. Ordinary tests remain sequential;
    // twelve is only an upper bound on scenario chains, not module-level fan-out.
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty(
        "junit.jupiter.execution.parallel.config.fixed.parallelism",
        providers.gradleProperty("oidf.parallelism").orElse("12").get(),
    )
    systemProperty("oidf.sut.workspace", rootProject.layout.projectDirectory.asFile.absolutePath)
    val evidenceDirectory =
        System.getProperty("oidf.evidence.dir")
            ?: providers.gradleProperty("oidf.evidence.dir").orNull
            ?: layout.buildDirectory.dir("oidf-evidence").get().asFile.absolutePath
    systemProperty("oidf.evidence.dir", evidenceDirectory)
    listOf(
        "oidf.suite.mode",
        "oidf.plan",
        "oidf.scenario",
        "oidf.module",
        "oidf.sut.infraWorkspace",
        "oidf.sut.composeRuntime",
        "oidf.sut.suiteContext",
        "oidf.sut.enterpriseContext",
        "oidf.sut.credentials",
        "oidf.sut.tlsCaCert",
    ).forEach { key ->
        val value = System.getProperty(key) ?: providers.gradleProperty(key).orNull
        if (!value.isNullOrBlank()) systemProperty(key, value)
    }
}
