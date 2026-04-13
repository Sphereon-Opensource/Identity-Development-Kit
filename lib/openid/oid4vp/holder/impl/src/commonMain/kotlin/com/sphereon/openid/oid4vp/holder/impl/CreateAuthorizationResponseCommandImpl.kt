/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.service.TypedServiceCommandAdapter

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateAuthorizationResponseCommand for OpenID4VP.
 *
 * Creates an authorization response by:
 * - Matching selected credentials against DCQL query
 * - Building VP tokens in the appropriate format (jwt_vc, ldp_vc, mso_mdoc, etc.)
 * - Applying holder binding if required
 * - Constructing the response parameters
 *
 * Reference: OpenID4VP 1.0 Section 6 - Authorization Response
 *
 * Response format (OpenID4VP 1.0 Final):
 * - vp_token: Self-descriptive credential(s)
 * - state: State from original request (if present)
 * - Note: presentation_submission is NOT used in OID4VP 1.0 Final (DCQL responses are self-descriptive)
 *
 * VP Token formats:
 * - JWT VP: Verifiable Presentation in JWT format
 * - SD-JWT: Selective Disclosure JWT
 * - mDoc/mDL: ISO 18013-5 mobile documents
 * - LDP VP: JSON-LD Verifiable Presentation
 */
@Inject
@SingleIn(SessionScope::class)
class CreateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreateAuthorizationResponseArgs, AuthorizationResponse>(
    commandId = CreateAuthorizationResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateAuthorizationResponseArgs>(),
    outputTypeToken = typeToken<AuthorizationResponse>(),
), CreateAuthorizationResponseCommand, CreateAuthorizationResponseCommandService {

    override val commandId: String get() = CreateAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationResponseArgs

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>
    ): IdkResult<AuthorizationResponse, IdkError> {
        return execute(CreateAuthorizationResponseArgs(request, selectedCredentials))
    }

    override suspend fun doExecute(
        args: CreateAuthorizationResponseArgs,
        applyDuring: (CreateAuthorizationResponseArgs) -> CreateAuthorizationResponseArgs
    ): IdkResult<AuthorizationResponse, IdkError> {
        val processedArgs = applyDuring(args)
        val request = processedArgs.request
        val selectedCredentials = processedArgs.selectedCredentials

        log.debug("Creating authorization response for ${selectedCredentials.size} selected credential(s)")

        // Validate that credentials were provided
        if (selectedCredentials.isEmpty()) {
            log.error("No credentials selected for authorization response")
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "At least one credential must be selected"
            ))
        }

        // Build VP token from selected credentials
        // The SelectedCredential already contains the presentation string (JWT, SD-JWT, or mdoc)
        val vpToken = buildVpToken(selectedCredentials)

        log.info("Created VP token with ${selectedCredentials.size} presentation(s)")

        // Build authorization response using type-safe builder
        // OpenID4VP 1.0 Final: vp_token is self-descriptive, no presentation_submission needed
        val response = buildOid4vpAuthorizationResponse {
            vpToken(vpToken)
            request.request.state?.let { state(it) }
        }

        log.debug("Authorization response created successfully")
        return Ok(response)
    }

    /**
     * Build VP token from selected credentials.
     *
     * OpenID4VP 1.0 Final Section 6.4 (DCQL Format):
     * - vp_token is a JSON object where keys are credential query IDs
     * - Values are single presentation strings or arrays of strings
     *
     * Example:
     * ```json
     * {
     *   "driver_license_query": "eyJhbGc...",
     *   "employment_query": ["eyJhbGc...", "eyJhbGc..."]
     * }
     * ```
     *
     * Note: SelectedCredential.credentialQueryId maps the presentation to its
     * DCQL credential query. Multiple credentials can satisfy the same query.
     */
    private fun buildVpToken(credentials: List<SelectedCredential>): VpToken {
        // Group presentations by credential query ID
        val grouped = credentials
            .groupBy { it.credentialQueryId }
            .mapValues { (_, credentialList) ->
                credentialList.map { it.presentation }
            }
        
        return VpToken(grouped)
    }
}
