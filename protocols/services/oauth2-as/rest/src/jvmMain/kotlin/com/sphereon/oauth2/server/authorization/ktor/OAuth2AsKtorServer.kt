package com.sphereon.oauth2.server.authorization.ktor

import com.sphereon.core.api.log.Log
import com.sphereon.core.api.session.AppCommandInvoker
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.server.authorization.impl.bootstrap.ensureActiveSigningKeyBlocking
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    Log.app().withTag("OAuth2AsKtorServer").info("Starting OAuth2 Authorization Server")

    val appGraph =
        createOAuth2AsAppGraph(
            application = Unit,
            appId = "oauth2-as",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )
    val signingKeyResult =
        ensureActiveSigningKeyBlocking(
            appCommandInvoker = appGraph.appCommandInvoker,
            signingKeyStore = appGraph.signingKeyStore,
            tenantInput = DefaultTenantInputString("default"),
            principalInput = DefaultPrincipalInputString("oauth2-as"),
        )
    check(!signingKeyResult.isErr) {
        "OAuth2 Authorization Server signing-key bootstrap failed: ${signingKeyResult.error}"
    }

    embeddedServer(CIO, port = configuredServerPort(), host = "0.0.0.0") {
        configureOAuth2As(appGraph)
    }.start(wait = true)
}

private fun configuredServerPort(): Int =
    System.getenv("SERVER_PORT")
        ?.toIntOrNull()
        ?.also { require(it in 1..65535) { "SERVER_PORT must be between 1 and 65535" } }
        ?: 8080

/**
 * Configure the Ktor application with OAuth2 Authorization Server routes.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover the OAuth2 AS HttpAdapter set
 * contributed via DI.
 */
fun Application.configureOAuth2As(appGraph: AppGraph) {
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
        tenantResolver = FixedTenantResolver("default")
    }
    log.info("KotlinInject plugin installed - full DI enabled")

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = """{"error": "${cause.message}"}""",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }
        // GET /login and POST /login are intentionally NOT registered here. The production
        // routes come from `installUniversalHttpAdapters()` below, which auto-discovers:
        //   - LoginPageHttpEndpointCommandImpl  (GET /login)  → LoginPageRenderer (default
        //     binding: SphereonBrandedLoginPageRenderer)
        //   - LoginSubmitHttpEndpointCommandImpl (POST /login) → UserAuthenticationProvider
        //     resolved by config (oauth2.user-provider.mode: federated|local-config|noop),
        //     sets the `oidc_login_sid` cookie and redirects to return_url.
        // A previous hand-written test login pair lived here, hardcoding `testuser/testpass`
        // and a `TestUserAuthenticationProvider` static map. It bypassed both the Sphereon
        // brand template and the OidcLoginSessionStore + cookie flow, leaving the callback
        // unable to resolve the user (the cookie was never set). Removed entirely so the
        // proper flow runs unconditionally.
    }

    // Auto-discover and expose OAuth2 HttpAdapter (login page + submit, authorize, token,
    // PAR, introspection, revocation, userinfo, etc.)
    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class OAuth2AsAppGraph : AbstractAppGraph() {
    abstract val appCommandInvoker: AppCommandInvoker
    abstract val signingKeyStore: SigningKeyStore

    // Default JWT-validation config for the STANDALONE IDK AS server: the default
    // JwtValidationService/IdpRegistry bindings on this classpath need a JwtValidationConfig. The
    // enterprise tenant-as assembly provides its own via PlatformBearerAuth — this graph-local
    // provider keeps the standalone server self-sufficient without colliding with that binding.
    @Provides
    @SingleIn(AppScope::class)
    fun provideJwtValidationConfig(): JwtValidationConfig = JwtValidationConfig()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): OAuth2AsAppGraph
    }
}

fun createOAuth2AsAppGraph(
    application: Any,
    appId: String = "oauth2-as",
    profile: String = "production",
    version: String = "1.0.0",
): OAuth2AsAppGraph {
    val graph =
        createGraphFactory<OAuth2AsAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
