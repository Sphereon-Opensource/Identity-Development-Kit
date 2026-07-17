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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.wallet.accessTokenClaims
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * OID4VCI 1.1 §6 pre-authorized-code grant handler.
 *
 * Verifies the pre-authorized code (and optional `tx_code`), mints an access token bound to the
 * issuer audience, and emits the optional `authorization_details` array carrying
 * `credential_identifiers` so the wallet knows which credential identifier to send on the
 * subsequent `/credential` call.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<GrantHandler>())
class PreAuthorizedCodeGrantHandlerImpl : GrantHandler {
    override val grantType: String = GrantType.PRE_AUTHORIZED_CODE.value

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.PreAuthorizedCode

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val paParams = params as GrantParameters.PreAuthorizedCode
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val commands = context.commands
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256

        val verified =
            commands.verifyPreAuthorizedCodeGrant
                .execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = paParams.preAuthorizedCode,
                        txCode = paParams.txCode,
                        clientId = tokenRequest.clientId,
                    ),
                ).getOrElse { error -> return Err(error) }

        // RFC 9449 §10.1 (and OID4VCI 1.0 §6.1 wallet-attestation guidance): the
        // pre-authorized-code grant carries no upfront commitment in this build, so
        // the proof presented at /token establishes the binding for the access token.
        val authorizationDetails =
            if (verified.useCredentialIdentifiers && verified.credentialConfigurationIds.isNotEmpty()) {
                JsonArray(
                    verified.credentialConfigurationIds.map { configId ->
                        buildJsonObject {
                            put("type", JsonPrimitive("openid_credential"))
                            put("credential_configuration_id", JsonPrimitive(configId))
                            putJsonArray("credential_identifiers") {
                                add(JsonPrimitive(verified.sessionId))
                            }
                        }
                    },
                )
            } else {
                null
            }

        val accessTokenClaims =
            buildMap<String, Any> {
                putAll(context.walletInstanceAttestation?.accessTokenClaims().orEmpty())
                authorizationDetails?.let { put("authorization_details", it) }
            }
        val accessToken =
            commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject ?: tokenRequest.clientId,
                        clientId = tokenRequest.clientId,
                        audience = listOfNotNull(verified.issuerIdentifier),
                        dpopJkt = proofJkt,
                        certificateThumbprintS256 = certThumbprint,
                        additionalClaims = accessTokenClaims,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        return commands.createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(proofJkt),
                authorizationDetails = authorizationDetails,
            ),
        )
    }
}
