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

/**
 * HTTP endpoint command for completing reconciliation with pre-extracted OIDC claims.
 *
 * POST /auth/oid4vp/sessions/{sessionId}/reconciliation/complete
 *
 * Called by the STS after it exchanges the authorization code for tokens and extracts
 * claims from the upstream IdP. The auth-bridge no longer needs to act as an OIDC RP —
 * it receives the raw claims directly from the STS and creates the identity binding.
 *
 * Request body:
 * ```json
 * {
 *   "claims": { "sub": "...", "given_name": "...", ... },
 *   "issuer": "https://connect.test.surfconext.nl",
 *   "providerId": "surf"
 * }
 * ```
 */
@JsExportCompat
interface CompleteReconciliationWithClaimsCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.auth.reconciliation.complete-with-claims"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{sessionId}/reconciliation/complete",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "completeReconciliationWithClaims",
                commandId = COMMAND_ID,
                tags = setOf("oid4vp-auth-reconciliation"),
                summary = "Complete identity reconciliation with pre-extracted OIDC claims from the STS",
            )
    }
}
