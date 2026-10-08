rootProject.name = "idk-protocols-pack"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// IDK checkout root (protocols -> ..)
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

    // The fork publishes its implementation; resolve it without a plugin marker.
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
    extra["idk.consumeCoreAsArtifacts"] = "true"
    extra["idk.consumePlatformAsArtifacts"] = "true"
    extra["idk.consumeInfrastructureAsArtifacts"] = "true"
    extra["idk.consumeIdentitySecurityAsArtifacts"] = "true"
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

println("==> idk-protocols-pack: mid+foundation+wallet bridges from worktree-m2; platformVersion=$platformVersion")

includeLocal("lib-credential-claims-mapper-impl", "lib/credential/claims-mapper/impl")
includeLocal("lib-credential-claims-mapper-public", "lib/credential/claims-mapper/public")
includeLocal("lib-data-store-credential-design-impl", "lib/data/store/credential-design/impl")
includeLocal("lib-idv-oidc", "lib/identity/idv/oidc")
includeLocal("lib-idv-wallet", "lib/identity/idv/wallet")
includeLocal("lib-identity-reconciliation-oidc", "lib/identity/reconciliation/oidc")
includeLocal("lib-mdoc-core", "lib/mdoc/core")
includeLocal("lib-mdoc-core-impl", "lib/mdoc/core/impl")
includeLocal("lib-mdoc-core-public", "lib/mdoc/core/public")
includeLocal("lib-mdoc-datatransfer", "lib/mdoc/datatransfer")
includeLocal("lib-mdoc-datatransfer-impl", "lib/mdoc/datatransfer/impl")
includeLocal("lib-mdoc-datatransfer-public", "lib/mdoc/datatransfer/public")
includeLocal("lib-mdoc-reader", "lib/mdoc/reader")
includeLocal("lib-mdoc-transport-ble", "lib/mdoc/transport-ble")
includeLocal("lib-mdoc-transport-ble-impl", "lib/mdoc/transport-ble/impl")
includeLocal("lib-mdoc-transport-ble-public", "lib/mdoc/transport-ble/public")
includeLocal("lib-mdoc-transport-nfc", "lib/mdoc/transport-nfc")
includeLocal("lib-mdoc-transport-oid4vp", "lib/mdoc/transport-oid4vp")
includeLocal("lib-mdoc-transport-restapi", "lib/mdoc/transport-restapi")
includeLocal("lib-oauth2-client-impl", "lib/oauth2/client/impl")
includeLocal("lib-oauth2-client-public", "lib/oauth2/client/public")
includeLocal("lib-oauth2-common-impl", "lib/oauth2/common/impl")
includeLocal("lib-oauth2-common-public", "lib/oauth2/common/public")
includeLocal("lib-oauth2-jwt-validation-api", "lib/oauth2/jwt/validation/api")
includeLocal("lib-oauth2-jwt-validation-impl", "lib/oauth2/jwt/validation/impl")
includeLocal("ktor-server-jwt-auth", "services/ktor/server/plugins/ktor-server-jwt-auth")
includeLocal("lib-oauth2-server-authorization-impl", "lib/oauth2/server/authorization/impl")
includeLocal("lib-oauth2-server-authorization-public", "lib/oauth2/server/authorization/public")
includeLocal("lib-oauth2-server-resource-impl", "lib/oauth2/server/resource/impl")
includeLocal("lib-oauth2-server-resource-public", "lib/oauth2/server/resource/public")
includeLocal("lib-oauth2-server-rest", "lib/oauth2/server/rest")
includeLocal("lib-openid-oid4vc-common-impl", "lib/openid/oid4vc/common/impl")
includeLocal("lib-openid-oid4vc-common-public", "lib/openid/oid4vc/common/public")
includeLocal("lib-openid-oid4vci-common-impl", "lib/openid/oid4vci/common/impl")
includeLocal("lib-openid-oid4vci-common-public", "lib/openid/oid4vci/common/public")
includeLocal("lib-openid-oid4vci-holder-impl", "lib/openid/oid4vci/holder/impl")
includeLocal("lib-openid-oid4vci-holder-public", "lib/openid/oid4vci/holder/public")
includeLocal("lib-openid-oid4vci-issuer-impl", "lib/openid/oid4vci/issuer/impl")
includeLocal("lib-openid-oid4vci-issuer-public", "lib/openid/oid4vci/issuer/public")
includeLocal("lib-openid-oid4vci-issuer-rest", "lib/openid/oid4vci/issuer/rest")
includeLocal("lib-openid-oid4vci-rest-impl", "lib/openid/oid4vci/rest/impl")
includeLocal("lib-openid-oid4vci-rest-public", "lib/openid/oid4vci/rest/public")
includeLocal("lib-openid-oid4vp-auth-bridge-impl", "lib/openid/oid4vp/auth-bridge/impl")
includeLocal("lib-openid-oid4vp-auth-bridge-public", "lib/openid/oid4vp/auth-bridge/public")
includeLocal("lib-openid-oid4vp-common-impl", "lib/openid/oid4vp/common/impl")
includeLocal("lib-openid-oid4vp-common-public", "lib/openid/oid4vp/common/public")
includeLocal("lib-openid-oid4vp-dcql", "lib/openid/oid4vp/dcql")
includeLocal("lib-openid-oid4vp-dcql-store-impl", "lib/openid/oid4vp/dcql-store/impl")
includeLocal("lib-openid-oid4vp-dcql-store-public", "lib/openid/oid4vp/dcql-store/public")
includeLocal("lib-openid-oid4vp-dcql-store-rest", "lib/openid/oid4vp/dcql-store/rest")
includeLocal("lib-openid-oid4vp-holder-impl", "lib/openid/oid4vp/holder/impl")
includeLocal("lib-openid-oid4vp-holder-public", "lib/openid/oid4vp/holder/public")
includeLocal("lib-openid-oid4vp-universal-impl", "lib/openid/oid4vp/universal/impl")
includeLocal("lib-openid-oid4vp-universal-public", "lib/openid/oid4vp/universal/public")
includeLocal("lib-openid-oid4vp-verifier-impl", "lib/openid/oid4vp/verifier/impl")
includeLocal("lib-openid-oid4vp-verifier-public", "lib/openid/oid4vp/verifier/public")
includeLocal("lib-openid-oid4vp-verifier-rest", "lib/openid/oid4vp/verifier/rest")
includeLocal("lib-openid-oid4vp-verifier-vcdm-impl", "lib/openid/oid4vp/verifier/vcdm-impl")
includeLocal("lib-sdjwt-impl", "lib/sdjwt/impl")
includeLocal("lib-sdjwt-public", "lib/sdjwt/public")
includeLocal("lib-statuslist-impl", "lib/statuslist/impl")
includeLocal("lib-statuslist-public", "lib/statuslist/public")
includeLocal("services-oauth2-as-rest", "services/oauth2-as/rest")
includeLocal("services-oid4vci-issuer-rest", "services/oid4vci-issuer/rest")
includeLocal("services-oid4vp-verifier-rest", "services/oid4vp-verifier/rest")
includeLocal("services-statuslist-rest", "services/statuslist/rest")
