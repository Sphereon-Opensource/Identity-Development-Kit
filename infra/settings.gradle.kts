rootProject.name = "idk-infra-pack"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// IDK checkout root (infra -> ..)
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

println("==> idk-infra-pack: GBS from worktree-m2; platformVersion=$platformVersion; non-wallet production")

// Infrastructure modules (data-link, data-store, ktor plugins).
includeLocal("ktor-server-kotlin-inject", "services/ktor/server/plugins/ktor-server-kotlin-inject")
includeLocal("lib-data-integration-public", "lib/data/integration/public")
includeLocal("lib-data-link-ble-public", "lib/data/link/ble/public")
includeLocal("lib-data-link-ble-robots", "lib/data/link/ble/robots")
includeLocal("lib-data-link-ble-test-fixtures", "lib/data/link/ble/test-fixtures")
includeLocal("lib-data-link-http-client", "lib/data/link/http/client")
includeLocal("lib-data-link-http-client-impl", "lib/data/link/http/client/impl")
includeLocal("lib-data-link-http-client-public", "lib/data/link/http/client/public")
includeLocal("lib-data-link-nfc-impl", "lib/data/link/nfc/impl")
includeLocal("lib-data-link-nfc-public", "lib/data/link/nfc/public")
includeLocal("lib-data-store-asset-impl", "lib/data/store/asset/impl")
includeLocal("lib-data-store-asset-public", "lib/data/store/asset/public")
includeLocal("lib-data-store-blob-client-http", "lib/data/store/blob/client-http")
includeLocal("lib-data-store-blob-impl", "lib/data/store/blob/impl")
includeLocal("lib-data-store-blob-impl-fs", "lib/data/store/blob/impl-fs")
includeLocal("lib-data-store-blob-impl-kv", "lib/data/store/blob/impl-kv")
includeLocal("lib-data-store-blob-impl-memory", "lib/data/store/blob/impl-memory")
includeLocal("lib-data-store-blob-impl-okd", "lib/data/store/blob/impl-okd")
includeLocal("lib-data-store-blob-public", "lib/data/store/blob/public")
includeLocal("lib-data-store-credential-design-public", "lib/data/store/credential-design/public")
includeLocal("lib-data-store-credential-type-binding-impl", "lib/data/store/credential-type-binding/impl")
includeLocal("lib-data-store-credential-type-binding-public", "lib/data/store/credential-type-binding/public")
includeLocal("lib-data-store-kv-impl", "lib/data/store/kv/impl")
includeLocal("lib-data-store-kv-impl-android-protected", "lib/data/store/kv/impl-android-protected")
includeLocal("lib-data-store-kv-impl-kottage", "lib/data/store/kv/impl-kottage")
includeLocal("lib-data-store-kv-impl-memory", "lib/data/store/kv/impl-memory")
includeLocal("lib-data-store-kv-public", "lib/data/store/kv/public")
includeLocal("lib-data-store-okd-openapi", "lib/data/store/okd-openapi")
includeLocal("lib-data-store-okd-server", "lib/data/store/okd-server")
includeLocal("lib-data-store-party-public", "lib/data/store/party/public")
includeLocal("lib-data-store-schema-registry-impl", "lib/data/store/schema-registry/impl")
includeLocal("lib-data-store-schema-registry-public", "lib/data/store/schema-registry/public")
includeLocal("lib-data-store-vault-portability", "lib/data/store/vault/portability")
includeLocal("lib-data-store-vault-public", "lib/data/store/vault/public")

includeLocal("lib-conf-theme-core-public", "lib/conf/theme/core/public")
