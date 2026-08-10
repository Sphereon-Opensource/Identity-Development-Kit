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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestArgs
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Builds an OID4VCI Authorization Code Flow authorization request URL.
 *
 * Supports both direct redirect and PAR (Pushed Authorization Requests, RFC 9126).
 * Always generates PKCE S256 challenge/verifier pair.
 *
 * Reference: OID4VCI 1.0 Section 5 — Authorization Code Flow
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildAuthorizationRequestCommand>())
class BuildAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val createAuthorizationRequestUrlCommand: CreateAuthorizationRequestUrlCommand,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<BuildAuthorizationRequestArgs, AuthorizationRequestResult, IdkError>(
        commandId = BuildAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationRequestResult>(),
    ),
    BuildAuthorizationRequestCommand {
    override val commandId: String get() = BuildAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildAuthorizationRequestArgs

    override suspend fun doExecute(
        args: BuildAuthorizationRequestArgs,
        applyDuring: (BuildAuthorizationRequestArgs) -> BuildAuthorizationRequestArgs,
    ): IdkResult<AuthorizationRequestResult, IdkError> {
        val applied = applyDuring(args)

        val state = secureRandom.newToken(lengthBytes = STATE_RANDOM_BYTES)
        val authorizationDetails =
            if (applied.credentialConfigurationIds.isEmpty()) {
                emptyMap()
            } else {
                mapOf("authorization_details" to JsonPrimitive(buildAuthorizationDetails(applied)))
            }

        if (applied.usePar && applied.parEndpoint == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "PAR endpoint must be provided when usePar=true"))
        }

        val result =
            createAuthorizationRequestUrlCommand.execute(
                CreateAuthorizationRequestUrlOptions(
                    authorizationServerMetadata =
                        AuthorizationServerMetadata(
                            issuer = applied.authorizationEndpoint.substringBefore("/authorize", applied.authorizationEndpoint),
                            tokenEndpoint = applied.authorizationEndpoint.substringBefore("/authorize", applied.authorizationEndpoint).trimEnd('/') + "/token",
                            authorizationEndpoint = applied.authorizationEndpoint,
                            pushedAuthorizationRequestEndpoint = if (applied.usePar) applied.parEndpoint else null,
                            codeChallengeMethodsSupported = listOf("S256"),
                        ),
                    authorizationRequest =
                        AuthorizationRequest(
                            responseType = "code",
                            clientId = applied.clientId,
                            redirectUri = applied.redirectUri,
                            state = state,
                            scope = applied.scope,
                            issuerState = applied.issuerState,
                            additionalParameters = authorizationDetails,
                        ),
                    clientAuthentication = applied.clientAuthentication,
                    dpopProofJwt = applied.dpopProofJwt,
                    additionalHeaders =
                        if (applied.clientAttestationJwt != null) {
                            mapOf(
                                "OAuth-Client-Attestation" to requireNotNull(applied.clientAttestationJwt),
                                "OAuth-Client-Attestation-PoP" to requireNotNull(applied.clientAttestationPopJwt),
                            )
                        } else {
                            emptyMap()
                        },
                ),
            )
        if (result.isErr) return Err(result.error)
        return Ok(
            AuthorizationRequestResult(
                authorizationUrl = result.value.authorizationRequestUrl,
                codeVerifier = result.value.pkceData?.codeVerifier
                    ?: return Err(IdkError.fromString(code = "OID4VCI_PKCE_MISSING", message = "Shared OAuth authorization request did not produce PKCE data")),
                state = state,
            ),
        )
    }

    /**
     * Builds authorization_details JSON string for the given args.
     *
     * Per OID4VCI 1.0 Section 5.1.1 and RFC 9396:
     * [{"type": "openid_credential", "credential_configuration_id": "<id>", ...}, ...]
     *
     * Includes optional `credential_identifiers` and `locations` when present in args.
     */
    private fun buildAuthorizationDetails(args: BuildAuthorizationRequestArgs): String {
        val array =
            buildJsonArray {
                for (configId in args.credentialConfigurationIds) {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("openid_credential"))
                            put("credential_configuration_id", JsonPrimitive(configId))
                            args.credentialIdentifiers?.get(configId)?.let { ids ->
                                putJsonArray("credential_identifiers") { ids.forEach { add(JsonPrimitive(it)) } }
                            }
                            args.locations?.let { locs ->
                                putJsonArray("locations") { locs.forEach { add(JsonPrimitive(it)) } }
                            }
                        },
                    )
                }
            }
        return Oid4vciJson.lenient.encodeToString(
            kotlinx.serialization.json.JsonArray
                .serializer(),
            array,
        )
    }

    private companion object {
        private const val STATE_RANDOM_BYTES = 32
    }

}
