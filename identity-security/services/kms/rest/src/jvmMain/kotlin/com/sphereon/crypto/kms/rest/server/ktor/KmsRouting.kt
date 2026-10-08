package com.sphereon.crypto.kms.rest.server.ktor

import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.ktor.server.inject.SelectedHttpAdapterRouteAttribute
import com.sphereon.ktor.server.inject.getSessionService
import com.sphereon.ktor.server.inject.markUniversalHttpAdapterRoute
import com.sphereon.ktor.server.inject.http.respondWithGeneric
import com.sphereon.ktor.server.inject.http.toGenericHttpRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * KMS routing module for Ktor Server.
 *
 * AppScope selects requests under /keys before request-scope construction. The selected adapter
 * then performs only defensive identity checks and delegates to the selected endpoint command.
 *
 * The HttpAdapter is automatically resolved from the SessionScope via kotlin-inject.
 * This is the ONLY code needed for Ktor - all business logic is in commonMain!
 */
fun Route.kmsRouting() {
    route("/keys/{...}") {
        markUniversalHttpAdapterRoute(allowedAdapterIds = KMS_ADAPTER_IDS)
        handle {
            val genericRequest = call.request.toGenericHttpRequest(call)
            val selectedRoute = call.attributes[SelectedHttpAdapterRouteAttribute]
            val genericResponse =
                call
                    .getSessionService<HttpAdapterDispatcher>()
                    .dispatch(genericRequest, selectedRoute)
            call.respondWithGeneric(genericResponse)
        }
    }
}

private val KMS_ADAPTER_IDS: Set<String> =
    setOf(
        "KMS-CAPABILITIES",
        "KMS-CERTIFICATES",
        "KMS-ENCRYPTION",
        "KMS-KEYS",
        "KMS-PROVIDERS",
        "KMS-RESOLVERS",
        "KMS-SIGNATURES",
    )
