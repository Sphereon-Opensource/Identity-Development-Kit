/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vp.auth.http

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

// ============================================================================
// Internal Session API Commands (for OAuth2 integration)
// ============================================================================

/**
 * HTTP endpoint command for creating OID4VP authentication sessions.
 *
 * POST /auth/oid4vp/sessions
 *
 * Creates a new authentication session, returning a QR code for the user's wallet
 * to scan and initiate credential presentation.
 */
@JsExportCompat
interface CreateOid4vpAuthSessionCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.create-session"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createOid4vpAuthSession",
                commandId = COMMAND_ID,
                tags = setOf("oid4vp-auth"),
                summary = "Create a new OID4VP authentication session",
            )
    }
}

/**
 * HTTP endpoint command for polling OID4VP authentication session status.
 *
 * GET /auth/oid4vp/sessions/{sessionId}/status
 *
 * Returns the current status of an authentication session. Used by clients
 * to poll for verification completion after the user scans the QR code.
 */
@JsExportCompat
interface GetOid4vpAuthStatusCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.session.status"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/sessions/{sessionId}/status",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getOid4vpAuthSessionStatus",
                commandId = COMMAND_ID,
                tags = setOf("oid4vp-auth"),
                summary = "Get OID4VP authentication session status",
            )
    }
}

/**
 * HTTP endpoint command for completing OID4VP authentication.
 *
 * POST /auth/oid4vp/sessions/{sessionId}/complete
 *
 * Exchanges a verified authentication session for user identity and claims.
 * Can only be called when the session status is VERIFIED.
 */
@JsExportCompat
interface CompleteOid4vpAuthCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.session.complete"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{sessionId}/complete",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "completeOid4vpAuth",
                commandId = COMMAND_ID,
                tags = setOf("oid4vp-auth"),
                summary = "Complete OID4VP authentication and resolve user identity",
            )
    }
}
