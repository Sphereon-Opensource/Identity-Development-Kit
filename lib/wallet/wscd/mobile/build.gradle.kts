import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

// LocalNativeWscd wraps the mobile KMS provider (Android StrongBox / iOS Secure Enclave via
// signum-supreme): unlike the software/public WSCD modules, js/wasm/linux targets do not apply
// here - lib-crypto-kms-provider-mobile (this module's provider dependency) itself only builds
// jvm/android/ios, so this module mirrors that target set exactly rather than the generic
// kmp.targets-gated js/wasm template used by wscd/public and wscd/software.
kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    androidLibrary {
        namespace = "com.sphereon.wallet.wscd.mobile"
        compileSdk = 35
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libWalletUnitPublic)
                api(projects.libWalletWscdPublic)
                // Same hardening as wscd/software's libCryptoKmsProviderSoftware
                // dependency: implementation, not api -
                // provider types (MobileKmsProviderImpl, MobileKmsProviderConfig) are used only
                // internally (LocalNativeWscdFactory/LocalNativeWscd) and must not leak onto this
                // module's consumers' compile classpath. Consumers reach custody exclusively through
                // Wscd.
                implementation(projects.libCryptoKmsProviderMobile)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                // LocalNativeWscdTest/LocalNativeWscdFactoryTest build a minimal session graph (via
                // staticMinimalTestAppGraph) to obtain a real SessionExecution for MobileKmsProviderImpl -
                // JVM-only placement (mirrors SoftwareWscdTest's own jvmTest placement in the sibling
                // wscd/software module) sidesteps lib-core-test lacking an android target, since that
                // module is not needed by any commonMain/commonTest code here.
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)
            }
        }
    }
}
