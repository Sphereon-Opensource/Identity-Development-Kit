rootProject.name = "Identity-Development-Kit"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Helper function to include a project with its name as the path while pointing to the actual directory
fun includeProject(name: String, path: String) {
    include(":$name")
    project(":$name").projectDir = file(path)
}

pluginManagement {
    if (file("gradle-build-support/settings.gradle.kts").exists()) {
        includeBuild("gradle-build-support/plugins/toml-catalog")
        includeBuild("gradle-build-support")
    }

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://aws.oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
            content { includeGroupAndSubgroups("software.amazon") }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/")
            mavenContent { snapshotsOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases/")
            mavenContent { releasesOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupAndSubgroups("org.jetbrains.compose")
                includeGroupAndSubgroups("org.jetbrains.kotlin")
                includeGroupAndSubgroups("org.jetbrains.kotlinx")
                includeGroupAndSubgroups("org.jetbrains.skiko")
            }
        }

        // Keep maven local at the end!!!!
        // https://slack-chats.kotlinlang.org/t/27045384/hi-there-i-have-a-very-annoying-internal-compiler-error-here
        mavenLocal {
            content {
                includeGroupAndSubgroups("com.sphereon")

            }
        }
    }
    plugins {
        id("app.cash.sqldelight") version "2.2.1"
        id("com.sphereon.gradle.toml-catalog") version settings.extra["gbsVersion"] as String
    }
}


// Workaround: Kotlin 2.3.x npm-publish plugin registers assembleWasmJsPackage with a broken
// mainFile provider. The NpmPublicationPlugin sets a dummy value, but task graph construction
// can still fail. Exclude the broken tasks when wasmJs is active (the workaround handles publish).
run {
    val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
    val hasWasm = "all" in kmpTargets || "wasmjs" in kmpTargets || "wasm" in kmpTargets
    if (hasWasm) {
        gradle.startParameter.excludedTaskNames.addAll(
            listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry")
        )
    }
}

// ===========================================
// Composite Build Configuration for gradle-build-support
// ===========================================

/**
 * Determines if gradle-build-support composite build should be enabled.
 * Priority:
 * 1. Environment variable USE_LOCAL_GRADLE_BUILD_SUPPORT (explicit control)
 * 2. Auto-detect: enabled if gradle-build-support/ directory exists with settings.gradle.kts
 */
fun isGradleBuildSupportCompositeBuildEnabled(): Boolean {
    System.getenv("USE_LOCAL_GRADLE_BUILD_SUPPORT")?.let { value ->
        if (value.equals("true", ignoreCase = true) || value == "1") return true
        if (value.equals("false", ignoreCase = true) || value == "0") return false
    }
    val gbsDir = file("gradle-build-support")
    val gbsSettingsFile = file("gradle-build-support/settings.gradle.kts")
    return gbsDir.exists() && gbsDir.isDirectory && gbsSettingsFile.exists()
}

/**
 * Parses a settings.gradle.kts file and extracts module info from include() calls.
 * Matches patterns like: include(":versions:common-bom")
 * Returns pairs of (artifactName, projectPath) e.g. ("common-bom", ":versions:common-bom")
 * Skips commented lines.
 */
fun extractGbsModulesFromSettings(settingsFile: File): List<Pair<String, String>> {
    if (!settingsFile.exists()) return emptyList()
    return settingsFile.readLines()
        .filter { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")
        }
        .mapNotNull { line ->
            val regex = Regex("""include\s*\(\s*"(:[^"]+)"\s*\)""")
            regex.find(line)?.let { match ->
                val projectPath = match.groupValues[1]
                val artifactName = projectPath.substringAfterLast(":")
                Pair(artifactName, projectPath)
            }
        }
}

val gbsVersion: String by settings
val useGbsCompositeBuild = isGradleBuildSupportCompositeBuildEnabled()

if (useGbsCompositeBuild) {
    val gbsSettingsFile = file("gradle-build-support/settings.gradle.kts")
    val gbsModules = extractGbsModulesFromSettings(gbsSettingsFile)

    includeBuild("gradle-build-support") {
        name = "gradle-build-support"
        dependencySubstitution {
            gbsModules.forEach { (artifactName, projectPath) ->
                substitute(module("com.sphereon.gradle:$artifactName")).using(project(projectPath))
            }
        }
    }
    println("==> Gradle Build Support Composite Build: ENABLED (${gbsModules.size} modules)")
} else {
    println("==> Gradle Build Support Composite Build: DISABLED (using Maven dependencies)")
}

gradle.settingsEvaluated {
    gradle.allprojects {
        buildscript.configurations.all {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
                    useVersion("1.27.1")
                    because("Spring Boot 3.5.6 requires commons-compress 1.27.1")
                }
            }
        }
    }
}


plugins {
    id("com.gradle.develocity") version ("4.0.2")
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.9.0")
}


dependencyResolutionManagement {
    versionCatalogs {
        // When gradle-build-support submodule is available with generated TOML catalogs,
        // use local files directly — no publishToMavenLocal needed.
        // Generate with: cd gradle-build-support && ./gradlew generateTomlCatalog
        val gbsTomlDir = file("gradle-build-support/versions")
        // Use non-versioned plugin TOML in composite build mode: Sphereon plugins resolve from
        // includeBuild without version, third-party plugins keep their versions.
        val plugBomToml = gbsTomlDir.resolve("gradle-plugin-bom/build/tomlCatalog/sphereonGradlePluginBom.toml")
        val libBomToml = gbsTomlDir.resolve("library-bom/build/tomlCatalog/sphereonLibraryBom.versioned.toml")

        create("sphereonplug") {
            if (useGbsCompositeBuild && plugBomToml.exists()) {
                from(files(plugBomToml))
            } else {
                from("com.sphereon.gradle:gradle-plugin-bom:$gbsVersion@toml" as String)
            }
        }
        create("sphereonlib") {
            if (useGbsCompositeBuild && libBomToml.exists()) {
                from(files(libBomToml))
            } else {
                from("com.sphereon.gradle:library-bom:$gbsVersion@toml" as String)
            }
        }
        // TODO: Move aws sdk to our bom
        create("awssdk") {
            from("aws.sdk.kotlin:version-catalog:1.4.31" as String)
        }

    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://aws.oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
            content { includeGroupAndSubgroups("software.amazon") }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots")
            mavenContent { snapshotsOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases")
            mavenContent { releasesOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupAndSubgroups("org.jetbrains.compose")
                includeGroupAndSubgroups("org.jetbrains.kotlin")
                includeGroupAndSubgroups("org.jetbrains.kotlinx")
                includeGroupAndSubgroups("org.jetbrains.skiko")
            }
        }

        // Keep maven local at the end!!!!
        // https://slack-chats.kotlinlang.org/t/27045384/hi-there-i-have-a-very-annoying-internal-compiler-error-here
        mavenLocal {
            content {
                includeGroupAndSubgroups("com.sphereon")

            }
        }
    }

}



develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
    }
}

// Core libraries
includeProject("lib-cbor-public", "lib/cbor/public")
includeProject("lib-cbor-impl", "lib/cbor/impl")
includeProject("lib-core-api-public", "lib/core/api/public")
includeProject("lib-core-api-default", "lib/core/api/default")
includeProject("lib-core-benchmarks", "lib/core/benchmarks")
includeProject("lib-conf-settings", "lib/conf/settings")
includeProject("lib-conf-yaml", "lib/conf/yaml")

// Theme
includeProject("lib-conf-theme-core-public", "lib/conf/theme/core/public")
includeProject("lib-conf-theme-core-impl", "lib/conf/theme/core/impl")
includeProject("lib-conf-theme-compose", "lib/conf/theme/compose")
includeProject("lib-conf-theme-web", "lib/conf/theme/web")
// UI Components
includeProject("lib-ui-compose", "lib/ui/compose")
includeProject("lib-core-test", "lib/core/test")
includeProject("lib-data-link-http-client-public", "lib/data/link/http/client/public")
includeProject("lib-data-link-http-client-impl", "lib/data/link/http/client/impl")
includeProject("lib-data-link-http-client", "lib/data/link/http/client")
includeProject("lib-core-loggers-mobile-logger", "lib/core/loggers/mobile-logger")

// Core Events
includeProject("lib-core-events-public", "lib/core/events/public")
includeProject("lib-core-events-impl", "lib/core/events/impl")

// Core IDN (RFC 3492 Punycode + IDNA2008)
includeProject("lib-core-idn-public", "lib/core/idn/public")

// Crypto libraries
includeProject("lib-crypto-core-public", "lib/crypto/core/public")
includeProject("lib-crypto-core-impl", "lib/crypto/core/impl")
includeProject("lib-crypto-core", "lib/crypto/core")
includeProject("lib-crypto-kms-provider-software", "lib/crypto/kms/provider/software")
includeProject("lib-crypto-kms-provider-aws", "lib/crypto/kms/provider/aws")
includeProject("lib-crypto-kms-provider-azure", "lib/crypto/kms/provider/azure")
includeProject("lib-crypto-kms-provider-mobile", "lib/crypto/kms/provider/mobile")
includeProject("lib-crypto-kms-rest-api", "lib/crypto/kms/rest/api")
includeProject("lib-crypto-kms-provider-rest", "lib/crypto/kms/provider/rest")
// KMS REST server moved to services/kms/rest/ — see Services section below

// Crypto key persistence (tenant-aware key reference store)
includeProject("lib-crypto-key-persistence-api", "lib/crypto/key/persistence/api")
includeProject("lib-crypto-key-persistence-impl", "lib/crypto/key/persistence/impl")
includeProject("lib-crypto-key-persistence-sqlite", "lib/crypto/key/persistence/sqlite")

// W3C Verifiable Credentials Data Integrity 1.0
includeProject("lib-crypto-data-integrity-proof-public", "lib/crypto/data-integrity-proof/public")
includeProject("lib-crypto-data-integrity-proof-impl", "lib/crypto/data-integrity-proof/impl")
includeProject("lib-crypto-data-integrity-proof-eddsa-jcs-2022", "lib/crypto/data-integrity-proof/eddsa-jcs-2022")

// JSON-LD 1.1 capability (Track A: loader + validators; Track B: full processor)
includeProject("lib-jsonld-public", "lib/jsonld/public")
includeProject("lib-jsonld-loader", "lib/jsonld/loader")

// SD-JWT libraries
includeProject("lib-sdjwt-public", "lib/sdjwt/public")
includeProject("lib-sdjwt-impl", "lib/sdjwt/impl")

// OAuth2 Common (shared models)
includeProject("lib-oauth2-common-public", "lib/oauth2/common/public")
includeProject("lib-oauth2-common-impl", "lib/oauth2/common/impl")

// OAuth2 Client
includeProject("lib-oauth2-client-public", "lib/oauth2/client/public")
includeProject("lib-oauth2-client-impl", "lib/oauth2/client/impl")

// OAuth2 Authorization Server
includeProject("lib-oauth2-server-authorization-public", "lib/oauth2/server/authorization/public")
includeProject("lib-oauth2-server-authorization-impl", "lib/oauth2/server/authorization/impl")
includeProject("lib-oauth2-server-resource-public", "lib/oauth2/server/resource/public")
includeProject("lib-oauth2-server-resource-impl", "lib/oauth2/server/resource/impl")

// OpenID OID4VC (shared VC-family types)
includeProject("lib-openid-oid4vc-common-public", "lib/openid/oid4vc/common/public")
includeProject("lib-openid-oid4vc-common-impl", "lib/openid/oid4vc/common/impl")

// OpenID OID4VCI
includeProject("lib-openid-oid4vci-common-public", "lib/openid/oid4vci/common/public")
includeProject("lib-openid-oid4vci-common-impl", "lib/openid/oid4vci/common/impl")
includeProject("lib-openid-oid4vci-issuer-public", "lib/openid/oid4vci/issuer/public")
includeProject("lib-openid-oid4vci-issuer-impl", "lib/openid/oid4vci/issuer/impl")
includeProject("lib-openid-oid4vci-holder-public", "lib/openid/oid4vci/holder/public")
includeProject("lib-openid-oid4vci-holder-impl", "lib/openid/oid4vci/holder/impl")
includeProject("lib-openid-oid4vci-rest-public", "lib/openid/oid4vci/rest/public")
includeProject("lib-openid-oid4vci-rest-impl", "lib/openid/oid4vci/rest/impl")

// OpenID OID4VP
includeProject("lib-openid-oid4vp-dcql", "lib/openid/oid4vp/dcql")
includeProject("lib-openid-oid4vp-common-public", "lib/openid/oid4vp/common/public")
includeProject("lib-openid-oid4vp-common-impl", "lib/openid/oid4vp/common/impl")
includeProject("lib-openid-oid4vp-holder-public", "lib/openid/oid4vp/holder/public")
includeProject("lib-openid-oid4vp-holder-impl", "lib/openid/oid4vp/holder/impl")
includeProject("lib-openid-oid4vp-verifier-public", "lib/openid/oid4vp/verifier/public")
includeProject("lib-openid-oid4vp-verifier-impl", "lib/openid/oid4vp/verifier/impl")
includeProject("lib-openid-oid4vp-universal-public", "lib/openid/oid4vp/universal/public")
includeProject("lib-openid-oid4vp-universal-impl", "lib/openid/oid4vp/universal/impl")

// Data Link - BLE
includeProject("lib-data-link-ble-public", "lib/data/link/ble/public")
includeProject("lib-data-link-ble-test-fixtures", "lib/data/link/ble/test-fixtures")
includeProject("lib-data-link-ble-robots", "lib/data/link/ble/robots")

// Data Link - NFC
includeProject("lib-data-link-nfc-impl", "lib/data/link/nfc/impl")
includeProject("lib-data-link-nfc-public", "lib/data/link/nfc/public")

// Data Store (cross-cutting storage abstractions)
includeProject("lib-data-store-kv-public", "lib/data/store/kv/public")
includeProject("lib-data-store-kv-impl", "lib/data/store/kv/impl")
includeProject("lib-data-store-kv-impl-memory", "lib/data/store/kv/impl-memory")
includeProject("lib-data-store-kv-impl-kottage", "lib/data/store/kv/impl-kottage")

// Data Store - Blob (cross-cutting blob/object storage abstraction)
includeProject("lib-data-store-blob-public", "lib/data/store/blob/public")
includeProject("lib-data-store-blob-impl", "lib/data/store/blob/impl")

// Attribute Flow (flow-agnostic attribute wiring primitives: AttributeBag, AttributePath,
// AttributeSource/Target/Binding. Consumed by IDV graphs, issuance pipelines, tabular sources, etc.)
includeProject("lib-attribute-flow-public", "lib/attribute/flow/public")

// Attribute Mapping (generic source -> target attribute rename rules + applier; reused by
// reconciliation flows, CSV-roster issuance, OIDC claim projection, etc.)
includeProject("lib-attribute-mapping-public", "lib/attribute/mapping/public")

// Invitation service — RELOCATED to VDX as vdx-service-invitation-* per
// feedback_edk_vs_vdx_placement (invitation orchestration is a product feature,
// not an open-source primitive).
includeProject("lib-data-store-blob-impl-memory", "lib/data/store/blob/impl-memory")
includeProject("lib-data-store-blob-impl-fs", "lib/data/store/blob/impl-fs")
includeProject("lib-data-store-blob-impl-kv", "lib/data/store/blob/impl-kv")
includeProject("lib-data-store-blob-client-http", "lib/data/store/blob/client-http")

// Data Store - OKD (Onderwijs Koppeling voor Document Management — Dutch MBO education standard)
includeProject("lib-data-store-okd-openapi", "lib/data/store/okd-openapi")
includeProject("lib-data-store-blob-impl-okd", "lib/data/store/blob/impl-okd")
includeProject("lib-data-store-okd-server", "lib/data/store/okd-server")

// Data Store - Schema Registry (schema management with blob store backing)
includeProject("lib-data-store-schema-registry-public", "lib/data/store/schema-registry/public")
includeProject("lib-data-store-schema-registry-impl", "lib/data/store/schema-registry/impl")

// Data Store - Credential Design (design, localization, and render metadata)
includeProject("lib-data-store-credential-design-public", "lib/data/store/credential-design/public")
includeProject("lib-data-store-credential-design-impl", "lib/data/store/credential-design/impl")

// Data Store - Party (data models for identity, contact, tenant)
includeProject("lib-data-store-party-public", "lib/data/store/party/public")

// DID libraries (W3C Decentralized Identifiers)
includeProject("lib-did-core-public", "lib/did/core/public")
includeProject("lib-did-resolver-public", "lib/did/resolver/public")
includeProject("lib-did-resolver-impl", "lib/did/resolver/impl")
includeProject("lib-did-manager-public", "lib/did/manager/public")
includeProject("lib-did-manager-impl", "lib/did/manager/impl")
includeProject("lib-did-methods-key", "lib/did/methods/key")
includeProject("lib-did-methods-jwk", "lib/did/methods/jwk")
includeProject("lib-did-methods-web", "lib/did/methods/web")
includeProject("lib-did-methods-webvh-public", "lib/did/methods/webvh/public")
includeProject("lib-did-methods-webvh-resolver", "lib/did/methods/webvh/resolver")
includeProject("lib-did-methods-webvh-provider", "lib/did/methods/webvh/provider")
includeProject("lib-did-methods-webvh-rest-server", "lib/did/methods/webvh/rest/server")
includeProject("lib-did-persistence-api", "lib/did/persistence/api")
includeProject("lib-did-persistence-memory", "lib/did/persistence/memory")
includeProject("lib-did-persistence-sqlite", "lib/did/persistence/sqlite")
includeProject("lib-did-rest-resolver-server", "lib/did/rest/resolver/server")

// mDoc libraries
includeProject("lib-mdoc-core-public", "lib/mdoc/core/public")
includeProject("lib-mdoc-core-impl", "lib/mdoc/core/impl")
includeProject("lib-mdoc-core", "lib/mdoc/core")
includeProject("lib-mdoc-transport-ble-public", "lib/mdoc/transport-ble/public")
includeProject("lib-mdoc-transport-ble-impl", "lib/mdoc/transport-ble/impl")
includeProject("lib-mdoc-transport-ble", "lib/mdoc/transport-ble")
includeProject("lib-mdoc-transport-nfc", "lib/mdoc/transport-nfc")
includeProject("lib-mdoc-transport-restapi", "lib/mdoc/transport-restapi")
includeProject("lib-mdoc-transport-oid4vp", "lib/mdoc/transport-oid4vp")
includeProject("lib-mdoc-datatransfer-public", "lib/mdoc/datatransfer/public")
includeProject("lib-mdoc-datatransfer-impl", "lib/mdoc/datatransfer/impl")
includeProject("lib-mdoc-datatransfer", "lib/mdoc/datatransfer")
includeProject("lib-mdoc-reader", "lib/mdoc/reader")



// Trust libraries
includeProject("lib-trust-core-public", "lib/trust/core/public")
includeProject("lib-trust-core-impl", "lib/trust/core/impl")
includeProject("lib-trust-etsi-entities-public", "lib/trust/etsi-entities-public")
includeProject("lib-trust-etsi", "lib/trust/etsi")
includeProject("lib-trust-x509", "lib/trust/x509")
includeProject("lib-trust-did", "lib/trust/did")
includeProject("lib-trust-oidfed", "lib/trust/oidfed")



// OAuth2 JWT Validation
includeProject("lib-oauth2-jwt-validation-api", "lib/oauth2/jwt/validation/api")
includeProject("lib-oauth2-jwt-validation-impl", "lib/oauth2/jwt/validation/impl")

// Credential Claims Mapper
includeProject("lib-credential-claims-mapper-public", "lib/credential/claims-mapper/public")
includeProject("lib-credential-claims-mapper-impl", "lib/credential/claims-mapper/impl")

// OID4VP Authentication Bridge
includeProject("lib-openid-oid4vp-auth-bridge-public", "lib/openid/oid4vp/auth-bridge/public")
includeProject("lib-openid-oid4vp-auth-bridge-impl", "lib/openid/oid4vp/auth-bridge/impl")

// Identity Matching
includeProject("lib-identity-matching-public", "lib/identity/matching/public")
includeProject("lib-identity-matching-impl", "lib/identity/matching/impl")

// Identity Resolution
includeProject("lib-identity-resolution-public", "lib/identity/resolution/public")
includeProject("lib-identity-resolution-impl", "lib/identity/resolution/impl")

// Identity Reconciliation
includeProject("lib-identity-reconciliation-public", "lib/identity/reconciliation/public")
includeProject("lib-identity-reconciliation-impl", "lib/identity/reconciliation/impl")

// Identity Verification
includeProject("lib-idv-public", "lib/identity/idv/public")
includeProject("lib-idv-oidc", "lib/identity/idv/oidc")
includeProject("lib-idv-wallet", "lib/identity/idv/wallet")

// Services (REST API deployment modules)
includeProject("ktor-server-kotlin-inject", "services/ktor/server/plugins/ktor-server-kotlin-inject")
includeProject("ktor-server-jwt-auth", "services/ktor/server/plugins/ktor-server-jwt-auth")
includeProject("services-kms-rest", "services/kms/rest")
includeProject("services-oid4vp-verifier-rest", "services/oid4vp-verifier/rest")
includeProject("services-oauth2-as-rest", "services/oauth2-as/rest")
includeProject("services-oid4vci-issuer-rest", "services/oid4vci-issuer/rest")
// services-oid4vci-holder-rest moved to EDK (vdx/edk/services/oid4vci-holder/rest)

// Examples
includeProject("examples-oid4vc-webapp-server", "examples/oid4vc/webapp/server")
includeProject("examples-service-byo-oidc", "examples/service-byo-oidc")

// Integration tests
includeProject("tests-oid4vc-integration", "tests/oid4vc-integration")
includeProject("tests-oauth2-integration", "tests/oauth2-integration")
includeProject("tests-oidf-conformance-oidc-op", "tests/oidf/conformance/oidc/op")


// ===========================================
// XCFramework / lib-all Composite Build Configuration
// ===========================================
// The lib-all project builds XCFrameworks which takes a long time.
// It is excluded from regular builds and only included when:
// 1. Environment variable BUILD_XCFRAMEWORKS=true (for releases)
// 2. Or when explicitly needed via composite build

/**
 * Determines if lib-all (XCFramework) build should be enabled.
 * Default: DISABLED (to speed up regular development builds)
 * Enable with: BUILD_XCFRAMEWORKS=true ./gradlew build
 */
fun isLibAllBuildEnabled(): Boolean {
    System.getenv("BUILD_XCFRAMEWORKS")?.let { value ->
        if (value.equals("true", ignoreCase = true) || value == "1") return true
        if (value.equals("false", ignoreCase = true) || value == "0") return false
    }
    // Default: disabled for faster builds
    return false
}

val useLibAllBuild = isLibAllBuildEnabled()

if (useLibAllBuild) {
    println("==> lib-all (XCFramework): ENABLED - XCFrameworks will be built")
    // All-in-one library (includes XCFramework builds for iOS)
    includeProject("lib-all", "lib/all")
} else {
    println("==> lib-all (XCFramework): DISABLED - Set BUILD_XCFRAMEWORKS=true to enable")
}

// BOM
includeProject("idk-bom", "versions/idk-bom")
