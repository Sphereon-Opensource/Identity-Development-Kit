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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.verifier.MatchedCredential
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.sdjwt.SdJwtCodec
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Implementation of ValidateAuthorizationResponseCommand for OpenID4VP RP (Verifier).
 *
 * Validates authorization responses against the original DCQL query:
 * - Validates state parameter matches original request
 * - Validates VP token format matches requested formats
 * - Validates credentials satisfy DCQL query requirements
 * - Validates required claims are present
 *
 * OpenID4VP 1.0 Final:
 * - VP tokens are self-descriptive (format is determined from the token itself)
 * - NO presentation_submission validation (that's DIF PE, not used in OID4VP 1.0 Final)
 *
 * Reference: OpenID4VP 1.0 Final Section 7 - Verifiable Presentation Validation
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<ValidateAuthorizationResponseArgs, ValidationResult>(
        commandId = ValidateAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<ValidationResult>(),
    ),
    ValidateAuthorizationResponseCommand,
    ValidateAuthorizationResponseCommandService {
    override val commandId: String get() = ValidateAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateAuthorizationResponseArgs

    override suspend fun validateAuthorizationResponse(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> = execute(args)

    override suspend fun doExecute(
        args: ValidateAuthorizationResponseArgs,
        applyDuring: (ValidateAuthorizationResponseArgs) -> ValidateAuthorizationResponseArgs,
    ): IdkResult<ValidationResult, IdkError> {
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(result)
        return result
    }

    private suspend fun emitOutcome(result: IdkResult<ValidationResult, IdkError>,) {
        val isValid = result.getOrNull()?.valid == true
        val type = if (isValid) EventTypes.OID4VP_RESPONSE_VERIFIED else EventTypes.OID4VP_RESPONSE_FAILED
        val category = if (isValid) EventCategories.SECURITY else EventCategories.ERROR
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OID4VP)
                .category(category)
                .origin(ValidateAuthorizationResponseCommand.COMMAND_ID)
                .payload(buildJsonObject { put("isValid", isValid) })
                .build(),
        )
    }

    private suspend fun doExecuteInternal(
        args: ValidateAuthorizationResponseArgs,
        applyDuring: (ValidateAuthorizationResponseArgs) -> ValidateAuthorizationResponseArgs,
    ): IdkResult<ValidationResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Validating authorization response against DCQL query")

        val parsedResponse = processedArgs.parsedResponse
        val originalRequest = processedArgs.originalRequest
        val dcqlQuery = processedArgs.dcqlQuery
        val expectedNonce = processedArgs.expectedNonce

        val errors = mutableListOf<String>()
        val matchedCredentials = mutableListOf<MatchedCredential>()

        // Validate state parameter
        if (originalRequest.state != null && parsedResponse.state != originalRequest.state) {
            errors.add("State mismatch: expected '${originalRequest.state}', got '${parsedResponse.state}'")
        }

        // Validate each presentation in the VP token (DCQL format: Map of queryId -> presentations)
        val vpToken = parsedResponse.vpToken

        for ((queryId, presentationList) in vpToken.presentations) {
            // Validate the credential query ID exists in the DCQL query
            val credentialQueries = dcqlQuery.credentials ?: emptyList()
            val matchingQuery = credentialQueries.find { it.id == queryId }

            if (matchingQuery == null) {
                errors.add("Credential query ID '$queryId' from vp_token not found in DCQL query")
                continue
            }

            // Validate each presentation for this query ID
            for ((presentationIndex, presentation) in presentationList.withIndex()) {
                val detectedFormat = CredentialFormat.detectFormat(presentation)

                if (detectedFormat == null) {
                    errors.add("Could not determine format of presentation for query '$queryId' at index $presentationIndex")
                    continue
                }

                // Validate format matches query if specified
                val queryFormat = matchingQuery.format
                val formatMatches =
                    queryFormat == null ||
                        queryFormat == detectedFormat.value ||
                        CredentialFormat.fromValueLenient(queryFormat) == detectedFormat

                if (!formatMatches) {
                    errors.add("Presentation format '${detectedFormat.value}' for query '$queryId' does not match required format '$queryFormat'")
                    continue
                }

                // Extract disclosed claims from the presentation
                val disclosedClaims = extractDisclosedClaims(presentation, detectedFormat)

                matchedCredentials.add(
                    MatchedCredential(
                        credentialQueryId = queryId,
                        format = detectedFormat.value,
                        presentation = presentation,
                        disclosedClaims = disclosedClaims,
                    ),
                )
            }
        }

        // Validate all required credentials are present
        val requiredCredentials =
            dcqlQuery.credentials?.filter { query ->
                // If no credential_sets, all credentials are required
                dcqlQuery.credential_sets.isNullOrEmpty()
            } ?: emptyList()

        for (required in requiredCredentials) {
            if (matchedCredentials.none { it.credentialQueryId == required.id }) {
                errors.add("Required credential '${required.id}' not found in response")
            }
        }

        // Validate credential_sets if present (OR logic)
        dcqlQuery.credential_sets?.filter { it.required }?.forEach { credentialSet ->
            val anySatisfied =
                credentialSet.options.any { option ->
                    option.credential_ids.all { credId ->
                        matchedCredentials.any { it.credentialQueryId == credId }
                    }
                }
            if (!anySatisfied) {
                errors.add("No option in required credential_set satisfied")
            }
        }

        val valid = errors.isEmpty()

        log.info("Validation result: valid=$valid, matched=${matchedCredentials.size}, errors=${errors.size}")

        val result =
            ValidationResult(
                valid = valid,
                matchedCredentials = matchedCredentials,
                errors = errors,
            )

        // Best-effort session update using state as session correlation key.
        val correlationId = originalRequest.state
        if (!correlationId.isNullOrBlank()) {
            authorizationSessionStore.storeValidationResult(correlationId = correlationId, validationResult = result).fold(
                success = { },
                failure = { e -> log.warn("Failed to update authorization session with validation result: ${e.message.defaultMessage}") },
            )
        }

        return Ok(result)
    }

    /**
     * Extract disclosed claims from a credential presentation.
     * For SD-JWT: parses the compact serialization and resolves all disclosures.
     */
    private fun extractDisclosedClaims(
        presentation: String,
        format: CredentialFormat,
    ): Map<String, Any?> =
        when (format) {
            CredentialFormat.SD_JWT_DC, CredentialFormat.SD_JWT_VC -> {
                SdJwtCodec.parse(presentation).fold(
                    success = { sdJwt ->
                        // fullPayload has all disclosures resolved into a JsonObject
                        val payload = sdJwt.payload.fullPayload
                        // Filter out SD-JWT internal fields and unwrap JsonPrimitive to native types
                        val internalKeys = setOf("_sd", "_sd_alg", "cnf", "iss", "iat", "exp", "nbf", "vct", "status")
                        payload.filterKeys { it !in internalKeys }.mapValues { (_, element) ->
                            when (element) {
                                is JsonPrimitive -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.contentOrNull
                                else -> element.toString()
                            }
                        }
                    },
                    failure = { error ->
                        log.warn("Failed to parse SD-JWT presentation for claim extraction: ${error.message}")
                        emptyMap()
                    },
                )
            }

            else -> {
                log.debug("Claim extraction not implemented for format: ${format.value}")
                emptyMap()
            }
        }
}
