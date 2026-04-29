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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.validate
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.JarSignerMethod
import com.sphereon.openid.oid4vp.common.ParseClientIdArgs
import com.sphereon.openid.oid4vp.common.ParseTransactionDataArgs
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.ParsedTransactionDataEntry
import com.sphereon.openid.oid4vp.common.ResolveScopeArgs
import com.sphereon.openid.oid4vp.common.ResolveScopeCommand
import com.sphereon.openid.oid4vp.common.ValidateClientIdArgs
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommand
import com.sphereon.openid.oid4vp.common.VerifierAttestation
import com.sphereon.openid.oid4vp.common.verifierInfo
import com.sphereon.openid.oid4vp.dcql.DcqlError
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.validateDcqlQuery
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommandService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Inject
@SingleIn(SessionScope::class)
class ResolveAuthorizationRequestCommandImpl(
    private val parseClientIdCommand: com.sphereon.openid.oid4vp.common.ParseClientIdCommand,
    private val validateClientIdCommand: ValidateClientIdCommand,
    private val resolveClientMetadataCommand: com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommand,
    private val parseTransactionDataCommand: ParseTransactionDataCommand,
    private val resolveScopeCommand: ResolveScopeCommand? = null,
    execution: SessionExecution,
) : TypedServiceCommandAdapter<AuthorizationRequest, ResolvedOid4vpRequest>(
        commandId = ResolveAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AuthorizationRequest>(),
        outputTypeToken = typeToken<ResolvedOid4vpRequest>(),
    ),
    ResolveAuthorizationRequestCommand,
    ResolveAuthorizationRequestCommandService {
    override val commandId: String get() = ResolveAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is AuthorizationRequest

    override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = execute(request)

    override suspend fun doExecute(
        args: AuthorizationRequest,
        applyDuring: (AuthorizationRequest) -> AuthorizationRequest,
    ): IdkResult<ResolvedOid4vpRequest, IdkError> {
        val processedArgs = applyDuring(args)

        // 1. Parse DCQL query - either from dcql_query parameter or resolved from scope
        // Per OpenID4VP 1.0 Section 5.5: Either dcql_query or scope representing a DCQL Query
        // MUST be present, but not both.
        val dcqlQueryFromParam =
            processedArgs.additionalParameters["dcql_query"]?.let { dcqlJsonElement ->
                try {
                    // `dcql_query` may arrive either as:
                    // - a JSON object (when coming from a parsed Request Object/JAR)
                    // - a JSON string containing the JSON object (when passed/merged via query parameters)
                    val normalizedElement: JsonElement =
                        when (dcqlJsonElement) {
                            is JsonPrimitive -> {
                                val raw = dcqlJsonElement.contentOrNull?.trim()
                                if (raw.isNullOrBlank()) {
                                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to parse DCQL query: empty dcql_query"))
                                }
                                Json.parseToJsonElement(raw)
                            }

                            else -> {
                                dcqlJsonElement
                            }
                        }

                    val parsedQuery = Json.decodeFromJsonElement(DcqlQuery.serializer(), normalizedElement)

                    // Validate the parsed DCQL query - use fold
                    validate(validateDcqlQuery, parsedQuery) { DcqlError.ValidationError(errors = it) }.fold(
                        success = { parsedQuery },
                        failure = { error ->
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Invalid DCQL query: ${error.errors.joinToString { it.message }}",
                                ),
                            )
                        },
                    )
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse DCQL query: ${expected.message}",
                        ),
                    )
                }
            }

        // 1b. If no dcql_query, try to resolve from scope (OpenID4VP 1.0 Section 5.5)
        val dcqlQueryFromScope =
            if (dcqlQueryFromParam == null && resolveScopeCommand != null && !processedArgs.scope.isNullOrBlank()) {
                val scopeResult =
                    resolveScopeCommand
                        .execute(ResolveScopeArgs(processedArgs.scope))
                        .getOrElse { return it.asErrorResult() }

                // If scope resolved to a DCQL query, use it
                scopeResult.dcqlQuery?.let { resolvedQuery ->
                    // Validate the resolved DCQL query
                    validate(validateDcqlQuery, resolvedQuery) { DcqlError.ValidationError(errors = it) }.fold(
                        success = { resolvedQuery },
                        failure = { error ->
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Invalid DCQL query from scope resolution: ${error.errors.joinToString { it.message }}",
                                ),
                            )
                        },
                    )
                }
            } else {
                null
            }

        // Check that both dcql_query and scope-referencing-DCQL are not present
        // Per OpenID4VP 1.0 Section 5.1: "Either a dcql_query or a scope parameter
        // representing a DCQL Query MUST be present in the Authorization Request, but not both."
        if (dcqlQueryFromParam != null && dcqlQueryFromScope != null) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Both dcql_query and scope referencing a DCQL query are present. Only one is allowed.",
                ),
            )
        }

        // Use whichever DCQL query was provided
        val dcqlQuery = dcqlQueryFromParam ?: dcqlQueryFromScope

        // 2. Parse verifier attestations if present (OpenID4VP 1.0 Section 5.1.1)
        val verifierAttestations: List<VerifierAttestation>? = processedArgs.verifierInfo

        // 3. Parse transaction data if present (OpenID4VP 1.0 Section 5.1.2)
        val transactionData: List<ParsedTransactionDataEntry>? =
            processedArgs.additionalParameters["transaction_data"]?.let { transactionDataElement ->
                try {
                    // transaction_data is an array of base64url-encoded JSON strings
                    val encodedEntries = transactionDataElement.jsonArray.map { it.jsonPrimitive.content }

                    parseTransactionDataCommand
                        .execute(ParseTransactionDataArgs(encodedEntries))
                        .getOrElse { error ->
                            return Err(error)
                        }.entries
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse transaction_data: ${expected.message}",
                        ),
                    )
                }
            }

        // 4. Resolve client metadata
        val resolvedMetadata =
            resolveClientMetadataCommand
                .execute(processedArgs)
                .getOrElse { return it.asErrorResult() }

        // 5. Parse client_id to determine scheme
        val parsedClientId =
            parseClientIdCommand
                .execute(ParseClientIdArgs(processedArgs.clientId))
                .getOrElse { return it.asErrorResult() }

        // 6. Validate client_id according to its scheme
        // If a Request Object (JAR) was used, propagate parsed header context into client_id validation
        val jarUsed =
            processedArgs.additionalParameters["__idk_jar_used"]?.jsonPrimitive?.booleanOrNull
                ?: (processedArgs.request != null || processedArgs.requestUri != null)
        val jarKid = processedArgs.additionalParameters["__idk_jar_kid"]?.jsonPrimitive?.contentOrNull
        val jarTyp = processedArgs.additionalParameters["__idk_jar_typ"]?.jsonPrimitive?.contentOrNull
        val jarX5c =
            processedArgs.additionalParameters["__idk_jar_x5c"]?.let { el ->
                (el as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
            }
        val jarSignerMethod =
            if (jarUsed) {
                JarSignerMethod.detect(x5c = jarX5c, jwk = null, kid = jarKid)
            } else {
                null
            }
        val jarSignerCertificates =
            jarX5c
                ?.mapNotNull { derOrPem ->
                    try {
                        certificateFromPem(x509DerOrPemToPem(derOrPem))
                    } catch (expected: Exception) {
                        log.debug("Failed to parse JAR signer certificate: ${expected.message}")
                        null
                    }
                }?.takeIf { it.isNotEmpty() }

        val validationArgs =
            ValidateClientIdArgs(
                parsedClientId = parsedClientId,
                jarUsed = jarUsed,
                jarSignerMethod = jarSignerMethod,
                jarTypHeader = jarTyp,
                jarSignerKid = jarKid,
                jarSignerCertificates = jarSignerCertificates,
                redirectUri = processedArgs.redirectUri,
                responseUri =
                    processedArgs.additionalParameters["response_uri"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) {
                            it.content
                        } else {
                            null
                        }
                    },
            )

        val validationResult =
            validateClientIdCommand
                .execute(validationArgs)
                .getOrElse { return it.asErrorResult() }

        // 7. Build verifier info with validation results
        val verifierInfoResult =
            VerifierInfo(
                clientId = processedArgs.clientId,
                clientIdScheme = parsedClientId.clientIdScheme,
                clientIdValid = validationResult.valid,
                clientIdValidationErrors = validationResult.errors,
                displayName = resolvedMetadata.metadata?.clientName,
                logoUri = resolvedMetadata.metadata?.logoUri,
                trustRoot = null, // TODO: Will come from identifier resolution trust establishment
            )

        // 8. Build resolved request
        return Ok(
            ResolvedOid4vpRequest(
                request = processedArgs,
                dcqlQuery = dcqlQuery,
                clientMetadata = resolvedMetadata.metadata,
                verifierInfo = verifierInfoResult,
                transactionData = transactionData,
                verifierAttestations = verifierAttestations,
            ),
        )
    }
}
