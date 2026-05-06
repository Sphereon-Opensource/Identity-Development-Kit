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

application {
    mainClass.set("com.sphereon.example.byo.ByoOidcApplicationKt")
}

dependencies {
    // ----- IDK-only runtime dependencies -----
    // Core types: SessionContext, IdkResult, error model, AppGraph abstractions.
    implementation(projects.libCoreApiPublic)
    // Default SessionContextFactory, IdentityResolutionPipeline, UserContextManager,
    // SessionContextManager bindings, DefaultRootScopeProvider, OidcPrincipalResolver.
    implementation(projects.libCoreApiDefault)

    // JWT validation: DefaultJwtValidationService, DefaultIdpRegistry.
    implementation(projects.libOauth2JwtValidationApi)
    implementation(projects.libOauth2JwtValidationImpl)

    // OAuth2 resource server: VerifyJwtCommandImpl (delegates to JwtService).
    implementation(projects.libOauth2ServerResourcePublic)
    implementation(projects.libOauth2ServerResourceImpl)

    // OAuth2 client: FetchAuthorizationServerMetadataCommand is required by
    // ResourceServerIntrospectTokenCommandImpl, which sits on the classpath
    // next to VerifyJwtCommand. We don't use introspection in the BYO demo
    // (JWTs are verified via JWKS, not the introspection endpoint) but
    // Metro needs the binding to construct the session graph.
    implementation(projects.libOauth2CommonPublic)
    implementation(projects.libOauth2CommonImpl)
    implementation(projects.libOauth2ClientPublic)
    implementation(projects.libOauth2ClientImpl)

    // Crypto core: JwtServiceImpl + VerifyJwsCommandImpl + IdentifierService +
    // SignatureService + JwksUrlExternalIdentifierResolutionServiceImpl.
    implementation(projects.libCryptoCorePublic)
    implementation(projects.libCryptoCoreImpl)

    // Software KMS provider: supplies RSA / EC verification primitives to
    // SignatureService. Without it, VerifyJwsCommand fails with
    // "No KMS found for signature algorithm RSA_SHA256" when validating
    // RS256-signed access tokens. The provider runs purely in-process, so
    // it stays IDK-pure.
    implementation(projects.libCryptoKmsProviderSoftware)

    // HTTP client implementation: the JWKS URL resolver uses HttpClientFactory.
    implementation(projects.libDataLinkHttpClientPublic)
    implementation(projects.libDataLinkHttpClientImpl)

    // A-2 Ktor plugin: install(JwtAuthentication) { ... }.
    implementation(projects.ktorServerJwtAuth)

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
