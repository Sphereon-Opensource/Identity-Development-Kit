package com.sphereon.crypto.kms.rest.server

import com.sphereon.crypto.kms.rest.server.ktor.kmsRouting
import com.sphereon.di.app.AppComponent
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.sessionInstance
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configure the Ktor application with KMS routes.
 *
 * @param appComponent Your application's AppComponent (from kotlin-inject).
 *                     REQUIRED - the KMS API cannot function without DI.
 *
 * The plugin will automatically:
 * - Create UserContextComponent per tenant/principal
 * - Create SessionComponent per request
 * - Inject HttpAdapter from SessionScope
 * - Clean up after request completes
 *
 * Example usage - see KmsKtorServerExample.kt:
 * ```kotlin
 * fun main() {
 *     val appComponent = KmsKtorAppComponent.init(
 *         application = Unit,
 *         appId = "kms-api",
 *         profile = "production",
 *         version = "1.0.0"
 *     )
 *
 *     embeddedServer(CIO, port = 8080) {
 *         configureKms(appComponent)  // ← Pass AppComponent here!
 *     }.start(wait = true)
 * }
 * ```
 */
fun Application.configureKms(appComponent: AppComponent) {
    // Install kotlin-inject plugin with AppComponent
    install(KotlinInjectPlugin) {
        this.appComponent = appComponent  // ← AppComponent passed to plugin!
    }
    log.info("KotlinInject plugin installed - full DI enabled")

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = """{"error": "${cause.message}"}""",
                status = io.ktor.http.HttpStatusCode.InternalServerError
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
