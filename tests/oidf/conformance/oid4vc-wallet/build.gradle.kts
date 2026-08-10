/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Live OIDF wallet adapter. Keeping this outside wallet-runner's own test
 * source set prevents unrelated product tests from becoming prerequisites for
 * the independently sharded conformance lane.
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
    implementation(projects.walletRunner)
    implementation(projects.walletAppPublic)
    implementation(projects.libWalletPublic)
    implementation(projects.libWalletInteractionPublic)
    implementation(projects.libWalletInteractionProtocolOid4vci)
    implementation(projects.libWalletInteractionProtocolOid4vp)
    implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
    implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
    testImplementation(sphereonlib.org.junit.jupiter.junit.jupiter.engine)
    testImplementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
    testImplementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val evidenceDirectory =
        System.getProperty("oidf.evidence.dir")
            ?: providers.gradleProperty("oidf.evidence.dir").orNull
            ?: layout.buildDirectory.dir("oidf-evidence").get().asFile.absolutePath
    systemProperty("oidf.evidence.dir", evidenceDirectory)
    listOf(
        "oidf.suite.baseUrl",
        "oidf.suite.dir",
        "oidf.suite.mode",
        "oidf.plan",
        "oidf.scenario",
        "oidf.module",
        "oidf.sut.workspace",
        "javax.net.ssl.trustStore",
        "javax.net.ssl.trustStorePassword",
        "javax.net.ssl.trustStoreType",
    ).forEach { key ->
        val value = System.getProperty(key) ?: providers.gradleProperty(key).orNull
        if (!value.isNullOrBlank()) systemProperty(key, value)
    }
}
