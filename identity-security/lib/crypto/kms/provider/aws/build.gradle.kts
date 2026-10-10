plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.integration.tests)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}
metro {
}

kotlin {

    kotlin.applyDefaultHierarchyTemplate()
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }

//        createIntegrationTest(includeCommonTest = true, includePlatformTest = true)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-compat-annotations:$version" else project(":lib-core-compat-annotations"))
                // default deps are already injected by conventions plugin!
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-cbor-public:$version" else project(":lib-cbor-public"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core:$version" else project(":lib-crypto-core"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-public:$version" else project(":lib-crypto-core-public"))
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(sphereonlib.co.touchlab.kermit)
                implementation(project.dependencies.platform(awssdk.bom))
                implementation(awssdk.services.kms)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                // implementation(sphereonlib.org.slf4j.simple) clashes with Springboot Logback
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-test:$version" else project(":lib-core-test"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-key-persistence-impl:$version" else project(":lib-crypto-key-persistence-impl"))
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
            }
        }
    }
}
