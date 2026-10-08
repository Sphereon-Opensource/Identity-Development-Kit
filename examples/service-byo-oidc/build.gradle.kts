import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails
import org.gradle.jvm.application.scripts.ScriptGenerator
import java.io.Writer

/*
 * BYO OIDC example service.
 *
 * Demonstrates plugging an external IdP (Keycloak, Auth0, Okta, etc.) into a
 * pure-IDK Ktor server. The deployment brings its own OIDC IdP and wires
 * IDK's real JWT validation stack (`DefaultJwtValidationService` +
 * `VerifyJwtCommand` + `JwtService`) into the `JwtAuthentication` Ktor
 * plugin. The only "BYO" piece here is the IdP itself (a Keycloak
 * testcontainer in the E2E test); the validator is stock IDK.
 *
 * Purity: this module depends ONLY on com.sphereon.idk projects and
 * third-party libraries (Ktor, kotlinx, logback, testcontainers). The
 * [checkIdkPurity] task below fails the build if any com.sphereon.edk or
 * com.sphereon.vdx artifact sneaks onto the runtime classpath.
 */
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    application
}

metro {
}

group = "com.sphereon.example"
version = "1.0.0-SNAPSHOT"

// IDK publications retain the owning platform version, independent of this example.
val idkArtifactVersion = rootProject.extra["platformVersion"].toString()

application {
    mainClass.set("com.sphereon.example.byo.ByoOidcApplicationKt")
}

dependencies {
    // Root examples consume pack publications. IDK_LOCAL_PACKS composites substitute
    // these same coordinates when explicitly selected for source development.
    // ----- IDK-only runtime dependencies -----
    // Core types: SessionContext, IdkResult, error model, AppGraph abstractions.
    implementation("com.sphereon.idk:lib-core-api-public:$idkArtifactVersion")
    // Default SessionContextFactory, IdentityResolutionPipeline, UserContextManager,
    // SessionContextManager bindings, DefaultRootScopeProvider, OidcPrincipalResolver.
    implementation("com.sphereon.idk:lib-core-api-default:$idkArtifactVersion")

    // JWT validation: DefaultJwtValidationService, DefaultIdpRegistry.
    implementation("com.sphereon.idk:lib-oauth2-jwt-validation-api:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-oauth2-jwt-validation-impl:$idkArtifactVersion")

    // OAuth2 resource server: VerifyJwtCommandImpl (delegates to JwtService).
    implementation("com.sphereon.idk:lib-oauth2-server-resource-public:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-oauth2-server-resource-impl:$idkArtifactVersion")

    // OAuth2 client: FetchAuthorizationServerMetadataCommand is required by
    // ResourceServerIntrospectTokenCommandImpl, which sits on the classpath
    // next to VerifyJwtCommand. We don't use introspection in the BYO demo
    // (JWTs are verified via JWKS, not the introspection endpoint) but
    // Metro needs the binding to construct the session graph.
    implementation("com.sphereon.idk:lib-oauth2-common-public:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-oauth2-common-impl:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-oauth2-client-public:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-oauth2-client-impl:$idkArtifactVersion")

    // Crypto core: JwtServiceImpl + VerifyJwsCommandImpl + IdentifierService +
    // SignatureService + JwksUrlExternalIdentifierResolutionServiceImpl.
    implementation("com.sphereon.idk:lib-crypto-core-public:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-crypto-core-impl:$idkArtifactVersion")

    // Software KMS provider: supplies RSA / EC verification primitives to
    // SignatureService. Without it, VerifyJwsCommand fails with
    // "No KMS found for signature algorithm RSA_SHA256" when validating
    // RS256-signed access tokens. The provider runs purely in-process, so
    // it stays IDK-pure.
    implementation("com.sphereon.idk:lib-crypto-kms-provider-software:$idkArtifactVersion")

    // HTTP client implementation: the JWKS URL resolver uses HttpClientFactory.
    implementation("com.sphereon.idk:lib-data-link-http-client-public:$idkArtifactVersion")
    implementation("com.sphereon.idk:lib-data-link-http-client-impl:$idkArtifactVersion")

    // A-2 Ktor plugin: install(JwtAuthentication) { ... }.
    implementation("com.sphereon.idk:ktor-server-jwt-auth:$idkArtifactVersion")

    // DI (Metro + Amazon App Platform scope infrastructure used by AppGraph).
    implementation(libs.bundles.app.platform.di)
    implementation(sphereonlib.software.amazon.app.platform.metro.public)
    implementation(sphereonlib.software.amazon.app.platform.metro.impl)

    // Ktor server runtime.
    implementation(sphereonlib.io.ktor.server.core)
    implementation(sphereonlib.io.ktor.server.cio)

    // Kotlinx serialization (response JSON) + coroutines.
    implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
    implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

    // Logging for the demo.
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // ----- Test -----
    testImplementation(sphereonlib.io.ktor.server.test.host)
    testImplementation(sphereonlib.io.ktor.client.cio)
    testImplementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
    testImplementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)

    // JUnit 5 engine/api (via BOM).
    testImplementation(sphereonlib.org.junit.jupiter.junit.jupiter.engine)

    // Testcontainers (declared in sphereonlib library BOM).
    testImplementation(sphereonlib.org.testcontainers.testcontainers)
    testImplementation(sphereonlib.org.testcontainers.junit.jupiter)
}

class CoordinateClasspathScriptGenerator(
    private val delegate: ScriptGenerator,
    private val libraryPaths: List<String>,
) : ScriptGenerator {
    override fun generateScript(
        details: JavaAppStartScriptGenerationDetails,
        destination: Writer,
    ) {
        require(details.modulePath.isEmpty()) { "BYO distribution uses a classpath, not a module path" }
        delegate.generateScript(
            object : JavaAppStartScriptGenerationDetails by details {
                override fun getClasspath(): List<String> = libraryPaths
            },
            destination,
        )
    }
}

// Resolve after Kotlin and convention plugins finish declaring their dependencies.
afterEvaluate {
    // Colliding distribution filenames include Maven identity: fork modules may publish different
    // bytecode with the same physical JAR basename. Keep every runtime artifact.
    val runtimeDistributionArtifacts =
        configurations
            .named("runtimeClasspath")
            .get()
            .resolvedConfiguration
            .resolvedArtifacts
    val collidingLibraryBasenames =
        runtimeDistributionArtifacts.groupBy { it.file.name }.filterValues { it.size > 1 }.keys
    val distributionLibraryNames =
        runtimeDistributionArtifacts.associate { artifact ->
            val coordinate = artifact.moduleVersion.id
            val name =
                if (artifact.file.name in collidingLibraryBasenames) {
                    "${coordinate.group}__${coordinate.name}__${coordinate.version}__${artifact.file.name}"
                } else {
                    artifact.file.name
                }
            require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe distribution artifact filename: $name" }
            artifact.file.absolutePath to name
        }
    require(distributionLibraryNames.values.toSet().size == distributionLibraryNames.size) {
        "Distribution Maven identities do not produce unique filenames"
    }

    tasks.named<CreateStartScripts>("startScripts") {
        val names = distributionLibraryNames
        val libraryPaths =
            requireNotNull(classpath).files.map { file ->
                "lib/" + (names[file.absolutePath] ?: file.name)
            }
        require(libraryPaths.toSet().size == libraryPaths.size) { "Distribution classpath names collide" }
        inputs.property("distributionLibraryPaths", libraryPaths)
        unixStartScriptGenerator = CoordinateClasspathScriptGenerator(unixStartScriptGenerator, libraryPaths)
        windowsStartScriptGenerator = CoordinateClasspathScriptGenerator(windowsStartScriptGenerator, libraryPaths)
    }

    distributions.named("main") {
        val names = distributionLibraryNames
        contents.eachFile {
            names[file.absolutePath]?.let { name = it }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Inline IDK purity check - per A-10.
//
// Fails the build if any resolved artifact on the runtimeClasspath belongs to
// the "com.sphereon.edk" or "com.sphereon.vdx" groups. This guards the BYO
// example against accidentally picking up EDK/VDX code through a transitive
// dependency. Third-party groups (Ktor, kotlinx, logback, testcontainers) are
// fine.
// ---------------------------------------------------------------------------
// Eagerly resolve the artifact id list at configuration time so the doLast
// body is configuration-cache compatible (no task-execution-time Configuration
// lookups, which Gradle 9 forbids).
val runtimeArtifactIds: Provider<List<String>> =
    provider {
        configurations
            .named("runtimeClasspath")
            .get()
            .resolvedConfiguration
            .resolvedArtifacts
            .map { it.moduleVersion.id.toString() }
    }

val checkIdkPurity by tasks.registering {
    group = "verification"
    description = "Fails if any com.sphereon.edk or com.sphereon.vdx artifact is on the runtimeClasspath."

    val artifactIds = runtimeArtifactIds
    doLast {
        val ids = artifactIds.get()
        val offending =
            ids.filter { id ->
                id.startsWith("com.sphereon.edk:") || id.startsWith("com.sphereon.vdx:")
            }
        if (offending.isNotEmpty()) {
            val list = offending.joinToString("\n") { "  - $it" }
            throw GradleException(
                "IDK purity violated: found EDK/VDX artifacts on runtimeClasspath:\n$list",
            )
        }
        logger.lifecycle("checkIdkPurity: OK (${ids.size} artifacts scanned)")
    }
}

tasks.named("check") {
    dependsOn(checkIdkPurity)
}
