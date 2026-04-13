package com.sphereon.crypto.kms.rest.server.ktor

import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.ktor.server.inject.getSessionService
import com.sphereon.ktor.server.inject.http.respondWithGeneric
import com.sphereon.ktor.server.inject.http.toGenericHttpRequest
import io.ktor.server.routing.*

/**
 * KMS routing module for Ktor Server.
 *
 * This ultra-thin adapter routes ALL requests under /keys to the Universal HTTP Adapter.
 * The adapter handles method routing, path parsing, and business logic delegation.
 *
 * The HttpAdapter is automatically resolved from the SessionScope via kotlin-inject.
 * This is the ONLY code needed for Ktor - all business logic is in commonMain!
 */
fun Route.kmsRouting() {
    route("/keys/{...}") {
        handle {
            // Get HttpAdapter from session scope (automatically created per request)
            val httpAdapter = call.getSessionService<HttpAdapter>()

            // Convert request, process via adapter, convert response
            val genericRequest = call.request.toGenericHttpRequest(call)
            val genericResponse = httpAdapter.handleRequest(genericRequest)
            call.respondWithGeneric(genericResponse)
        }
    }
}
