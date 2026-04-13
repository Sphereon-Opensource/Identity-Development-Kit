/*
 * Copyright 2025 Sphereon International B.V.
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

/**
 * HTTP endpoint command for initiating identity verification (reconciliation) for an OID4VP session.
 *
 * POST /auth/oid4vp/sessions/{sessionId}/idv/initiate
 *
 * Creates a reconciliation session and returns an OIDC authorization URL for the user
 * to verify their institutional identity.
 */
interface InitiateOid4vpIdvCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.idv.initiate"

        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/sessions/{sessionId}/idv/initiate",
            consumes = setOf(MediaType.ApplicationJson),
            produces = setOf(MediaType.ApplicationJson),
            operationId = "initiateOid4vpIdv",
            commandId = COMMAND_ID,
            tags = setOf("oid4vp-auth-idv"),
            summary = "Initiate identity verification for an OID4VP authentication session"
        )
    }
}

/**
 * HTTP endpoint command for checking identity verification status.
 *
 * GET /auth/oid4vp/sessions/{sessionId}/idv/status
 *
 * Returns the current status of the reconciliation flow for the session.
 */
interface GetOid4vpIdvStatusCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.idv.status"

        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/sessions/{sessionId}/idv/status",
            produces = setOf(MediaType.ApplicationJson),
            operationId = "getOid4vpIdvStatus",
            commandId = COMMAND_ID,
            tags = setOf("oid4vp-auth-idv"),
            summary = "Get identity verification status for an OID4VP session"
        )
    }
}

/**
 * HTTP endpoint command for handling the OIDC callback from the reconciliation provider.
 *
 * GET /auth/oid4vp/idv/callback?code=...&state=...
 *
 * Receives the authorization code from the upstream OIDC provider (e.g., SURF),
 * finds the OID4VP session via the reconciliation state parameter,
 * completes the reconciliation, and redirects the user back to the frontend.
 *
 * The OID4VP session is resolved from the OIDC `state` parameter via the reconciliation
 * session store, so no session ID is needed in the path. This allows using a fixed
 * redirect URI with Keycloak.
 */
interface HandleOid4vpIdvCallbackCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.idv.callback"

        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/idv/callback",
            produces = setOf(MediaType.Custom("text/html")),
            operationId = "handleOid4vpIdvCallback",
            commandId = COMMAND_ID,
            tags = setOf("oid4vp-auth-idv"),
            summary = "Handle OIDC callback for OID4VP identity verification"
        )
    }
}
