rootProject.name = "idk-identity-security-pack"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// IDK checkout root (identity-security -> ..)
val idkRoot = settings.rootDir.resolve("..").canonicalFile

fun includeLocal(name: String, relativeFromPack: String) {
    val workspaceSelection = System.getenv("WORKSPACE_SOURCE_MODULES")
    if (workspaceSelection != null && name !in workspaceSelection.split(",").map { it.trim() }.filter { it.isNotEmpty() }) return
    include(":" + name)
    project(":" + name).projectDir = settings.rootDir.resolve(relativeFromPack)
}

pluginManagement {
    val idkRootDir = settings.rootDir.resolve("..").normalize()
    val platformVersionFile = idkRootDir.resolve("platform-version.properties")
    require(platformVersionFile.isFile) { "Missing " + platformVersionFile.absolutePath }
    val platformProps = java.util.Properties().apply {
        platformVersionFile.reader(Charsets.UTF_8).use { load(it) }
    }
    val gbsVersionProperty = platformProps.getProperty("gbsVersion")!!.trim()
    val platformVersionProperty = platformProps.getProperty("platformVersion")!!.trim()
    settings.extra["gbsVersion"] = gbsVersionProperty
    settings.extra["platformVersion"] = platformVersionProperty

    val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }


    settings.extra["gbsUseLocalGradleBuildSupport"] = false

    // GBS TOML emits id="software.amazon.app.platform"; Nexus has gradle-plugin:0.0.16SPH
    // but the plugin marker POM was only published through 0.0.15SPH. Map id -> module.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "software.amazon.app.platform") {
                useModule("software.amazon.app.platform:gradle-plugin:${requested.version}")
            }
        }
    }

    repositories {
        // Local SPH App Platform / Metro plugin markers (not always mirrored on Nexus yet).
        mavenLocal()
        if (worktreeMavenRepo != null) {
            exclusiveContent {
                forRepository {
                    maven {
                        name = "worktree"
                        url = uri(worktreeMavenRepo)
                    }
                }
                filter {
                    includeGroupByRegex(
                        if (System.getenv("WORKSPACE_TOOL_MAVEN_REPO") == null) "com\\.sphereon(\\..+)?"
                        else "com\\.sphereon(?!\\.gradle(?:\\.|$))(\\..+)?"
                    )
                }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupAndSubgroups("org.jetbrains.compose")
                includeGroupAndSubgroups("org.jetbrains.kotlin")
                includeGroupAndSubgroups("org.jetbrains.kotlinx")
                includeGroupAndSubgroups("org.jetbrains.skiko")
            }
        }
    }
    plugins {
        id("com.sphereon.gradle.toml-catalog") version gbsVersionProperty
        id("app.cash.sqldelight") version "2.3.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val platformVersion = settings.extra["platformVersion"] as String
val gbsVersion = settings.extra["gbsVersion"] as String

gradle.beforeProject {
    version = platformVersion
    group = "com.sphereon.idk"
    extra["platformVersion"] = platformVersion
    extra["gbsVersion"] = gbsVersion
    extra["idk.consumeWalletAsArtifacts"] = "true"
    extra["idk.consumeCoreAsArtifacts"] = "true"
    extra["idk.consumePlatformAsArtifacts"] = "true"
    extra["idk.consumeInfrastructureAsArtifacts"] = "true"
}

dependencyResolutionManagement {
    repositories {
        val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }

        if (worktreeMavenRepo != null) {
            exclusiveContent {
                forRepository {
                    maven {
                        name = "worktree"
                        url = uri(worktreeMavenRepo)
                    }
                }
                filter {
                    includeGroupByRegex(
                        if (System.getenv("WORKSPACE_TOOL_MAVEN_REPO") == null) "com\\.sphereon(\\..+)?"
                        else "com\\.sphereon(?!\\.gradle(?:\\.|$))(\\..+)?"
                    )
                }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupAndSubgroups("org.jetbrains.compose")
                includeGroupAndSubgroups("org.jetbrains.kotlin")
                includeGroupAndSubgroups("org.jetbrains.kotlinx")
                includeGroupAndSubgroups("org.jetbrains.skiko")
            }
        }
        mavenLocal {
            content { includeGroupAndSubgroups("com.sphereon") }
        }
    }
    versionCatalogs {
        create("sphereonplug") {
            from("com.sphereon.gradle:gradle-plugin-bom:$gbsVersion@toml" as String)
        }
        create("sphereonlib") {
            from("com.sphereon.gradle:library-bom:$gbsVersion@toml" as String)
        }
        create("libs") {
            from(files(idkRoot.resolve("gradle/libs.versions.toml")))
        }
        create("awssdk") {
            from("aws.sdk.kotlin:version-catalog:1.6.107" as String)
        }
    }
}

println("==> idk-identity-security-pack: foundation+protocols artifacts; platformVersion=$platformVersion")

// claims-mapper, idv-wallet, and idv-oidc live in the protocols pack (C1 cycle break).
includeLocal("lib-attribute-flow-public", "lib/attribute/flow/public")
includeLocal("lib-attribute-mapping-public", "lib/attribute/mapping/public")
includeLocal("lib-crypto-certificate-persistence-api", "lib/crypto/certificate/persistence/api")
includeLocal("lib-crypto-certificate-persistence-sqlite", "lib/crypto/certificate/persistence/sqlite")
includeLocal("lib-crypto-core", "lib/crypto/core")
includeLocal("lib-crypto-core-impl", "lib/crypto/core/impl")
includeLocal("lib-crypto-core-public", "lib/crypto/core/public")
includeLocal("lib-crypto-data-integrity-proof-ecdsa-rdfc-2019", "lib/crypto/data-integrity-proof/ecdsa-rdfc-2019")
includeLocal("lib-crypto-data-integrity-proof-eddsa-jcs-2022", "lib/crypto/data-integrity-proof/eddsa-jcs-2022")
includeLocal("lib-crypto-data-integrity-proof-eddsa-rdfc-2022", "lib/crypto/data-integrity-proof/eddsa-rdfc-2022")
includeLocal("lib-crypto-data-integrity-proof-impl", "lib/crypto/data-integrity-proof/impl")
includeLocal("lib-crypto-data-integrity-proof-public", "lib/crypto/data-integrity-proof/public")
includeLocal("lib-crypto-key-persistence-api", "lib/crypto/key/persistence/api")
includeLocal("lib-crypto-key-persistence-impl", "lib/crypto/key/persistence/impl")
includeLocal("lib-crypto-key-persistence-sqlite", "lib/crypto/key/persistence/sqlite")
includeLocal("lib-crypto-kms-provider-aws", "lib/crypto/kms/provider/aws")
includeLocal("lib-crypto-kms-provider-azure", "lib/crypto/kms/provider/azure")
includeLocal("lib-crypto-kms-provider-mobile", "lib/crypto/kms/provider/mobile")
includeLocal("lib-crypto-kms-provider-rest", "lib/crypto/kms/provider/rest")
includeLocal("lib-crypto-kms-provider-software", "lib/crypto/kms/provider/software")
includeLocal("lib-crypto-kms-rest-api", "lib/crypto/kms/rest/api")
// Wave 1 dual-mode: compile kms-impl against in-tree http-client-public (not stale worktree artifacts).
includeLocal("lib-data-link-http-client-public", "../infra/lib/data/link/http/client/public")
includeLocal("lib-data-link-http-client-kms-impl", "lib/data/link/http/client/kms-impl")
includeLocal("lib-crypto-secdsa-impl", "lib/crypto/secdsa/impl")
includeLocal("lib-crypto-secdsa-public", "lib/crypto/secdsa/public")
includeLocal("lib-did-core-public", "lib/did/core/public")
includeLocal("lib-did-hosting-impl", "lib/did/hosting/impl")
includeLocal("lib-did-hosting-public", "lib/did/hosting/public")
includeLocal("lib-did-manager-impl", "lib/did/manager/impl")
includeLocal("lib-did-manager-public", "lib/did/manager/public")
includeLocal("lib-did-methods-jwk", "lib/did/methods/jwk")
includeLocal("lib-did-methods-key", "lib/did/methods/key")
includeLocal("lib-did-methods-web", "lib/did/methods/web")
includeLocal("lib-did-methods-webvh-provider", "lib/did/methods/webvh/provider")
includeLocal("lib-did-methods-webvh-public", "lib/did/methods/webvh/public")
includeLocal("lib-did-methods-webvh-resolver", "lib/did/methods/webvh/resolver")
includeLocal("lib-did-methods-webvh-rest-server", "lib/did/methods/webvh/rest/server")
includeLocal("lib-did-persistence-api", "lib/did/persistence/api")
includeLocal("lib-did-persistence-memory", "lib/did/persistence/memory")
includeLocal("lib-did-persistence-sqlite", "lib/did/persistence/sqlite")
includeLocal("lib-did-persistence-test-fixtures", "lib/did/persistence/test-fixtures")
includeLocal("lib-did-resolver-impl", "lib/did/resolver/impl")
includeLocal("lib-did-resolver-public", "lib/did/resolver/public")
includeLocal("lib-did-rest-resolver-server", "lib/did/rest/resolver/server")
includeLocal("lib-identity-matching-impl", "lib/identity/matching/impl")
includeLocal("lib-identity-matching-public", "lib/identity/matching/public")
includeLocal("lib-identity-reconciliation-impl", "lib/identity/reconciliation/impl")
includeLocal("lib-identity-reconciliation-public", "lib/identity/reconciliation/public")
includeLocal("lib-identity-resolution-impl", "lib/identity/resolution/impl")
includeLocal("lib-identity-resolution-public", "lib/identity/resolution/public")
includeLocal("lib-idv-public", "lib/identity/idv/public")
includeLocal("lib-jsonld-loader", "lib/jsonld/loader")
includeLocal("lib-jsonld-processor", "lib/jsonld/processor")
includeLocal("lib-jsonld-public", "lib/jsonld/public")
includeLocal("lib-jsonld-rdf-canon", "lib/jsonld/rdf-canon")
includeLocal("lib-software-registry-impl", "lib/software/registry/impl")
includeLocal("lib-software-registry-public", "lib/software/registry/public")
includeLocal("lib-trust-core-impl", "lib/trust/core/impl")
includeLocal("lib-trust-core-public", "lib/trust/core/public")
includeLocal("lib-trust-did", "lib/trust/did")
includeLocal("lib-trust-etsi", "lib/trust/etsi")
includeLocal("lib-trust-etsi-entities-public", "lib/trust/etsi-entities-public")
includeLocal("lib-trust-oidfed", "lib/trust/oidfed")
includeLocal("lib-trust-x509", "lib/trust/x509")
includeLocal("services-did-hosting-rest", "services/did/hosting/rest")
includeLocal("services-did-manager-rest", "services/did/manager/rest")
includeLocal("services-kms-rest", "services/kms/rest")
