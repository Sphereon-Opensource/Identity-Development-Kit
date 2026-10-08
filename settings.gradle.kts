import java.util.Properties

rootProject.name = "Identity-Development-Kit"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

/** Load platformVersion + gbsVersion from platform-version.properties (single source of truth). */
fun loadPlatformVersionProperties(rootDir: java.io.File): Properties {
    val file = rootDir.resolve("platform-version.properties")
    require(file.isFile) {
        "Missing ${file.absolutePath}. Create it with platformVersion= and gbsVersion=."
    }
    return Properties().apply {
        file.reader(Charsets.UTF_8).use { load(it) }
    }
}

val platformVersionProperties = loadPlatformVersionProperties(settings.rootDir)
val platformVersion = platformVersionProperties.getProperty("platformVersion")?.trim().orEmpty()
    .ifEmpty { error("platformVersion missing in platform-version.properties") }
val gbsVersionCanonical = platformVersionProperties.getProperty("gbsVersion")?.trim().orEmpty()
    .ifEmpty { error("gbsVersion missing in platform-version.properties") }

settings.extra["platformVersion"] = platformVersion
settings.extra["gbsVersion"] = gbsVersionCanonical

// Apply uniform product version to every project before build scripts run.
gradle.beforeProject {
    version = platformVersion
    extra["platformVersion"] = platformVersion
    extra["gbsVersion"] = gbsVersionCanonical
}

val sphereonBuildProfile = (System.getenv("SPHEREON_BUILD_PROFILE")
    ?: System.getProperty("sphereon.build.profile"))?.trim()?.lowercase()
val formsBuildProfileProjects = setOf(
    "lib-cbor-impl",
    "lib-cbor-public",
    "lib-core-api-default",
    "lib-core-api-public",
    "lib-core-compat-annotations",
    "lib-core-test",
    "lib-crypto-core-public",
    "lib-data-credential-definition-public",
    "lib-data-store-asset-public",
    "lib-data-store-blob-public",
    "lib-data-store-credential-design-public",
    "lib-data-store-credential-type-binding-public",
    "lib-data-store-schema-registry-public",
    "lib-openid-oid4vc-common-public",
    "lib-openid-oid4vp-dcql",
)

if (sphereonBuildProfile != null && sphereonBuildProfile != "forms") {
    throw GradleException("Unsupported sphereon.build.profile: $sphereonBuildProfile")
}

// Helper function to include a project with its name as the path while pointing to the actual directory
fun includeProject(name: String, path: String) {
    if (sphereonBuildProfile == "forms" && name !in formsBuildProfileProjects) return
    include(":$name")
    project(":$name").projectDir = file(path)
}

pluginManagement {
    val platformVersionFile = settings.rootDir.resolve("platform-version.properties")
    require(platformVersionFile.isFile) {
        "Missing ${platformVersionFile.absolutePath}. Create it with platformVersion= and gbsVersion=."
    }
    val platformProps = java.util.Properties().apply {
        platformVersionFile.reader(Charsets.UTF_8).use { load(it) }
    }
    val gbsVersionProperty = platformProps.getProperty("gbsVersion")?.trim().orEmpty()
        .ifEmpty { error("gbsVersion missing in platform-version.properties") }
    val platformVersionProperty = platformProps.getProperty("platformVersion")?.trim().orEmpty()
        .ifEmpty { error("platformVersion missing in platform-version.properties") }
    settings.extra["gbsVersion"] = gbsVersionProperty
    settings.extra["platformVersion"] = platformVersionProperty

    val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }

    // Build tooling is consumed as published artifacts in standalone builds.

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
        id("app.cash.sqldelight") version "2.3.2"
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

// Cross-repository dependencies are resolved from published Maven artifacts.
val gbsVersion: String by settings

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
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}


dependencyResolutionManagement {
    versionCatalogs {
        // Build tooling and catalogs are published Maven inputs.
        create("sphereonplug") {
            from("com.sphereon.gradle:gradle-plugin-bom:$gbsVersion@toml" as String)
        }
        create("sphereonlib") {
            from("com.sphereon.gradle:library-bom:$gbsVersion@toml" as String)
        }
        // TODO: Move aws sdk to our bom
        create("awssdk") {
            from("aws.sdk.kotlin:version-catalog:1.6.107" as String)
        }

    }
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

// Pack modules (core/, platform/, infra/, identity-security/, protocols/, wallet-lib/, wallet/)
// are owned by pack settings via includeBuild when IDK_LOCAL_PACKS is set — not registered
// as projects of Identity-Development-Kit. lib-core-benchmarks removed: core/lib/core/benchmarks
// does not exist (orphan path was lib/core/benchmarks).

// Examples
includeProject("examples-oid4vc-webapp-server", "examples/oid4vc/webapp/server")
includeProject("examples-service-byo-oidc", "examples/service-byo-oidc")

// Integration tests
includeProject("tests-oid4vc-integration", "tests/oid4vc-integration")
includeProject("tests-oauth2-integration", "tests/oauth2-integration")
includeProject("tests-oidf-conformance-oidc-op", "tests/oidf/conformance/oidc/op")
includeProject("tests-oidf-conformance-oid4vc", "tests/oidf/conformance/oid4vc")
includeProject("tests-oidf-conformance-oid4vc-services", "tests/oidf/conformance/oid4vc-services")
includeProject("tests-oidf-conformance-oid4vc-wallet", "tests/oidf/conformance/oid4vc-wallet")


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

// keep in sync with gradle/idk-local-packs.gradle.kts
// (apply(from) does not export top-level functions into this settings script)
val IDK_PACK_DIRS: Set<String> =
    setOf("core", "platform", "infra", "identity-security", "protocols", "wallet-lib")

fun parseIdkLocalPacks(): List<String> {
    val raw = System.getenv("IDK_LOCAL_PACKS")?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { packs ->
            packs.forEach { pack ->
                if (pack !in IDK_PACK_DIRS) {
                    throw GradleException(
                        "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}",
                    )
                }
            }
        }
        .distinct()
}

fun extractIdkPackModuleNames(settingsFile: File): List<String> {
    if (!settingsFile.isFile) return emptyList()
    val localOrMapped =
        Regex("""include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*\)""")
    return settingsFile.readLines()
        .filter { line ->
            val t = line.trim()
            !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
        }
        .mapNotNull { line -> localOrMapped.find(line)?.groupValues?.get(1) }
        .distinct()
}

fun Settings.includeIdkLocalPackBuilds(idkRoot: File) {
    val packs = parseIdkLocalPacks()
    if (packs.isEmpty()) {
        println("==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)")
        return
    }
    packs.forEach { pack ->
        val packRoot = idkRoot.resolve(pack)
        val packSettings = packRoot.resolve("settings.gradle.kts")
        if (!packSettings.isFile) {
            throw GradleException("Missing pack settings: ${packSettings.absolutePath}")
        }
        val modules = extractIdkPackModuleNames(packSettings)
        if (modules.isEmpty()) {
            throw GradleException(
                "No includeLocal/includeMapped modules in ${packSettings.absolutePath}",
            )
        }
        includeBuild(packRoot) {
            name = "idk-$pack"
            dependencySubstitution {
                modules.forEach { moduleName ->
                    substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
                }
            }
        }
        println("==> IDK pack source build: $pack (${modules.size} modules)")
    }
}

// Selective multi-pack source composite when developing from the IDK checkout root.
includeIdkLocalPackBuilds(settings.rootDir)
