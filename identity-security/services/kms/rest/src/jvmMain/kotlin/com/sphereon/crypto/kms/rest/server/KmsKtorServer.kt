package com.sphereon.crypto.kms.rest.server

import com.sphereon.di.app.AppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Installs the per-request DI plumbing a KMS host needs, plus JSON negotiation and a liveness route.
 *
 * This mounts the self-contained KMS REST API. Hosting assemblies remain responsible for
 * authentication and tenant resolution before this function is called.
 *
 * @param appGraph The application's AppGraph. Required: session resolution cannot run without DI.
 *
 * The plugin will automatically:
 * - Create UserContextGraph per tenant/principal
 * - Create SessionGraph per request
 * - Clean up after request completes
 *
 * ```kotlin
 * embeddedServer(CIO, port = 8080) {
 *     configureKms(appGraph)
 *     installUniversalHttpAdapters()
 * }.start(wait = true)
 * ```
 */
fun Application.configureKms(
    appGraph: AppGraph,
    /**
     * Strategy for deriving the session tenant from the request. Defaults to a fixed `default` tenant
     * (standalone/dev IDK use). Enterprise deployments pass a resolver that honors the Layer-1-resolved
     * (host/JWT) tenant — e.g. EDK's `AttributeTenantResolver` — so KMS sessions are per-tenant and the
     * per-tenant keystore/config derivation keys off the real tenant.
     */
    tenantResolver: TenantResolver = FixedTenantResolver("default"),
    /**
     * Whether to install the default StatusPages plugin. Enterprise containers that install their own
     * StatusPages (e.g. a GraalVM-native-safe handler) pass false to avoid a Ktor DuplicatePluginException.
     */
    installStatusPages: Boolean = true,
    /**
     * Whether this self-contained host should mount the generic KMS HTTP surface itself. Composite
     * enterprise hosts set this to false and mount the same adapters after their authenticated,
     * explicitly allow-listed API boundary has been installed.
     */
    installUniversalRoutes: Boolean = true,
) {
    // Install kotlin-inject plugin with AppGraph
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph // ← AppGraph passed to plugin!
        this.tenantResolver = tenantResolver
    }
    log.info("KotlinInject plugin installed - full DI enabled")

    install(ContentNegotiation) {
        json()
    }

    if (installStatusPages) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    text = """{"error": "${cause.message}"}""",
                    status = HttpStatusCode.InternalServerError,
                )
            }
        }
    }

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("OK")
        }
    }
    // KMS is composed from seven path-focused adapters. The former keys-only shortcut left
    // providers, certificates, encryption, signatures and resolvers advertised but unreachable.
    if (installUniversalRoutes) {
        installUniversalHttpAdapters()
    }
}
