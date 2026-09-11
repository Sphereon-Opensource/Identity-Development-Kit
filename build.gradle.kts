@file:Suppress("UnstableApiUsage")

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

val sphereonBuildProfile = (System.getenv("SPHEREON_BUILD_PROFILE")
    ?: System.getProperty("sphereon.build.profile"))?.trim()?.lowercase()

if (sphereonBuildProfile == "forms") {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.js.npm.NpmExtension> {
            lockFileDirectory.set(rootProject.layout.projectDirectory.dir("kotlin-js-store/forms"))
        }
    }
}

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
    // kotlinx-datetime: pin to the `0.7.1-0.6.x-compat` flavour. Vanilla
    // 0.7.1 moved `kotlinx.datetime.Instant` into `kotlin.time` (typealias)
    // and ships no class file on the runtime classpath. Kotlin Dataframe's
    // CSV / convert bytecode still calls the old class and throws
    // `NoClassDefFoundError: kotlinx/datetime/Instant` at ingest. The compat
    // build keeps both the legacy class and the new stdlib alias, so both
    // Dataframe and IDK call sites resolve. Force on every configuration so
    // a transitive 0.7.1 request loses the conflict (Gradle's version
    // ordering does NOT consider `0.7.1-0.6.x-compat` greater than `0.7.1`,
    // so without an explicit force the non-compat variant wins).
    configurations.configureEach {
        resolutionStrategy {
            force("io.github.pdvrieze.xmlutil:core:0.91.3")
            force("io.github.pdvrieze.xmlutil:serialization:0.91.3")
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
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
        isIgnoreExitValue = true
        commandLine("git", "rev-parse", "--short=7", "HEAD")
    }.standardOutput.asText.get().replace("\n", "").trim().ifEmpty { "nogit" }

    // npm registry rejects republishing the same version, so each SNAPSHOT publish
    // must produce a unique version. Add a monotonic build id (CI run number, or
    // local UTC timestamp) so consecutive publishes always get a fresh version.
    val buildId = System.getenv("GITHUB_RUN_NUMBER")
        ?: DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

    val baseNoSuffix = baseVersion.removeSuffix("-SNAPSHOT")
    return "$baseNoSuffix-SNAPSHOT.$buildId.$gitCommitHash"
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

// Force evaluation of every subproject so the deprecation task below can
// inspect their applied plugins. Only runs when the task is actually requested.
if (gradle.startParameter.taskNames.any { it.endsWith("listNpmPackageNames") }) {
    subprojects.forEach { evaluationDependsOn(it.path) }
}

tasks.register("listNpmPackageNames") {
    group = "publishing"
    description = "Write all @sphereon/idk-* npm package names this build publishes to build/npm-packages.txt"
    notCompatibleWithConfigurationCache("Enumerates subprojects at execution time")
    val outFile = layout.buildDirectory.file("npm-packages.txt")
    outputs.file(outFile)
    doLast {
        val names = subprojects
            .filter { it.plugins.hasPlugin("com.sphereon.gradle.plugin.npm-publication") }
            .map { "@sphereon/idk-${it.name}" }
            .sorted()
        val f = outFile.get().asFile
        f.parentFile.mkdirs()
        f.writeText(names.joinToString(separator = "\n", postfix = "\n"))
        logger.lifecycle("Wrote ${names.size} package name(s) to ${f.absolutePath}")
    }
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

                val moduleMd = projectDir.resolve("dokka/module.md")
                if (moduleMd.exists()) {
                    dokkaPublications.html {
                        includes.from(moduleMd)
                    }
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

abstract class VerifyWalletBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val violations: org.gradle.api.provider.ListProperty<String>

    // Text-scan leg, run alongside the dependency-edge check above. Uses the same plain per-line
    // regex/text scan over a fixed file collection as VerifyWalletKmsBoundaryTask (zero
    // allowlists), so the legacy single-orchestration Wallet facade (the `Wallet` interface +
    // `WalletImpl` + their `com.sphereon.wallet.Wallet` import) can never be reintroduced
    // anywhere in the wallet trees. Deliberately scans BOTH main and test sources (unlike the
    // KMS scan, which exempts tests) - there is no legitimate reason for this facade to reappear
    // anywhere, including test code.
    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val orchestrationSourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val orchestrationVdxRootPath: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "AGPL boundary violation: non-wallet modules depend on wallet modules:\n" +
                    found.joinToString("\n"),
            )
        }

        val vdxRoot = java.io.File(orchestrationVdxRootPath.get()).canonicalFile
        // Word-boundary regexes: `\b` after `WalletImpl`/`Wallet` fails to match when the name
        // continues with more word characters, so `WalletImplementationChoice` and
        // `WalletIdentityResolver` are NOT caught, but `class WalletImpl(`, `interface Wallet {`,
        // and `import com.sphereon.wallet.Wallet` (bare, or with a trailing ` as X` alias) are.
        val classWalletImpl = Regex("""\bclass\s+WalletImpl\b""")
        // \b (not a trailing char class) so a bodiless `interface Wallet` declaration is caught too.
        val interfaceWallet = Regex("""\binterface\s+Wallet\b""")
        val importWallet = Regex("""^\s*import\s+com\.sphereon\.wallet\.Wallet\b""")
        val permissiveSecurityDefault = Regex("""=\s*WalletSecurityGate\.allow\b""")
        val orchestrationViolations =
            orchestrationSourceFiles.files.flatMap { file ->
                val normalizedPath = file.canonicalPath.replace('\\', '/')
                val isMainSource = Regex("""/src/[^/]*Main/""").containsMatchIn(normalizedPath)
                file.readLines().mapIndexedNotNull { index, line ->
                    if (classWalletImpl.containsMatchIn(line) ||
                        interfaceWallet.containsMatchIn(line) ||
                        importWallet.containsMatchIn(line) ||
                        (isMainSource && permissiveSecurityDefault.containsMatchIn(line))
                    ) {
                        "${file.relativeTo(vdxRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
        if (orchestrationViolations.isNotEmpty()) {
            throw GradleException(
                "Wallet orchestration/security boundary violation: the legacy Wallet/WalletImpl " +
                    "facade must not be reintroduced, and production constructors must not default " +
                    "to WalletSecurityGate.allow:\n" +
                    orchestrationViolations.joinToString("\n"),
            )
        }
    }
}

abstract class VerifyWalletKmsBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val allowlistedFilePaths: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val vdxRootPath: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val vdxRoot = java.io.File(vdxRootPath.get()).canonicalFile
        val allowlistedFiles = allowlistedFilePaths.get().map { java.io.File(it).canonicalFile }.toSet()
        // Match the whole com.sphereon.crypto.core.kms package (not a per-name alternation) plus the
        // whole com.sphereon.crypto.kms.provider package, so new names/providers are covered without
        // touching this task again.
        val forbiddenImport =
            Regex(
                """^\s*import\s+com\.sphereon\.crypto\.(core\.kms|kms\.provider)\..*""",
            )
        // Directory-exempt rule: any file under a lib/wallet/wscd/ module is a concrete WSCD
        // implementation by construction, so raw KMS imports there are in-boundary, not violations.
        // EDK remote wallet-unit WSCD adapters live under lib/wallet/unit/remote/.../wscd/ and
        // import KMS graph types only to replace them out of the consuming application graph.
        // allowlistedFilePaths is reserved for named, temporary leftovers only (see the task
        // registration below).
        fun isUnderWscdDirectory(file: java.io.File): Boolean {
            val path = file.canonicalFile.path.replace('\\', '/')
            return path.contains("/lib/wallet/wscd/") ||
                (path.contains("/lib/wallet/unit/remote/") && path.contains("/wscd/"))
        }
        // Flag the bare package text too, not only import lines - catches fully-qualified inline usage
        // (e.g. a type reference spelled out as com.sphereon.crypto.core.kms.KeyManagerService without
        // an import line) that the import-only regex above would miss.
        val forbiddenFqnText = "com.sphereon.crypto.core.kms."
        val forbiddenProviderFqnText = "com.sphereon.crypto.kms.provider."
        val violations =
            sourceFiles.files
                .filter { file -> file.canonicalFile !in allowlistedFiles }
                .filter { file -> !isUnderWscdDirectory(file) }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (forbiddenImport.containsMatchIn(line) ||
                            line.contains("asKeyManagerServiceGraph") ||
                            line.contains(forbiddenFqnText) || line.contains(forbiddenProviderFqnText)
                        ) {
                            "${file.relativeTo(vdxRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Wallet WSCA/WSCD boundary violation: raw KMS imports are only allowed in concrete WSCD implementations and tests:\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

abstract class VerifyWalletKmsDependencyRuleTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val violations: org.gradle.api.provider.ListProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Wallet KMS dependency violation: only lib-wallet-wscd-* modules may depend on KMS providers:\n" +
                    found.joinToString("\n"),
            )
        }
    }
}

val verifyWalletBoundaryTask = tasks.register<VerifyWalletBoundaryTask>("verifyWalletBoundary") {
    group = "verification"
    description = "Fails if a project outside wallet depends on a project under wallet, or if the deleted single-orchestration Wallet facade reappears"
    violations.set(emptyList())
    // Scan-root computation matches verifyWalletKmsBoundary below (idk lib/wallet + wallet
    // product tree + EDK lib/wallet + the VDX wallet-interaction service). Unlike the KMS scan,
    // both *Main and *Test sources are included - see this task class's KDoc above for why test
    // sources are not exempt here.
    val orchestrationVdxRoot = rootDir.parentFile.parentFile.parentFile
    val orchestrationScanRoots =
        listOf(
            rootDir.resolve("lib/wallet"),
            rootDir.resolve("wallet"),
            rootDir.parentFile.resolve("lib/wallet"),
            orchestrationVdxRoot.resolve("vdx/vdx/service/wallet-interaction"),
            // Top-level VDX-infra service assemblies: this scan root holds a Wallet consumer that
            // narrower scan-root lists can miss (services/service-wallet-interaction).
            orchestrationVdxRoot.resolve("services/service-wallet-interaction"),
            orchestrationVdxRoot.resolve("services/service-wallet-unit"),
        ).filter { it.exists() }
    orchestrationSourceFiles.from(
        orchestrationScanRoots.map { root ->
            fileTree(root) {
                include("**/*.kt")
                exclude("**/build/**")
            }
        },
    )
    orchestrationVdxRootPath.set(orchestrationVdxRoot.canonicalPath)
}

tasks.register<VerifyWalletKmsBoundaryTask>("verifyWalletKmsBoundary") {
    group = "verification"
    description = "Fails if wallet holder, interaction, or credential-store production code imports raw KMS APIs outside WSCD implementations"
    val vdxRoot = rootDir.parentFile.parentFile.parentFile
    // The idk wallet/ product tree (wallet/app, wallet/runner, wallet/cli, wallet/profile) is in
    // scope: its bootstraps (DefaultWalletApp, WalletAppBootstrap, HeadlessWalletRunnerBootstrap)
    // must not import KMS provider types directly.
    //
    // lib/openid is scanned ONLY for its holder-facing submodules (oid4vci/holder, oid4vp/holder):
    // that is the only lib/openid code sitting behind the wallet WSCA/WSCD boundary (3.1 rule 2,
    // "protocol adapters and holder libs depend on Wsca, never on Wscd or KMS"). The FULL lib/openid
    // tree is deliberately NOT in scope here: lib/openid/oid4vci/issuer and lib/openid/oid4vp/verifier
    // (and friends) are server-side issuer/verifier code with no WSCA/WSCD boundary obligation at all
    // and legitimately import raw KMS APIs directly for their own signing - scanning them would flag
    // real, correct code as a boundary violation.
    val scanRoots =
        listOf(
            rootDir.resolve("lib/wallet"),
            rootDir.resolve("wallet"),
            rootDir.parentFile.resolve("lib/wallet"),
            rootDir.resolve("lib/openid/oid4vci/holder"),
            rootDir.resolve("lib/openid/oid4vp/holder"),
            // The VDX service tree lives under vdx/vdx/... from the VDX-infra root.
            vdxRoot.resolve("vdx/vdx/service/wallet-interaction"),
        ).filter { it.exists() }
    sourceFiles.from(
        scanRoots.map { root ->
            fileTree(root) {
                include("**/src/*Main/**/*.kt")
                // Lowercase jvm-only layout (e.g. lib/wallet/cli/src/main) - the *Main glob is
                // case-sensitive and would silently skip it.
                include("**/src/main/**/*.kt")
                exclude("**/build/**")
                exclude("**/src/*Test/**")
                exclude("**/src/test/**")
            }
        },
    )
    // No production code should need a temporary allowlist entry here (bootstraps must not import
    // KMS provider types directly). Kept as an empty, explicit list (not removed) so a future
    // genuinely-temporary leftover has an obvious place to register itself.
    allowlistedFilePaths.set(emptyList())
    vdxRootPath.set(vdxRoot.canonicalPath)
}

val verifyWalletKmsDependencyRuleTask =
    tasks.register<VerifyWalletKmsDependencyRuleTask>("verifyWalletKmsDependencyRule") {
        group = "verification"
        description = "Fails if a wallet module outside lib-wallet-wscd-* depends on a KMS provider module or the crypto core impl"
        violations.set(emptyList())
    }

abstract class VerifyWalletAppBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val violations: org.gradle.api.provider.ListProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Wallet app boundary violation: wallet-app-* modules must not depend on wallet-runner " +
                    "(WalletInteractionClient wiring belongs to each Tier 1 consumer, not a shared reach " +
                    "into wallet-runner's session graph), and must not expose a KMS provider module via " +
                    "api():\n" + found.joinToString("\n"),
            )
        }
    }
}

val verifyWalletAppBoundaryTask =
    tasks.register<VerifyWalletAppBoundaryTask>("verifyWalletAppBoundary") {
        group = "verification"
        description = "Fails if a wallet-app-* module depends on wallet-runner, or exposes a KMS provider module via api()"
        violations.set(emptyList())
    }

/**
 * Enforces the greenfield presentation dependency direction:
 *
 *     headless public API -> presentation assembly -> presentation contracts -> renderer
 *
 * Contracts contain serializable state/intents and the Nav3 key marker only. The presenter is the
 * sole adapter from WalletApp/App Platform/Molecule into those contracts. Compose renders models;
 * it does not query WalletApp or holder implementations. This is intentionally a build-owned rule,
 * not a repository script that product builds can accidentally skip.
 */
abstract class VerifyWalletPresentationBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val dependencyViolations: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val idkRootPath: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val idkRoot = java.io.File(idkRootPath.get()).canonicalFile
        val violations = dependencyViolations.get().toMutableList()
        val importPattern = Regex("""^\s*import\s+([^\s]+)""")
        val walletImpl = Regex("""\b(class\s+WalletImpl|com\.sphereon\.wallet\.WalletImpl)\b""")
        val forbiddenCompatibilityDeclaration =
            Regex("""\b(?:data\s+class|sealed\s+class|class|interface|object|typealias)\s+\w*(?:Compat(?:ibility)?|Legacy)\w*\b""")
        val deprecatedDeclarationOrSuppression =
            Regex("""@(?:kotlin\.)?Deprecated\b|@Suppress\s*\(\s*[\"']DEPRECATION[\"']""")
        val bottomSheet = Regex("""\b(?:Modal|Standard)?BottomSheet\w*\b|\bbottom[ -]?sheet\b""", RegexOption.IGNORE_CASE)
        val onboardingSecretField =
            Regex("""\bval\s+(?:pin|secret|verifier|biometricToken)\s*:\s*[^,)=]+""", RegexOption.IGNORE_CASE)
        val onboardingRawSecretContainer = Regex("""\b(?:CharArray|ByteArray)\b""")
        val rawKms =
            listOf(
                "com.sphereon.crypto.core.kms.",
                "com.sphereon.crypto.kms.provider.",
                "asKeyManagerServiceGraph",
            )
        val rawNetworkClient =
            listOf(
                "io.ktor.",
                "HttpClient",
                "java.net.",
            )
        val forbiddenPlatformUi =
            listOf(
                "androidx.compose.ui.awt.SwingPanel",
                "androidx.compose.ui.viewinterop.AndroidView",
                "androidx.compose.ui.interop.UIKitView",
                "androidx.compose.ui.viewinterop.WebElementView",
                "java.awt.",
                "javax.swing.",
                "javafx.",
            )
        val credentialDetailRawImageLoader =
            listOf(
                "coil.",
                "io.kamel.",
                "AsyncImage",
                "SubcomposeAsyncImage",
                "rememberAsyncImagePainter",
                "ImageRequest",
                "KamelImage",
                "java.net.URL",
                "URL(",
            )
        val credentialDetailRendererBusinessLogic =
            listOf(
                "Json.decodeFromString",
                "Json.parseToJsonElement",
                "decodeFromString<",
                "kotlinx.serialization.json.",
                "Regex(",
                ".groupBy(",
                ".filter(",
                ".filter {",
                ".mapNotNull(",
                ".joinToString(",
                "·",
            )

        sourceFiles.files.sortedBy(java.io.File::getPath).forEach { file ->
            val path = file.canonicalFile.path.replace('\\', '/')
            val layer =
                when {
                    path.contains("/wallet/presentation/contracts/") -> "contracts"
                    path.contains("/wallet/presentation/presenter/") -> "presenter"
                    path.contains("/lib/wallet/interaction/presenter-contracts/") -> "interaction-contracts"
                    path.contains("/lib/wallet/interaction/presenter/") -> "interaction-presenter"
                    path.contains("/wallet/ui/compose/") -> "compose"
                    path.contains("/wallet/ui/navigation3/") -> "navigation3"
                    else -> return@forEach
                }
            val onboardingSource =
                file.name.contains("Onboarding", ignoreCase = true) ||
                    file.name.contains("PreProfileNavigation", ignoreCase = true)
            val onboardingSerializableLayer = layer == "contracts" || layer == "presenter" || layer == "navigation3"
            val credentialDetailRenderer = layer == "compose" && file.name.contains("CredentialDetail", ignoreCase = true)
            file.readLines().forEachIndexed { index, line ->
                val imported = importPattern.find(line)?.groupValues?.get(1)
                val commonViolation =
                    when {
                        rawKms.any(line::contains) -> "raw KMS access"
                        rawNetworkClient.any(line::contains) ->
                            "presentation layers must use profile-bound query/projection ports, not network clients"
                        walletImpl.containsMatchIn(line) -> "WalletImpl access"
                        forbiddenPlatformUi.any(line::contains) ->
                            "platform widget UI is forbidden; render with Compose Multiplatform"
                        bottomSheet.containsMatchIn(line) -> "bottom-sheet API"
                        deprecatedDeclarationOrSuppression.containsMatchIn(line) -> "deprecated declaration or suppressed deprecated usage"
                        forbiddenCompatibilityDeclaration.containsMatchIn(line) -> "compatibility or legacy declaration"
                        onboardingSource && onboardingSerializableLayer && onboardingRawSecretContainer.containsMatchIn(line) ->
                            "onboarding presentation state must not use raw secret containers"
                        onboardingSource && onboardingSerializableLayer && onboardingSecretField.containsMatchIn(line) ->
                            "onboarding presentation state must not declare serializable secret value fields"
                        imported?.contains(".compat.") == true || imported?.contains(".legacy.") == true -> "compatibility or legacy import"
                        imported?.startsWith("com.sphereon.enterprise.") == true ||
                            imported?.startsWith("com.sphereon.commercial.") == true ||
                            imported?.startsWith("com.sphereon.vdx.") == true ->
                            "commercial implementation import"
                        credentialDetailRenderer && credentialDetailRawImageLoader.any(line::contains) ->
                            "credential detail must not fetch external images; render only presenter-approved inline data"
                        credentialDetailRenderer && credentialDetailRendererBusinessLogic.any(line::contains) ->
                            "credential detail renderer must not parse claims or derive/filter activity"
                        else -> null
                    }
                val layerViolation =
                    when (layer) {
                        "contracts" ->
                            when {
                                imported == null -> null
                                imported.startsWith("com.sphereon.core.") ||
                                    imported.startsWith("kotlinx.io.") ||
                                    imported.startsWith("io.ktor.") ||
                                    imported.startsWith("com.sphereon.wallet.interaction.") &&
                                    !imported.startsWith("com.sphereon.wallet.interaction.presenter.contracts.") ->
                                    "wallet presentation contracts must depend only on serializable presentation contracts"
                                imported.startsWith("androidx.") ||
                                    imported.startsWith("org.jetbrains.compose.") ||
                                    imported.startsWith("software.amazon.app.platform.") ||
                                    imported.startsWith("com.sphereon.wallet.app.") ||
                                    imported.startsWith("com.sphereon.wallet.profile.") ||
                                    imported.startsWith("com.sphereon.wallet.ui.") ->
                                    "contracts must remain framework and wallet-runtime neutral"
                                else -> null
                            }
                        "interaction-contracts" ->
                            when {
                                imported == null ->
                                    when {
                                        Regex("""\bval\s+(?:state|authorizationUrl|accessToken|refreshToken|privateKey|credentialPayload)\s*:""")
                                            .containsMatchIn(line) -> "safe interaction contracts must not expose raw state or secret-bearing fields"
                                        else -> null
                                    }
                                imported.startsWith("com.sphereon.core.") ||
                                    imported.startsWith("kotlinx.io.") ||
                                    imported.startsWith("io.ktor.") ||
                                    imported.startsWith("com.sphereon.wallet.interaction.") &&
                                    !imported.startsWith("com.sphereon.wallet.interaction.presenter.contracts.") ->
                                    "interaction presenter contracts must contain presentation-owned types only"
                                imported.startsWith("androidx.") ||
                                    imported.startsWith("org.jetbrains.compose.") ||
                                    imported.startsWith("software.amazon.app.platform.") ||
                                    imported.startsWith("com.sphereon.wallet.app.") ||
                                    imported.startsWith("com.sphereon.wallet.profile.") ||
                                    imported.startsWith("com.sphereon.wallet.ui.") ||
                                    imported.contains(".impl.") ->
                                    "interaction presenter contracts must remain framework and runtime-implementation neutral"
                                else -> null
                            }
                        "presenter" ->
                            when {
                                imported == null -> null
                                imported.startsWith("androidx.compose.") && !imported.startsWith("androidx.compose.runtime.") ->
                                    "presenters may use Compose runtime for Molecule, not Compose UI"
                                imported.startsWith("androidx.navigation3.ui.") ||
                                    imported.startsWith("com.sphereon.wallet.ui.") ||
                                    imported.startsWith("com.sphereon.wallet.runner.") ||
                                    imported.contains(".impl.") ->
                                    "presenters must not depend on a renderer, runner, or implementation package"
                                else -> null
                            }
                        "interaction-presenter" ->
                            when {
                                imported == null -> null
                                imported.startsWith("androidx.compose.") && !imported.startsWith("androidx.compose.runtime.") ->
                                    "interaction presenters may use Compose runtime for Molecule, not Compose UI"
                                imported.startsWith("androidx.navigation3.") ||
                                    imported.startsWith("com.sphereon.wallet.ui.") ||
                                    imported.startsWith("com.sphereon.wallet.runner.") ||
                                    imported.contains(".impl.") ->
                                    "interaction presenters must not depend on navigation, renderers, runners, or implementations"
                                else -> null
                            }
                        "compose" ->
                            when {
                                imported == null -> null
                                imported.startsWith("com.sphereon.wallet.app.") ||
                                    imported.startsWith("com.sphereon.wallet.profile.") ||
                                    imported.startsWith("com.sphereon.wallet.credential.") ||
                                imported.startsWith("com.sphereon.wallet.interaction.") &&
                                    !imported.startsWith("com.sphereon.wallet.interaction.presenter.contracts.") ||
                                    imported.startsWith("com.sphereon.wallet.runner.") ||
                                    imported.startsWith("com.sphereon.wallet.presentation.presenter.") ||
                                    imported.startsWith("androidx.navigation3.") ||
                                    imported.contains(".impl.") ->
                                    "Compose must render presentation APIs instead of accessing wallet/runtime implementations"
                                else -> null
                            }
                        "navigation3" ->
                            when {
                                imported == null -> null
                                imported.startsWith("com.sphereon.wallet.app.") ||
                                    imported.startsWith("com.sphereon.wallet.profile.") ||
                                    imported.startsWith("com.sphereon.wallet.credential.") ||
                                    imported.startsWith("com.sphereon.wallet.interaction.") ||
                                    imported.startsWith("com.sphereon.wallet.runner.") ||
                                    imported.contains(".impl.") ->
                                    "Navigation 3 adapter must consume presentation state and renderer APIs only"
                                else -> null
                            }
                        else -> null
                    }
                listOfNotNull(commonViolation, layerViolation).distinct().forEach { reason ->
                    violations +=
                        "${file.relativeTo(idkRoot).invariantSeparatorsPath}:${index + 1}: $reason: ${line.trim()}"
                }
            }
        }

        val credentialDetailContract =
            idkRoot.resolve(
                "wallet/presentation/contracts/src/commonMain/kotlin/com/sphereon/wallet/presentation/WalletCredentialDetailModels.kt",
            )
        if (!credentialDetailContract.isFile) {
            violations += "wallet/presentation/contracts: credential detail contract is missing"
        } else {
            val detailSliceSource =
                sourceFiles.files
                    .filter { it.name.contains("CredentialDetail", ignoreCase = true) }
                    .joinToString("\n") { it.readText() }
            listOf(
                "WalletCredentialFacePresentation",
                "WalletInfoNodePresentation",
                "WalletInfoValuePresentation",
                "WalletPrivacyPresentation",
            ).filterNot(detailSliceSource::contains).forEach { canonicalType ->
                violations +=
                    "${credentialDetailContract.relativeTo(idkRoot).invariantSeparatorsPath}: " +
                        "credential detail must reuse canonical $canonicalType"
            }
            val duplicateInfoAst =
                Regex(
                    """\b(?:data\s+class|sealed\s+(?:class|interface)|class|interface|typealias)\s+\w*(?:Claim|Info)(?:Node|Value|Markdown)\w*\b""",
                )
            credentialDetailContract.readLines().forEachIndexed { index, line ->
                if (duplicateInfoAst.containsMatchIn(line)) {
                    violations +=
                        "${credentialDetailContract.relativeTo(idkRoot).invariantSeparatorsPath}:${index + 1}: " +
                            "credential detail must not duplicate the canonical info/rich-text AST: ${line.trim()}"
                }
            }
        }
        sourceFiles.files.sortedBy(java.io.File::getPath).forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (line.contains("WalletCredentialDetailTab")) {
                    violations +=
                        "${file.relativeTo(idkRoot).invariantSeparatorsPath}:${index + 1}: " +
                            "obsolete detail-tab route is forbidden; credential detail is one process-style screen: ${line.trim()}"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Wallet presentation boundary violation. Keep contracts framework-neutral, adapt " +
                    "WalletApp only in presenters, and keep renderers free of wallet business logic:\n" +
                    violations.distinct().joinToString("\n"),
            )
        }
    }
}

val verifyWalletPresentationBoundaryTask =
    tasks.register<VerifyWalletPresentationBoundaryTask>("verifyWalletPresentationBoundary") {
        group = "verification"
        description = "Enforces greenfield wallet contracts/presenter/renderer dependency and source boundaries"
        dependencyViolations.set(emptyList())
        val presentationRoots =
            listOf(
                rootDir.resolve("wallet/presentation/contracts"),
                rootDir.resolve("wallet/presentation/presenter"),
                rootDir.resolve("lib/wallet/interaction/presenter-contracts"),
                rootDir.resolve("lib/wallet/interaction/presenter"),
                rootDir.resolve("wallet/ui/compose"),
                rootDir.resolve("wallet/ui/navigation3"),
            )
        sourceFiles.from(
            presentationRoots.map { root ->
                fileTree(root) {
                    include("**/*.kt")
                    exclude("**/build/**")
                }
            },
        )
        idkRootPath.set(rootDir.canonicalPath)
    }

abstract class VerifyWalletReferenceBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val dependencyViolations: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val idkRootPath: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val banned =
            Regex(
                """@(?:kotlin\.)?Deprecated\b|\bTODO\b|\bFIXME\b|\b(?:Modal|Standard)?BottomSheet\w*\b|\bbottom[ -]?sheet\b|\b(?:Legacy|Compat(?:ibility)?)\w*\b""",
                RegexOption.IGNORE_CASE,
            )
        val forbiddenImports =
            listOf(
                "com.sphereon.crypto.core.kms.",
                "com.sphereon.crypto.kms.provider.",
                "com.sphereon.wallet.runner.",
                "com.sphereon.enterprise.",
                "com.sphereon.commercial.",
                "com.sphereon.vdx.",
                "io.ktor.",
                "java.net.",
                "HttpClient",
            )
        val forbiddenPlatformUi =
            listOf(
                "androidx.compose.ui.awt.SwingPanel",
                "androidx.compose.ui.viewinterop.AndroidView",
                "androidx.compose.ui.interop.UIKitView",
                "androidx.compose.ui.viewinterop.WebElementView",
                "java.awt.",
                "javax.swing.",
                "javafx.",
            )
        val localOnlyForbidden =
            listOf(
                "com.sphereon.enterprise.",
                "com.sphereon.commercial.",
                "com.sphereon.vdx.",
                "com.sphereon.wallet.interaction.remote.",
                "com.sphereon.wallet.unit.remote.",
                "com.sphereon.data.store.blob.http.",
                "ManagedProfileProvisioningRequest",
                "ManagedProfileBinding",
                "WalletBackendRouteRef",
                "DefaultPrincipalMapPropertySource",
                "DefaultAppMapPropertySource",
            )
        val localOnboardingNetworkForbidden =
            listOf(
                "io.ktor.",
                "HttpClient",
                "com.sphereon.wallet.interaction.remote.",
                "com.sphereon.wallet.unit.remote.",
                "RemoteConfiguration",
                "RemoteBranding",
                "RemoteProvisioning",
                "ManagedProfileProvisioningRequest",
            )
        val violations = dependencyViolations.get().toMutableList()
        val idkRoot = java.io.File(idkRootPath.get()).canonicalFile
        val androidRuntimeRequirements =
            mapOf(
                "wallet/app/impl/src/androidMain/kotlin/com/sphereon/wallet/app/LocalWalletAndroidStorageBindings.kt" to
                    listOf(
                        "AndroidProtectedPreferencesKvStoreConfig",
                        "FileSystemBlobStoreConfig",
                        "LocalWalletStorageCapability.DurableFileSystemStorage",
                    ),
                "wallet/reference/local/src/androidMain/kotlin/com/sphereon/wallet/reference/local/AndroidWalletUserAuthentication.kt" to
                    listOf(
                        "application = context.applicationContext",
                        "profileDescriptorStore = AndroidProfileDescriptorStore",
                        "LocalWalletStorageCapability.DurableFileSystemStorage",
                    ),
                "wallet/reference/local/src/androidMain/kotlin/com/sphereon/wallet/reference/local/AndroidProfileDescriptorStore.kt" to
                    listOf(
                        "AndroidKeyStore",
                        "AES/GCM/NoPadding",
                        "Context.MODE_PRIVATE",
                    ),
            )
        androidRuntimeRequirements.forEach { (relativePath, requiredFragments) ->
            val source = idkRoot.resolve(relativePath)
            if (!source.isFile) {
                violations += "$relativePath: Android LOCAL durable runtime source is missing"
            } else {
                val text = source.readText()
                requiredFragments.filterNot(text::contains).forEach { fragment ->
                    violations += "$relativePath: Android LOCAL durable runtime contract is missing '$fragment'"
                }
            }
        }
        sourceFiles.files.sortedBy(java.io.File::getPath).forEach { file ->
            val normalizedPath = file.canonicalFile.path.replace('\\', '/')
            val localCompositionSource =
                normalizedPath.contains("/wallet/reference/local/src/") ||
                    normalizedPath.contains("/wallet/reference/node/src/") ||
                    normalizedPath.contains("/wallet/reference/wasm/src/") ||
                    normalizedPath.endsWith("/wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app/WalletAppBootstrap.kt") ||
                    normalizedPath.contains("/wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app/LocalWallet") ||
                    normalizedPath.contains("/wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app/AppLocalWallet") ||
                    normalizedPath.contains("/wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app/di/WalletProductAppGraph.kt") ||
                    normalizedPath.endsWith("/wallet/profile/impl/src/commonMain/kotlin/com/sphereon/wallet/profile/impl/LocalProfileProvisioner.kt")
            val localOnboardingSource =
                normalizedPath.endsWith("/wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app/LocalWalletApplicationServices.kt") ||
                    normalizedPath.endsWith("/wallet/presentation/presenter/src/commonMain/kotlin/com/sphereon/wallet/presentation/presenter/WalletLocalOnboardingPresenter.kt") ||
                    normalizedPath.endsWith("/wallet/reference/app/src/commonMain/kotlin/com/sphereon/wallet/reference/app/WalletReferenceBootstrapCoordinator.kt") ||
                    normalizedPath.contains("/wallet/reference/local/src/") ||
                    normalizedPath.contains("/wallet/reference/node/src/") ||
                    normalizedPath.contains("/wallet/reference/wasm/src/")
            file.readLines().forEachIndexed { index, line ->
                val reason =
                    when {
                        forbiddenImports.any(line::contains) -> "forbidden runtime import"
                        forbiddenPlatformUi.any(line::contains) -> "platform widget UI is forbidden; render with Compose Multiplatform"
                        localCompositionSource && localOnlyForbidden.any(line::contains) ->
                            "local reference must not resolve managed/backend routes or remote wallet clients"
                        localOnboardingSource && localOnboardingNetworkForbidden.any(line::contains) ->
                            "local onboarding must remain local-authority-only and must not resolve VDX or remote clients"
                        banned.containsMatchIn(line) -> "greenfield source violation"
                        else -> null
                    }
                if (reason != null) violations += "${file.relativeTo(idkRoot).invariantSeparatorsPath}:${index + 1}: $reason: ${line.trim()}"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Wallet reference boundary violation. Keep the product on public WalletApp/presenter/UI " +
                "contracts and confine local runtime assembly to native or Node local-authority adapters:\n" +
                    violations.distinct().joinToString("\n"),
            )
        }
    }
}

val verifyWalletReferenceBoundaryTask =
    tasks.register<VerifyWalletReferenceBoundaryTask>("verifyWalletReferenceBoundary") {
        group = "verification"
        description = "Enforces the greenfield AGPL reference app and local composition-root boundary"
        dependencyViolations.set(emptyList())
        sourceFiles.from(
            fileTree(rootDir.resolve("wallet/reference")) {
                include("**/src/*Main/**/*.kt")
                exclude("**/build/**")
            },
            fileTree(rootDir.resolve("wallet/app/impl/src/commonMain/kotlin/com/sphereon/wallet/app")) {
                include("LocalWallet*.kt")
                include("AppLocalWallet*.kt")
                include("WalletAppBootstrap.kt")
                include("di/WalletProductAppGraph.kt")
            },
            fileTree(rootDir.resolve("lib/wallet/party/local/src")) {
                include("*Main/**/*.kt")
                exclude("**/build/**")
            },
            fileTree(rootDir.resolve("wallet/app/impl/src/androidMain/kotlin/com/sphereon/wallet/app")) {
                include("LocalWallet*.kt")
            },
            fileTree(rootDir.resolve("lib/wallet/party")) {
                include("**/src/*Main/**/*.kt")
                exclude("**/build/**")
            },
            rootDir.resolve("wallet/presentation/presenter/src/commonMain/kotlin/com/sphereon/wallet/presentation/presenter/WalletLocalOnboardingPresenter.kt"),
            rootDir.resolve("wallet/reference/app/src/commonMain/kotlin/com/sphereon/wallet/reference/app/WalletReferenceBootstrapCoordinator.kt"),
            rootDir.resolve("wallet/profile/impl/src/commonMain/kotlin/com/sphereon/wallet/profile/impl/LocalProfileProvisioner.kt"),
        )
        idkRootPath.set(rootDir.canonicalPath)
    }

val verifyWalletKitTargetContractTask =
    tasks.register("verifyWalletKitTargetContract") {
        group = "verification"
        description = "Requires the settled wallet-kit JVM, classic JS, WasmJS, and iOS target matrix"
        notCompatibleWithConfigurationCache("Reads wallet Gradle scripts from the source tree")
        val walletKitBuild = rootDir.resolve("wallet/kit/build.gradle.kts")
        inputs.file(walletKitBuild)
        doLast {
            val source = walletKitBuild.readText()
            val requiredDeclarations =
                linkedMapOf(
                    "JVM" to Regex("""\bjvm\s*\("""),
                    "classic JS" to Regex("""(?m)^\s*js\s*\{"""),
                    "WasmJS" to Regex("""\bconfigureWasmJsTargetIfEnabled\s*\{"""),
                    "iOS device and simulator" to Regex("""\bconfigureIosTargetsIfEnabled\s*\("""),
                )
            val missing = requiredDeclarations.filterValues { declaration -> !declaration.containsMatchIn(source) }.keys
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "wallet-kit target contract regression. P5 requires JVM, classic JS, WasmJS, " +
                        "iOS device, and iOS simulator; missing: ${missing.joinToString()}. " +
                        "See test-results/wallet-v4-p5-verification.md.",
                )
            }
        }
    }

val verifyWalletProductUiTargetContractTask =
    tasks.register("verifyWalletProductUiTargetContract") {
        group = "verification"
        description = "Requires JVM, classic JS, WasmJS, Android, and iOS across the wallet product UI chain"
        notCompatibleWithConfigurationCache("Reads wallet Gradle scripts from the source tree")
        val productBuilds =
            linkedMapOf(
                "lib-conf-theme-compose" to rootDir.resolve("lib/conf/theme/compose/build.gradle.kts"),
                "lib-ui-compose" to rootDir.resolve("lib/ui/compose/build.gradle.kts"),
                "wallet-presentation-contracts" to rootDir.resolve("wallet/presentation/contracts/build.gradle.kts"),
                "wallet-presentation" to rootDir.resolve("wallet/presentation/presenter/build.gradle.kts"),
                "wallet-presentation-molecule" to rootDir.resolve("wallet/presentation/molecule/build.gradle.kts"),
                "wallet-ui-compose" to rootDir.resolve("wallet/ui/compose/build.gradle.kts"),
                "wallet-ui-navigation3" to rootDir.resolve("wallet/ui/navigation3/build.gradle.kts"),
                "wallet-reference-app" to rootDir.resolve("wallet/reference/app/build.gradle.kts"),
                "wallet-reference-compose" to rootDir.resolve("wallet/reference/compose/build.gradle.kts"),
            )
        val nativeReferenceBuild = rootDir.resolve("wallet/reference/local/build.gradle.kts")
        val nodeReferenceBuild = rootDir.resolve("wallet/reference/node/build.gradle.kts")
        val wasmReferenceBuild = rootDir.resolve("wallet/reference/wasm/build.gradle.kts")
        inputs.files(productBuilds.values + nativeReferenceBuild + nodeReferenceBuild + wasmReferenceBuild)
        doLast {
            val requiredDeclarations =
                linkedMapOf(
                    "JVM" to Regex("""\bjvm\s*\("""),
                    "classic JS" to Regex("""\bconfigureJsTargetIfEnabled\s*\{"""),
                    "WasmJS" to Regex("""\bconfigureWasmJsTargetIfEnabled\s*\{"""),
                    "Android target" to Regex("""\bandroid\s*\{"""),
                    "Android KMP plugin" to Regex("""plugins\.com\.android\.kotlin\.multiplatform\.library"""),
                    "iOS device and simulator" to Regex("""\bconfigureIosTargetsIfEnabled\s*\("""),
                )
            val violations =
                productBuilds.flatMap { (module, buildFile) ->
                    val source = buildFile.readText()
                    requiredDeclarations
                        .filterValues { declaration -> !declaration.containsMatchIn(source) }
                        .keys
                        .map { missing -> "$module: missing $missing" } +
                        if (Regex("""\bandroidLibrary\s*\{""").containsMatchIn(source)) {
                            listOf("$module: deprecated androidLibrary target DSL is forbidden; use android")
                        } else {
                            emptyList()
                        }
                }
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Wallet product UI target contract regression. Every product layer must " +
                        "declare JVM, classic JS, WasmJS, Android, iOS device, and iOS simulator targets:\n" +
                        violations.joinToString("\n"),
                )
            }

            val nativeReference = nativeReferenceBuild.readText()
            val nodeReference = nodeReferenceBuild.readText()
            val wasmReference = wasmReferenceBuild.readText()
            val splitViolations = buildList {
                if (!Regex("""\bjvm\s*\(\s*\"desktop\"""").containsMatchIn(nativeReference)) add("wallet-reference-local: missing desktop JVM")
                if (!Regex("""\bandroid\s*\{""").containsMatchIn(nativeReference)) add("wallet-reference-local: missing Android")
                if (!Regex("""\bconfigureIosTargetsIfEnabled\s*\(""").containsMatchIn(nativeReference)) add("wallet-reference-local: missing iOS")
                if (!Regex("""\bconfigureJsTargetIfEnabled\s*\{""").containsMatchIn(nodeReference)) add("wallet-reference-node: missing Node.js")
                if (!Regex("""\bnodejs\s*\(""").containsMatchIn(nodeReference)) add("wallet-reference-node: JS target is not Node.js")
                if (!Regex("""\bconfigureWasmJsTargetIfEnabled\s*\{""").containsMatchIn(wasmReference)) add("wallet-reference-wasm: missing WasmJS")
                if (!Regex("""\bbrowser\s*\{""").containsMatchIn(wasmReference)) add("wallet-reference-wasm: WasmJS target is not browser Compose")
            }
            if (splitViolations.isNotEmpty()) {
                throw GradleException("Wallet reference adapter target regression:\n${splitViolations.joinToString("\n")}")
            }
        }
    }

val verifyWalletUiDependencyVersionsTask =
    tasks.register("verifyWalletUiDependencyVersions") {
        group = "verification"
        description = "Pins the supported Compose Multiplatform and Navigation 3 versions for every wallet target"
        notCompatibleWithConfigurationCache("Reads wallet and BOM Gradle scripts from the source tree")
        val composeBom = rootDir.resolve("gradle-build-support/versions/gradle-plugin-bom/build.gradle.kts")
        val libraryBom = rootDir.resolve("gradle-build-support/versions/library-bom/build.gradle.kts")
        val walletBuilds =
            fileTree(rootDir.resolve("wallet")) { include("**/build.gradle.kts") }
                .files
                .sortedBy(java.io.File::getPath)
        val reusableUiBuilds =
            listOf("lib/conf/theme", "lib/ui", "lib/wallet")
                .flatMap { path -> fileTree(rootDir.resolve(path)) { include("**/build.gradle.kts") }.files }
                .sortedBy(java.io.File::getPath)
        inputs.files(composeBom, libraryBom, walletBuilds, reusableUiBuilds)
        doLast {
            val violations = mutableListOf<String>()
            if (!composeBom.readText().contains("org.jetbrains.compose:1.11.1")) {
                violations += "Compose Multiplatform plugin must be 1.11.1"
            }
            val libraryVersions = libraryBom.readText()
            listOf(
                "org.jetbrains.compose.runtime:runtime:1.11.1",
                "org.jetbrains.compose.foundation:foundation:1.11.1",
                "org.jetbrains.compose.ui:ui:1.11.1",
                "org.jetbrains.compose.ui:ui-test:1.11.1",
                "org.jetbrains.compose.ui:ui-tooling-preview:1.11.1",
                "org.jetbrains.compose.material3:material3:1.11.0-alpha07",
                "org.jetbrains.compose.material:material-icons-extended:1.7.3",
            ).filterNot(libraryVersions::contains).forEach { coordinate ->
                violations += "shared library BOM is missing $coordinate"
            }
            if (!libraryVersions.contains("androidx.navigation3:navigation3-runtime:1.1.4")) {
                violations += "canonical Navigation 3 runtime must be 1.1.4"
            }
            if (!libraryVersions.contains("org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1")) {
                violations += "Wasm/iOS-capable JetBrains Compose Multiplatform Navigation 3 UI port must be 1.1.1"
            }
            (walletBuilds + reusableUiBuilds).forEach { buildFile ->
                val text = buildFile.readText()
                if (Regex("""\b(?:api|implementation)\s*\(\s*compose\.""").containsMatchIn(text)) {
                    violations +=
                        "${buildFile.relativeTo(rootDir).invariantSeparatorsPath}: deprecated compose.* dependency accessor is forbidden; use sphereonlib"
                }
                if (text.contains("1.10.3") || text.contains("material3:material3:1.9.0")) {
                    violations += "${buildFile.relativeTo(rootDir).invariantSeparatorsPath}: stale hard-coded Compose version"
                }
                if (text.contains("\"androidx.navigation3:navigation3-ui")) {
                    violations +=
                        "${buildFile.relativeTo(rootDir).invariantSeparatorsPath}: AndroidX Navigation 3 UI has no JS/WasmJS/iOS variants; use the JetBrains Compose Multiplatform port"
                }
            }
            if (violations.isNotEmpty()) throw GradleException("Wallet UI dependency version violation:\n${violations.joinToString("\n")}")
        }
    }

val verifyWalletWebRuntimeBoundaryTask =
    tasks.register("verifyWalletWebRuntimeBoundary") {
        group = "verification"
        description = "Keeps the Wasm renderer free of local runtime/Node/VDX implementation code"
        notCompatibleWithConfigurationCache("Reads wallet Wasm/Node sources from the source tree")
        val wasmSources = fileTree(rootDir.resolve("wallet/reference/wasm/src/wasmJsMain")) { include("**/*.kt") }
        val nodeMain = rootDir.resolve("wallet/reference/node/src/jsMain/kotlin/com/sphereon/wallet/reference/local/NodeLocalWalletReferenceMain.kt")
        val idkRootPath = rootDir.canonicalPath
        inputs.files(wasmSources, nodeMain)
        doLast {
            val forbidden = listOf(
                "LocalWalletAppBootstrap",
                "createLocalWalletReferenceApp",
                "WalletPresentationSessionController",
                "WalletReferenceWeb",
                "com.sphereon.wallet.app.",
                "kotlinx.io.files",
                "node:http",
                "node:fs",
                "com.sphereon.vdx.",
                "com.sphereon.enterprise.",
                "com.sphereon.commercial.",
            )
            val violations =
                inputs.files.files.filter { it.extension == "kt" && it.path.replace('\\', '/').contains("/wasmJsMain/") }.flatMap { source ->
                    source.readLines().mapIndexedNotNull { index, line ->
                        forbidden.firstOrNull(line::contains)?.let { token ->
                            "${source.relativeTo(java.io.File(idkRootPath)).invariantSeparatorsPath}:${index + 1}: forbidden Wasm dependency '$token'"
                        }
                    }
                }.toMutableList()
            val nodeText = nodeMain.readText()
            listOf("WalletPresentationSessionController", "LocalWalletAppBootstrap", "127.0.0.1", "isAllowedOrigin").filterNot(nodeText::contains).forEach {
                violations += "wallet/reference/node authority is missing '$it'"
            }
            if (nodeText.contains("WalletReferenceApplicationContent") || nodeText.contains("androidx.compose")) {
                violations += "wallet/reference/node authority must not contain Compose application code"
            }
            if (violations.isNotEmpty()) throw GradleException("Wallet web runtime boundary violation:\n${violations.joinToString("\n")}")
        }
    }

val kmpTargetsForWalletVerify = (System.getProperty("kmp.targets") ?: "jvm")
    .split(",").map { it.trim().lowercase() }
val wasmEnabledForWalletVerify =
    "all" in kmpTargetsForWalletVerify ||
        "wasmjs" in kmpTargetsForWalletVerify ||
        "wasm" in kmpTargetsForWalletVerify

val verifyWalletWasmArtifactBoundaryTask =
    tasks.register("verifyWalletWasmArtifactBoundary") {
        group = "verification"
        description = "Proves the browser artifact cannot link Node filesystem or local wallet authority code"
        notCompatibleWithConfigurationCache("Inspects compiled Wasm artifacts and is skipped for JVM-only builds")
        onlyIf { wasmEnabledForWalletVerify }
        if (wasmEnabledForWalletVerify) {
            dependsOn(":wallet-reference-wasm:compileDevelopmentExecutableKotlinWasmJs")
        }
        val executableRoot =
            rootDir.resolve("wallet/reference/wasm/build/compileSync/wasmJs/main/developmentExecutable/kotlin")
        outputs.upToDateWhen { false }
        doLast {
            val artifacts = executableRoot.listFiles()?.filter { it.extension in setOf("wasm", "mjs", "js") }.orEmpty()
            if (artifacts.isEmpty()) throw GradleException("Wallet Wasm executable artifacts are missing at $executableRoot")
            val forbidden =
                listOf(
                    "SystemPathSeparator",
                    "kotlinx.io.files.Path",
                    "nodeModulesWasmJs",
                    "getRequire",
                    "LocalWalletAppBootstrap",
                    "WalletPresentationSessionController",
                    "WalletReferenceWeb",
                )
            val violations = mutableListOf<String>()
            artifacts.forEach { artifact ->
                val content = artifact.readBytes().toString(Charsets.ISO_8859_1)
                forbidden.filter(content::contains).forEach { token ->
                    violations += "${artifact.name}: linked forbidden browser symbol '$token'"
                }
            }
            if (violations.isNotEmpty()) {
                throw GradleException("Wallet Wasm artifact boundary violation:\n${violations.joinToString("\n")}")
            }
        }
    }

val verifyWalletWasmDependencyBoundaryTask =
    tasks.register("verifyWalletWasmDependencyBoundary") {
        group = "verification"
        description = "Rejects wallet authority, filesystem, storage and network stacks from the Wasm renderer graph"
        notCompatibleWithConfigurationCache("Resolves the Wasm compile classpath and is skipped for JVM-only builds")
        onlyIf { wasmEnabledForWalletVerify }
        doLast {
            val configuration =
                requireNotNull(rootProject.findProject(":wallet-reference-wasm"))
                    .configurations.getByName("wasmJsCompileClasspath")
            val forbiddenNameFragments =
                listOf(
                    "kotlinx-io",
                    "ktor-client",
                    "ktor-io",
                    "okio",
                    "lib-core-api-public",
                    "lib-data-store-blob",
                )
            val forbiddenExactNames = setOf("wallet-app-public", "wallet-app-impl", "wallet-presentation")
            val violations =
                configuration.resolvedConfiguration.resolvedArtifacts
                    .filter { artifact ->
                        artifact.name in forbiddenExactNames ||
                            forbiddenNameFragments.any { token -> artifact.name.contains(token, ignoreCase = true) }
                    }
                    .map { artifact -> "${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.moduleVersion.id.version}" }
                    .distinct()
                    .sorted()
            if (violations.isNotEmpty()) {
                throw GradleException("Wallet Wasm dependency boundary violation:\n${violations.joinToString("\n")}")
            }
        }
    }

gradle.projectsEvaluated {
    val walletRoot = rootDir.toPath().resolve("wallet").toAbsolutePath().normalize()
    // Live OIDF wallet adapter lives outside wallet/ so the conformance lane is not
    // coupled to wallet-runner's product tests. It is still a wallet consumer, not a
    // production leak of the AGPL product tree.
    val walletExemptRoots =
        listOf(
            walletRoot,
            rootDir.toPath().resolve("tests/oidf/conformance/oid4vc-wallet").toAbsolutePath().normalize(),
        )
    verifyWalletBoundaryTask.configure {
        violations.set(
            rootProject.subprojects.flatMap { project ->
                val projectPath = project.projectDir.toPath().toAbsolutePath().normalize()
                if (walletExemptRoots.any { projectPath.startsWith(it) }) {
                    emptyList()
                } else {
                    project.configurations.flatMap { configuration ->
                        configuration.dependencies
                            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                            .mapNotNull { dependency ->
                                val dependencyProject = rootProject.findProject(dependency.path)
                                val dependencyPath = dependencyProject?.projectDir?.toPath()?.toAbsolutePath()?.normalize()
                                if (dependencyPath != null && dependencyPath.startsWith(walletRoot)) {
                                    "${project.path} (${configuration.name}) -> ${dependencyProject.path}"
                                } else {
                                    null
                                }
                            }
                    }
                }
            },
        )
    }
}

abstract class VerifyWalletCustomWscdSampleBoundaryTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val dependencyViolations: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val idkRootPath: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val idkRoot = java.io.File(idkRootPath.get()).canonicalFile
        val forbiddenSource =
            listOf(
                "com.sphereon.crypto.core.kms.",
                "com.sphereon.crypto.kms.provider.",
                "@Deprecated",
                "@kotlin.Deprecated",
                "TODO",
                "FIXME",
                "BottomSheet",
            )
        val forbiddenTierOneImport = Regex("""^\s*import\s+com\.sphereon\.wallet\.(?:app|runner)\.""")
        val forbiddenImport = Regex("""^\s*import\s+.*\.(?:compat|legacy)(?:\.|$)""", RegexOption.IGNORE_CASE)
        val forbiddenLegacyAlias = Regex("""\btypealias\s+\w*(?:Legacy|Compat(?:ibility)?)\w*""", RegexOption.IGNORE_CASE)
        val violations = dependencyViolations.get().toMutableList()
        sourceFiles.files.sortedBy(java.io.File::getPath).forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val reason =
                    when {
                        forbiddenSource.any(line::contains) -> "forbidden KMS, deprecated, or placeholder source"
                        forbiddenTierOneImport.containsMatchIn(line) -> "Tier-1 import"
                        forbiddenImport.containsMatchIn(line) -> "compatibility or legacy import"
                        forbiddenLegacyAlias.containsMatchIn(line) -> "compatibility or legacy alias"
                        else -> null
                    }
                if (reason != null) {
                    violations += "${file.relativeTo(idkRoot).invariantSeparatorsPath}:${index + 1}: $reason: ${line.trim()}"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Custom WSCD sample boundary violation. The sample may depend only on Tier-2 public " +
                    "contracts, and Tier-1 must remain unaware of the sample:\n" +
                    violations.distinct().joinToString("\n"),
            )
        }
    }
}

val verifyWalletCustomWscdSampleBoundaryTask =
    tasks.register<VerifyWalletCustomWscdSampleBoundaryTask>("verifyWalletCustomWscdSampleBoundary") {
        group = "verification"
        description = "Proves the custom WSCD sample swaps at Tier 2 without Tier-1 or raw-KMS coupling"
        dependencyViolations.set(emptyList())
        sourceFiles.from(
            fileTree(rootDir.resolve("wallet/examples/custom-wscd")) {
                include("**/*.kt")
                exclude("**/build/**")
            },
        )
        idkRootPath.set(rootDir.canonicalPath)
    }

gradle.projectsEvaluated {
    val allowed =
        mapOf(
            "wallet-reference-app" to
                setOf(
                    "wallet-app-public",
                    "wallet-presentation",
                    "wallet-presentation-molecule",
                    "wallet-reference-compose",
                    "wallet-ui-compose",
                    "wallet-ui-navigation3",
                ),
            "wallet-reference-compose" to setOf("wallet-presentation-contracts", "wallet-presentation-molecule", "wallet-ui-compose", "wallet-ui-navigation3"),
            "wallet-reference-local" to setOf("wallet-reference-app", "wallet-app-impl", "lib-wallet-wsca-impl"),
            "wallet-reference-node" to
                setOf(
                    "wallet-app-impl",
                    "wallet-presentation",
                ),
            "wallet-reference-wasm" to
                setOf(
                    "wallet-reference-compose",
                    "wallet-ui-compose",
                ),
        )
    verifyWalletReferenceBoundaryTask.configure {
        val forbiddenLocalRuntimeDependencies =
            listOf(
                "enterprise-wallet",
                "commercial-wallet",
                "wallet-interaction-remote",
                "wallet-unit-remote",
                "wallet-provider-remote",
                "data-store-blob-client-http",
            )
        val localIsolationViolations = mutableListOf<String>()
        val visited = mutableSetOf<String>()

        fun visitProductionDependencies(project: org.gradle.api.Project) {
            if (!visited.add(project.path)) return
            project.configurations
                .filter { configuration ->
                    configuration.name.contains("Main", ignoreCase = true) &&
                        !configuration.name.contains("Test", ignoreCase = true)
                }.forEach { configuration ->
                    configuration.dependencies.forEach { dependency ->
                        val dependencyName = dependency.name.lowercase()
                        if (forbiddenLocalRuntimeDependencies.any(dependencyName::contains)) {
                            localIsolationViolations +=
                            "local reference production graph reaches ${dependency.group}:${dependency.name} via ${project.path}:${configuration.name}"
                        }
                        if (dependency is org.gradle.api.artifacts.ProjectDependency) {
                            rootProject.findProject(dependency.path)?.let(::visitProductionDependencies)
                        }
                    }
                }
        }
        rootProject.findProject(":wallet-reference-local")?.let(::visitProductionDependencies)
        rootProject.findProject(":wallet-reference-node")?.let(::visitProductionDependencies)
        rootProject.findProject(":wallet-reference-wasm")?.let(::visitProductionDependencies)

        dependencyViolations.set(
            allowed.flatMap { (projectName, permitted) ->
                val project = rootProject.findProject(":$projectName") ?: return@flatMap emptyList()
                project.configurations
                    .filterNot { configuration -> configuration.name.contains("Test", ignoreCase = true) }
                    .flatMap { configuration ->
                        configuration.dependencies
                            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                            .mapNotNull { dependency ->
                                val dependencyName = dependency.path.removePrefix(":")
                                if (dependencyName !in permitted) {
                                    ":$projectName (${configuration.name}) -> ${dependency.path}; allowed=$permitted"
                                } else {
                                    null
                                }
                            }
                    }
            } + localIsolationViolations,
        )
    }
}

gradle.projectsEvaluated {
    val sampleName = "wallet-example-custom-wscd"
    val allowedSampleDependencies = setOf("lib-core-api-public", "lib-wallet-wscd-public")
    val tierOneProjects = setOf("wallet-app-public", "wallet-app-impl", "wallet-runner", "wallet-cli", "wallet-kit")
    verifyWalletCustomWscdSampleBoundaryTask.configure {
        val sample = rootProject.findProject(":$sampleName")
        val sampleViolations =
            sample?.configurations.orEmpty().flatMap { configuration ->
                configuration.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .mapNotNull { dependency ->
                        val dependencyName = dependency.path.removePrefix(":")
                        if (dependencyName !in allowedSampleDependencies) {
                            ":$sampleName (${configuration.name}) -> ${dependency.path}; allowed=$allowedSampleDependencies"
                        } else {
                            null
                        }
                    }
            }
        val tierOneViolations =
            tierOneProjects.flatMap { projectName ->
                rootProject.findProject(":$projectName")?.configurations.orEmpty().flatMap { configuration ->
                    configuration.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .mapNotNull { dependency ->
                            if (dependency.path.removePrefix(":") == sampleName) {
                                ":$projectName (${configuration.name}) -> :$sampleName"
                            } else {
                                null
                            }
                        }
                }
            }
        dependencyViolations.set(sampleViolations + tierOneViolations)
    }
}

gradle.projectsEvaluated {
    val allowedProjectDependencies =
        mapOf(
            "wallet-presentation-contracts" to setOf("lib-wallet-interaction-presenter-contracts"),
            "lib-wallet-interaction-presenter-contracts" to emptySet(),
            "lib-wallet-interaction-presenter" to
                setOf(
                    "lib-wallet-interaction-public",
                    "lib-wallet-interaction-presenter-contracts",
                ),
            "wallet-presentation" to
                setOf(
                    "wallet-presentation-contracts",
                    "wallet-app-public",
                    "lib-wallet-interaction-presenter",
                    "lib-catalog-public",
                ),
            "wallet-presentation-molecule" to setOf("wallet-presentation-contracts"),
            "wallet-ui-compose" to
                setOf(
                    "wallet-presentation-contracts",
                    "lib-conf-theme-compose",
                    "lib-ui-compose",
                ),
            "wallet-ui-navigation3" to
                setOf(
                    "wallet-presentation-contracts",
                    "wallet-presentation-molecule",
                    "wallet-ui-compose",
                ),
        )
    verifyWalletPresentationBoundaryTask.configure {
        dependencyViolations.set(
            allowedProjectDependencies.flatMap { (projectName, allowedDependencies) ->
                val project = rootProject.findProject(":$projectName") ?: return@flatMap emptyList()
                project.configurations.flatMap { configuration ->
                    configuration.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .mapNotNull { dependency ->
                            val dependencyName = dependency.path.removePrefix(":")
                            if (dependencyName !in allowedDependencies) {
                                ":$projectName (${configuration.name}) -> ${dependency.path}; allowed=$allowedDependencies"
                            } else {
                                null
                            }
                        }
                }
            },
        )
    }
}

gradle.projectsEvaluated {
    // Only lib-wallet-wscd-* modules may depend on KMS provider modules or the crypto core impl; every
    // other wallet module - the lib/wallet tree AND the wallet PRODUCT tree (wallet/app, wallet/runner,
    // wallet/cli, wallet/profile) - must reach KMS through WSCA/WSCD instead of touching it directly.
    //
    // lib/openid is scanned too, but for the KMS PROVIDER prefixes only, not lib-crypto-core-impl:
    // lib-openid-oid4vci-holder-impl has a real, justified `api` dependency on lib-crypto-core-impl
    // for non-KMS JOSE/JWT impl classes, and the issuer/verifier modules (server-side, outside the
    // WSCA/WSCD boundary) legitimately use it for their own signing. lib-crypto-core-impl bundles
    // JOSE and KMS bindings in one module, so a bare module-name check cannot tell those apart -
    // the import-level verifyWalletKmsBoundary scan (extended to the oid4vci/oid4vp holder
    // submodules) draws that finer line.
    val kmsProviderPrefixes = listOf("lib-crypto-kms-provider-", "lib-crypto-kms-")
    val kmsRestrictedPrefixes = kmsProviderPrefixes + "lib-crypto-core-impl"
    val libWalletRoot = rootDir.toPath().resolve("lib/wallet").toAbsolutePath().normalize()
    val productWalletRoot = rootDir.toPath().resolve("wallet").toAbsolutePath().normalize()
    val libOpenidRoot = rootDir.toPath().resolve("lib/openid").toAbsolutePath().normalize()
    // lib-wallet-impl's jvmMain must not depend on lib-crypto-kms-provider-software directly - KMS
    // registration goes through the WSCA/WSCD boundary (SoftwareKmsProviderRegistrar,
    // lib-wallet-wscd-software). Its commonMain must not directly depend on lib-crypto-core-impl
    // either; that dependency stays transitively compile-visible via lib-openid-oid4vci-holder-impl's
    // own api dependency on it, needed there for non-KMS JOSE/crypto impl classes. Kept as an empty,
    // explicit set (not removed) so a future genuinely-temporary leftover has an obvious place to
    // register itself.
    val kmsDependencyAllowlist = emptySet<String>()
    // Test configurations are exempt: the boundary philosophy (see verifyWalletKmsBoundary's
    // message) allows raw KMS use in concrete WSCD implementations AND tests.
    fun isTestConfiguration(name: String): Boolean = name.contains("test", ignoreCase = true)
    verifyWalletKmsDependencyRuleTask.configure {
        violations.set(
            rootProject.subprojects
                .mapNotNull { p ->
                    val projectPath = p.projectDir.toPath().toAbsolutePath().normalize()
                    val restricted =
                        when {
                            p.name.startsWith("lib-wallet-wscd-") -> null
                            projectPath.startsWith(libWalletRoot) || projectPath.startsWith(productWalletRoot) -> kmsRestrictedPrefixes
                            projectPath.startsWith(libOpenidRoot) -> kmsProviderPrefixes
                            else -> null
                        }
                    restricted?.let { p to it }
                }
                .flatMap { (p, restricted) ->
                    p.configurations
                        .filterNot { isTestConfiguration(it.name) }
                        .flatMap { cfg ->
                            cfg.dependencies
                                .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                                .mapNotNull { dep ->
                                    val depName = dep.path.removePrefix(":")
                                    if (restricted.any { depName.startsWith(it) || depName == it }) {
                                        "${p.path} (${cfg.name}) -> ${dep.path}"
                                    } else {
                                        null
                                    }
                                }
                        }
                }
                .filterNot { it in kmsDependencyAllowlist },
        )
    }
}

gradle.projectsEvaluated {
    // wallet-app-* modules (wallet-app-public, wallet-app-impl) must never depend on wallet-runner -
    // each Tier 1 consumer (product app, headless runner) owns its own WalletInteractionClient wiring
    // rather than one reaching into the other's session graph - and must not expose a KMS provider
    // module via an `api`-flavored configuration (KMP source sets name these `<sourceSet>Api`, e.g.
    // `commonMainApi`). The api() check is a belt on top of verifyWalletKmsDependencyRule's braces:
    // that rule already forbids ANY non-test KMS-provider dependency in the wallet product tree,
    // while this one stays meaningful even if the product-tree scan is ever narrowed.
    val kmsRestrictedPrefixes = listOf("lib-crypto-kms-provider-", "lib-crypto-kms-", "lib-crypto-core-impl")
    fun isTestConfiguration(name: String): Boolean = name.contains("test", ignoreCase = true)
    verifyWalletAppBoundaryTask.configure {
        violations.set(
            rootProject.subprojects
                .filter { p -> p.name.startsWith("wallet-app-") }
                .flatMap { p ->
                    p.configurations
                        .filterNot { isTestConfiguration(it.name) }
                        .flatMap { cfg ->
                            cfg.dependencies
                                .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                                .mapNotNull { dep ->
                                    val depName = dep.path.removePrefix(":")
                                    when {
                                        depName == "wallet-runner" -> "${p.path} (${cfg.name}) -> :$depName"
                                        cfg.name.endsWith("Api") && kmsRestrictedPrefixes.any { depName.startsWith(it) || depName == it } ->
                                            "${p.path} (${cfg.name}) -> :$depName"
                                        else -> null
                                    }
                                }
                        }
                },
        )
    }
}

val rootCheckTask =
    tasks.findByName("check")?.let { tasks.named("check") }
        ?: tasks.register("check") {
            group = "verification"
            description = "Runs verification tasks for the root project"
        }

rootCheckTask.configure {
    dependsOn(verifyWalletBoundaryTask)
    dependsOn("verifyWalletKmsBoundary")
    dependsOn(verifyWalletKmsDependencyRuleTask)
    dependsOn(verifyWalletAppBoundaryTask)
    dependsOn(verifyWalletPresentationBoundaryTask)
    dependsOn(verifyWalletReferenceBoundaryTask)
    dependsOn(verifyWalletKitTargetContractTask)
    dependsOn(verifyWalletProductUiTargetContractTask)
    dependsOn(verifyWalletUiDependencyVersionsTask)
    dependsOn(verifyWalletWebRuntimeBoundaryTask)
    dependsOn(verifyWalletWasmArtifactBoundaryTask)
    dependsOn(verifyWalletWasmDependencyBoundaryTask)
    dependsOn(verifyWalletCustomWscdSampleBoundaryTask)
}
