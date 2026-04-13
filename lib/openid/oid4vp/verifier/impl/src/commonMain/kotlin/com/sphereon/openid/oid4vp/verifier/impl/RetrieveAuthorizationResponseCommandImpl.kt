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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.verifier.RetrievedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of RetrieveAuthorizationResponseCommand for OpenID4VP RP.
 *
 * OpenID4VP 1.0 Section 14.3.3 - Protection of Authorization Response Data:
 *
 * This command retrieves an authorization response that was stored during
 * direct_post handling, using the response_code from the redirect URI.
 *
 * Flow:
 * 1. RP backend handles direct_post response, generates response_code
 * 2. Wallet redirects user agent to redirect_uri with response_code
 * 3. RP frontend extracts response_code from URL
 * 4. RP frontend calls this command to retrieve the actual response
 * 5. Response code is invalidated (single-use)
 *
 * Security properties:
 * - Response code is single-use (consumed after retrieval)
 * - Response code expires after TTL
 * - VP token never exposed in URLs
 *
 * Error cases:
 * - INVALID_RESPONSE_CODE: Response code not found
 * - EXPIRED_RESPONSE_CODE: Response code has expired
 * - USED_RESPONSE_CODE: Response code has already been used
 *
 * Reference: OpenID4VP 1.0 Section 13.3 and 14.3.3
 */
@Inject
@SingleIn(SessionScope::class)
class RetrieveAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    private val responseCodeStore: ResponseCodeStore,
) : TypedServiceCommandAdapter<RetrieveAuthorizationResponseArgs, RetrievedAuthorizationResponse>(
    commandId = RetrieveAuthorizationResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<RetrieveAuthorizationResponseArgs>(),
    outputTypeToken = typeToken<RetrievedAuthorizationResponse>(),
), RetrieveAuthorizationResponseCommand, RetrieveAuthorizationResponseCommandService {

    override val commandId: String get() = RetrieveAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RetrieveAuthorizationResponseArgs

    private val clock = Clock.System

    override suspend fun retrieveAuthorizationResponse(args: RetrieveAuthorizationResponseArgs): IdkResult<RetrievedAuthorizationResponse, IdkError> {
        return execute(args)
    }

    override suspend fun doExecute(
        args: RetrieveAuthorizationResponseArgs,
        applyDuring: (RetrieveAuthorizationResponseArgs) -> RetrieveAuthorizationResponseArgs
    ): IdkResult<RetrievedAuthorizationResponse, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Retrieving authorization response for response_code")

        // Retrieve the stored response (this will mark it as used if markAsUsed is true)
        val storedResponse = responseCodeStore.retrieve(
            responseCode = processedArgs.responseCode,
            markAsUsed = processedArgs.markAsUsed
        ).getOrElse { error ->
            log.error("Failed to retrieve authorization response: ${error.message}")
            return Err(error)
        }

        val now = clock.now().toEpochMilliseconds()

        log.info("Retrieved authorization response: vpToken queries=${storedResponse.parsedResponse.vpToken.queryIds.size}, " +
            "marked as used=${processedArgs.markAsUsed}")

        return Ok(RetrievedAuthorizationResponse(
            parsedResponse = storedResponse.parsedResponse,
            validationResult = storedResponse.validationResult,
            state = storedResponse.state,
            retrievedAt = now
        ))
    }
}
