import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)

                // VCDM Data Integrity verification contract (proof model + purpose).
                api(projects.libCryptoDataIntegrityProofPublic)

                // OAuth2 common and client (for base AuthorizationRequest/Response models and JAR support)
                api(projects.libOauth2CommonPublic)
                api(projects.libOauth2ClientPublic)

                // OID4VP common (for type-safe extensions)
                api(projects.libOpenidOid4vpCommonPublic)

                // DCQL (for query creation)
                api(projects.libOpenidOid4vpDcql)

                // Credential status policy (CredentialStatusPolicy on the create-request args + session)
                api(projects.libStatuslistPublic)

                // ISO/IEC 18013-5 second-edition ZKP request/provider contracts used by the
                // additive verifier-binding arguments. Concrete verification remains in impl.
                api(projects.libMdocCorePublic)

                // DCQL query configuration store (DcqlQueryConfigurationStore is exposed on Oid4vpVerifierService)
                api(projects.libOpenidOid4vpDcqlStorePublic)

                // Serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // Validation (konform)
                api(sphereonlib.io.konform.konform)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
