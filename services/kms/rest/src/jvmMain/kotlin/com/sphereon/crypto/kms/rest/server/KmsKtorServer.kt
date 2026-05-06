package com.sphereon.crypto.kms.rest.server

import com.sphereon.crypto.kms.rest.server.ktor.kmsRouting
import com.sphereon.di.app.AppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
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
 * Configure the Ktor application with KMS routes.
 *
 * @param appGraph Your application's AppGraph (from kotlin-inject).
 *                     REQUIRED - the KMS API cannot function without DI.
 *
 * The plugin will automatically:
 * - Create UserContextGraph per tenant/principal
 * - Create SessionGraph per request
 * - Inject HttpAdapter from SessionScope
 * - Clean up after request completes
 *
 * Example usage - see KmsKtorServerExample.kt:
 * ```kotlin
 * fun main() {
 *     val appGraph = KmsKtorAppGraph.init(
 *         application = Unit,
 *         appId = "kms-api",
 *         profile = "production",
 *         version = "1.0.0"
 *     )
 *
 *     embeddedServer(CIO, port = 8080) {
 *         configureKms(appGraph)  // ← Pass AppGraph here!
 *     }.start(wait = true)
 * }
 * ```
 */
fun Application.configureKms(appGraph: AppGraph) {
    // Install kotlin-inject plugin with AppGraph
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph // ← AppGraph passed to plugin!
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
        // Health check endpoint
        get("/health") {
            call.respondText("OK")
        }

        // KMS routes - uses HttpAdapter from session scope
        kmsRouting()
    }
}
