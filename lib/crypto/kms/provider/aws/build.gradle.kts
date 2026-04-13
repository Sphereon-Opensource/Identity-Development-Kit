import com.codingfeline.buildkonfig.compiler.FieldSpec

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.codingfeline.buildkonfig)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.integration.tests)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
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
                // default deps are already injected by conventions plugin!
                implementation(projects.libCborPublic)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)
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
                implementation(projects.libCryptoCoreImpl)
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
            }
        }
    }
}

buildkonfig {
    packageName = "com.sphereon.crypto.kms.aws"
    defaultConfigs {
        buildConfigField(
            FieldSpec.Type.STRING,
            "AWS_REGION",
            System.getenv("AWS_REGION"),
            nullable = true,
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "AWS_ACCESS_KEY_ID",
            System.getenv("AWS_ACCESS_KEY_ID"),
            nullable = true,
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "AWS_SECRET_ACCESS_KEY",
            System.getenv("AWS_SECRET_ACCESS_KEY"),
            nullable = true,
        )
    }
}
