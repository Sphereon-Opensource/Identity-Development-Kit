rootProject.name = "idk-conformance-pack"
fun includeMapped(name: String, relativeFromIdk: String) {
    val selected = System.getenv("WORKSPACE_SOURCE_MODULES")
    if (selected != null && name !in selected.split(",").map { it.trim() }) return
    include(":$name")
    project(":$name").projectDir = settings.rootDir.resolve("..").resolve(relativeFromIdk)
}
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
    extra["idkLibVersion"] = platformVersion
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


includeMapped("tests-oidf-conformance-oid4vc", "tests/oidf/conformance/oid4vc")
includeMapped("tests-oidf-conformance-oid4vc-services", "tests/oidf/conformance/oid4vc-services")
includeMapped("tests-oidf-conformance-oid4vc-wallet", "tests/oidf/conformance/oid4vc-wallet")
