package com.sphereon.openid.oid4vp.verifier.ktor

import com.sphereon.core.api.log.Log
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
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
    Log.app().withTag("Oid4vpVerifierKtorServer").info("Starting OID4VP Verifier Server")

    val appGraph =
        createOid4vpVerifierAppGraph(
            application = Unit,
            appId = "oid4vp-verifier",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )

    embeddedServer(CIO, port = configuredServerPort(), host = "0.0.0.0") {
        configureOid4vpVerifier(appGraph)
    }.start(wait = true)
}

private fun configuredServerPort(): Int =
    System.getenv("SERVER_PORT")
        ?.toIntOrNull()
        ?.also { require(it in 1..65535) { "SERVER_PORT must be between 1 and 65535" } }
        ?: 8080

/**
 * Configure the Ktor application with OID4VP Verifier + Universal routes.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover all HttpAdapter instances
 * contributed via DI (Oid4vpVerifierHttpAdapter + UniversalOid4vpHttpAdapter).
 */
fun Application.configureOid4vpVerifier(appGraph: AppGraph) {
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
    }

    // Auto-discover and expose all HttpAdapters (verifier + universal)
    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class Oid4vpVerifierAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): Oid4vpVerifierAppGraph
    }
}

fun createOid4vpVerifierAppGraph(
    application: Any,
    appId: String = "oid4vp-verifier",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val graph =
        createGraphFactory<Oid4vpVerifierAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
