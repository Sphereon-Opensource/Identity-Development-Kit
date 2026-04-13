package com.sphereon.openid.oid4vci.holder.ktor

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
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
    println("Starting OID4VCI Holder Server...")

    val appGraph =
        createOid4vciHolderAppGraph(
            application = Unit,
            appId = "oid4vci-holder",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureOid4vciHolder(appGraph)
    }.start(wait = true)
}

/**
 * Configure the Ktor application with OID4VCI Holder routes.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover all HttpAdapter instances
 * contributed via DI (Oid4vciHolderHttpAdapter).
 */
fun Application.configureOid4vciHolder(appGraph: AppGraph) {
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
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

    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class Oid4vciHolderAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): Oid4vciHolderAppGraph
    }
}

fun createOid4vciHolderAppGraph(
    application: Any,
    appId: String = "oid4vci-holder",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val graph =
        createGraphFactory<Oid4vciHolderAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
