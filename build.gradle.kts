@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

// Detect host architecture and OS
val osName = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()

// Only override Kotlin/Native home for Apple Silicon macOS
if (osName.contains("mac") && arch == "aarch64") {
    val konanDir = File(System.getProperty("user.home"), ".konan")
    val nativeDir = konanDir.listFiles()?.firstOrNull {
        it.isDirectory && it.name.startsWith("kotlin-native-prebuilt-macos-aarch64")
    }

    if (nativeDir != null) {
        logger.lifecycle("Using Kotlin/Native ARM64 toolchain at: ${nativeDir.absolutePath}")
        project.extensions.extraProperties["kotlin.native.home"] = nativeDir.absolutePath
    } else {
        logger.warn("No ARM64 Kotlin/Native toolchain found in ~/.konan — defaulting to automatic download.")
    }
} else {
    logger.lifecycle("Using default Kotlin/Native toolchain for $osName ($arch)")
}

allprojects {
    group = "com.sphereon.idk"
    version = "0.25.0-SNAPSHOT"
    val npmVersion by extra { getNpmVersion() }

    // Workaround: Gradle's Kryo-based test output serializer corrupts binary result files when
    // Kotlin/Native tests produce output with non-standard characters (hex addresses, mangled symbols).
    // The allTests aggregate report task crashes reading these files. Individual test tasks (jvmTest,
    // linuxX64Test, etc.) still detect and report failures correctly.
    // See: https://github.com/gradle/gradle/issues/25268
    tasks.withType<org.gradle.api.tasks.testing.TestReport>().configureEach {
        if (name == "allTests") enabled = false
    }

    // Workaround: Kotlin 2.3.x npm-publish plugin registers wasmJs tasks whose mainFile provider
    // has no value, causing assembleWasmJsPackage to fail during task graph construction.
    // When wasmJs is NOT in kmp.targets, pre-register no-op tasks before the npm-publish plugin
    // can create its broken versions. When wasmJs IS active, the ConventionsPlugin workaround handles it.
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" !in kmpTargets && "wasmjs" !in kmpTargets && "wasm" !in kmpTargets) {
        }
    }



    plugins.withType<MavenPublishPlugin> {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "sphereon-opensource"
                    val snapshotsUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/"
                    val releasesUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-releases/"
                    url = uri(if (version.toString().contains("SNAPSHOT")) snapshotsUrl else releasesUrl)
                    credentials {
                        username = System.getenv("NEXUS_USERNAME")
                        password = System.getenv("NEXUS_PASSWORD")
                    }
                }
            }
        }
    }

}

plugins {
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.conventions) apply false
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.integration.tests) apply false
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication) apply false
    alias(sphereonplug.plugins.com.android.library) apply false
    alias(sphereonplug.plugins.com.android.application) apply false
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(sphereonplug.plugins.com.vanniktech.maven.publish) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization) apply false
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin) apply false
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin) apply false
    alias(sphereonplug.plugins.dev.zacsweers.metro) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlin.android) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin) apply false
    alias(sphereonplug.plugins.software.amazon.app.platform) apply false
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu) apply false

    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.compose) apply false
    alias(sphereonplug.plugins.org.jetbrains.compose) apply false
    alias(sphereonplug.plugins.org.jetbrains.compose.hot.reload) apply false
    alias(sphereonplug.plugins.io.ktor.plugin) apply false
    alias(sphereonplug.plugins.org.jlleitschuh.gradle.ktlint) apply false
    alias(sphereonplug.plugins.io.gitlab.arturbosch.detekt) apply false
    alias(sphereonplug.plugins.org.jetbrains.dokka)
}

subprojects {
    // Modules excluded from ktlint (generated code that gets added to source sets)
    val ktlintExcludedModules = setOf(
        "lib-crypto-kms-rest-api",             // OpenAPI generated sources
        "lib-crypto-kms-provider-digidentity", // OpenAPI generated sources
        "lib-crypto-key-persistence-sqlite",   // SQLDelight generated sources in commonMain source set
        "lib-crypto-kms-provider-aws",         // BuildKonfig generated sources in commonMain source set
        "lib-crypto-kms-provider-azure",       // BuildKonfig generated sources in commonMain source set
        "lib-data-store-okd-openapi",          // OpenAPI generated sources
    )

    if (!name.endsWith("-bom")) {
        apply(plugin = "com.sphereon.gradle.plugin.conventions")
        if (name !in ktlintExcludedModules) {
            apply(plugin = "org.jlleitschuh.gradle.ktlint")
            apply(plugin = "io.gitlab.arturbosch.detekt")
        }
    }

    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.8.0")
            outputToConsole.set(true)
            coloredOutput.set(true)
            filter {
                exclude { element -> element.file.absolutePath.replace('\\', '/').contains("/build/") }
            }
        }
    }

    plugins.withId("io.gitlab.arturbosch.detekt") {
        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            baseline = file("config/detekt/baseline.xml")
            buildUponDefaultConfig = false
            allRules = false
            parallel = true
            ignoreFailures = true
            autoCorrect = false
        }
        tasks.matching { it.name == "detektGenerateConfig" }.configureEach {
            enabled = false
        }
    }

    // kotlinx-io uses eval('require')('os') internally, which fails in ESM mode because
    // `require` is not available in ES modules. Provide require to ESM modules via a CJS preload shim.
    // See: https://github.com/Kotlin/kotlinx-io/issues/345
    val shimFile = rootProject.file("gradle-build-support/js/esm-require-shim.cjs")
    if (shimFile.exists()) {
        tasks.withType<KotlinJsTest>().configureEach {
            nodeJsArgs.add("--require")
            nodeJsArgs.add(shimFile.absolutePath)
        }
    }

    // xmlutil 0.90.1: duplicate function declarations in JS ESM output. 0.91.3 fixes this.
    // kotlinx-datetime: force non-compat version (Gradle considers 0.7.1-0.6.x-compat > 0.7.1)
    configurations.configureEach {
        resolutionStrategy {
            force("io.github.pdvrieze.xmlutil:core:0.91.3")
            force("io.github.pdvrieze.xmlutil:serialization:0.91.3")
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            // Kotlin RC: force all Kotlin artifacts to match compiler version across all targets
            val kotlinVersion = extra["kotlin.version"] as String
            force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
            force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            defaultConfig {
                minSdk = 27
            }
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<AppExtension> {
            defaultConfig {
                minSdk = 27
            }
        }
    }
}

repositories {
    mavenCentral()
    google()
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

fun getNpmVersion(): String {
    val baseVersion = project.version.toString()
    if (!baseVersion.endsWith("-SNAPSHOT")) {
        return baseVersion
    }

    // Get git commit hash (workingDir needed for composite builds)
    val gitCommitHash = providers.exec {
        workingDir = rootDir
        commandLine("git", "rev-parse", "--short=7", "HEAD")
    }.standardOutput.asText.get().replace("\n", "").trim()

    return "$baseVersion-build-$gitCommitHash"
}

// =============================================================================
// Aggregate Test Task for running all IDK tests from root
// =============================================================================
tasks.register("allTests") {
    group = "verification"
    description = "Run all IDK tests across all subprojects"
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("allTests") } })
}

// =============================================================================
// Aggregate NPM Tasks for publishing JS packages
// =============================================================================
tasks.register("publishAllNpmPackages") {
    group = "publishing"
    description = "Publish all IDK npm packages to npmjs"
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("publishJsPackageToNpmjsRegistry") } })
}

tasks.register("assembleAllNpmPackages") {
    group = "publishing"
    description = "Assemble all IDK npm packages (validate without publishing)"
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("assembleJsPackage") } })
}

// =============================================================================
// Aggregate ktlint Tasks
// =============================================================================
tasks.register("ktlintCheckAll") {
    group = "verification"
    description = "Run ktlint checks across all IDK subprojects"
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") } })
}

tasks.register("ktlintFormatAll") {
    group = "formatting"
    description = "Run ktlint format across all IDK subprojects"
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("ktlintFormat") } })
}

// =============================================================================
// Aggregate detekt Tasks
// =============================================================================
tasks.register("detektAll") {
    group = "verification"
    description = "Run detekt static analysis across all IDK subprojects"
    dependsOn(provider {
        subprojects.flatMap { sub ->
            sub.tasks.matching { it.name.startsWith("detekt") && !it.name.contains("Baseline") }
        }
    })
}

tasks.register("detektBaselineAll") {
    group = "verification"
    description = "Generate detekt baselines across all IDK subprojects"
    dependsOn(provider {
        subprojects.flatMap { sub ->
            sub.tasks.matching { it.name.startsWith("detekt") && it.name.contains("Baseline") }
        }
    })
}

// =============================================================================
// Dokka HTML Documentation
// =============================================================================

// Modules to exclude from Dokka due to classpath conflicts, generated code, or deprecated status
val dokkaExcludedModules = setOf(
    "tests-oid4vc-integration",           // oid4vci integration tests
    "lib-crypto-kms-rest-api",            // OpenAPI generated sources
    "lib-crypto-kms-provider-digidentity", // OpenAPI generated sources
    "lib-all",                            // Aggregator module, no actual code
    "lib-crypto-core",                    // Deprecated - placeholder only
    "lib-data-link-http-client"           // Deprecated - placeholder only
)

dokka {
    moduleName.set("Identity-Development-Kit (IDK)")
    moduleVersion.set(version.toString())

    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        includes.from(rootDir.resolve("dokka/Module.md"))
    }

    pluginsConfiguration.html {
        footerMessage.set("© ${java.time.Year.now().value} Sphereon International B.V. | Creating Trust In A Digital World")
        customStyleSheets.from(rootDir.resolve("dokka/sphereon-styles.css"))
    }
}

// Apply Dokka plugin and aggregate dependencies only when a Dokka task is requested
val isDokkaRequested = gradle.startParameter.taskNames.any {
    it.contains("dokka", ignoreCase = true)
}
if (isDokkaRequested) {
    dependencies {
        // Aggregate documentation from all subprojects that have Dokka applied
        subprojects.forEach { subproject ->
            if (subproject.name !in dokkaExcludedModules) {
                subproject.plugins.withId("org.jetbrains.dokka") {
                    dokka(subproject)
                }
            }
        }
    }
    subprojects {
        if (name !in dokkaExcludedModules) {
            apply(plugin = "org.jetbrains.dokka")

            // Configure Dokka for each subproject with custom styles
            extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
                pluginsConfiguration.html {
                    customStyleSheets.from(rootDir.resolve("dokka/sphereon-styles.css"))
                    footerMessage.set("© ${java.time.Year.now().value} Sphereon International B.V. | Creating Trust In A Digital World")
                }
            }

            // Ensure Dokka tasks run after copyGeneratedSources (for OpenAPI-generated code)
            afterEvaluate {
                tasks.matching { it.name.startsWith("dokka") }.configureEach {
                    tasks.findByName("copyGeneratedSources")?.let { mustRunAfter(it) }
                }
            }
        }
    }
}
