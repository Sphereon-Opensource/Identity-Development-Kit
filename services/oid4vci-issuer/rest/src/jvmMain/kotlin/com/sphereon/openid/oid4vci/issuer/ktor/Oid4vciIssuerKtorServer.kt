package com.sphereon.openid.oid4vci.issuer.ktor

import com.sphereon.core.api.log.Log
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.statuslist.impl.StatusListProvisioner
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
    val logger = Log.app().withTag("Oid4vciIssuerKtorServer")
    logger.info("Starting OID4VCI Issuer Server")

    val appGraph =
        createOid4vciIssuerAppGraph(
            application = Unit,
            appId = "oid4vci-issuer",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )

    // Create any configured credential status lists before serving, under the same fixed tenant
    // used by the HTTP adapter. File-backed KMS paths are tenant-partitioned, so bootstrapping from
    // an anonymous context would open a different keystore from requests. Provisioning is
    // fail-closed: an issuer that advertises status-backed credentials must not start without them.
    kotlinx.coroutines.runBlocking {
        val sessionManager =
            appGraph.userContextManager
                .createOrGetFromInputs(
                    DefaultTenantInputString("default"),
                    DefaultPrincipalInputString("oid4vci-issuer"),
                ).sessionContextManager
        val sessionId = "statuslist-provisioning"
        val session = sessionManager.createOrGetFromId(
            sessionId,
            principalType = com.sphereon.di.context.PrincipalType.SERVICE,
        )
        try {
            val provisioner = (session.graph as StatusListProvisioner.Graph).statusListProvisioner
            val result = provisioner.provisionConfigured()
            check(!result.isErr) { "Status list provisioning failed: ${result.error}" }
        } finally {
            sessionManager.destroyById(sessionId)
        }
    }

    embeddedServer(CIO, port = configuredServerPort(), host = "0.0.0.0") {
        configureOid4vciIssuer(appGraph)
    }.start(wait = true)
}

private fun configuredServerPort(): Int =
    System.getenv("SERVER_PORT")
        ?.toIntOrNull()
        ?.also { require(it in 1..65535) { "SERVER_PORT must be between 1 and 65535" } }
        ?: 8080

/**
 * Configure the Ktor application with OID4VCI Issuer routes.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover all HttpAdapter instances
 * contributed via DI (Oid4vciIssuerProtocolHttpAdapter + Oid4vciIssuerMetadataHttpAdapter +
 * Oid4vciRestHttpAdapter).
 */
fun Application.configureOid4vciIssuer(appGraph: AppGraph) {
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

    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class Oid4vciIssuerAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): Oid4vciIssuerAppGraph
    }
}

fun createOid4vciIssuerAppGraph(
    application: Any,
    appId: String = "oid4vci-issuer",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val graph =
        createGraphFactory<Oid4vciIssuerAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
