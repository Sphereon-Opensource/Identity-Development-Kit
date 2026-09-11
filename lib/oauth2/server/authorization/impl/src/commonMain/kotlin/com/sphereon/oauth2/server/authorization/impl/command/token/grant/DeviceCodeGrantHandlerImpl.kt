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
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.GrantHandlerKeys
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * RFC 8628 device-code grant handler.
 *
 * Verifies the device code through [VerifyDeviceCodeGrantCommand] (which surfaces the RFC 8628
 * §3.5 polling state machine: `authorization_pending`, `slow_down`, `access_denied`,
 * `expired_token`), mints the access / optional refresh / optional id_token triple, then
 * consumes the device-authorization record via [DeviceAuthorizationStorage.consume]. Consumption
 * happens AFTER token minting so a failure mid-issuance leaves the record APPROVED for
 * idempotent retry.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<GrantHandler>())
@StringKey(GrantHandlerKeys.DEVICE_CODE)
class DeviceCodeGrantHandlerImpl(
    private val verifyDeviceCodeGrantCommand: VerifyDeviceCodeGrantCommand,
    private val deviceAuthorizationStorage: DeviceAuthorizationStorage,
    private val clock: Clock,
    private val createAccessToken: CreateAccessTokenCommand,
    private val createRefreshToken: Lazy<CreateRefreshTokenCommand>,
    private val createIdToken: Lazy<CreateIdTokenCommand>,
    private val createTokenResponse: CreateTokenResponseCommand,
    private val clientRegistry: ClientRegistry,
) : GrantHandler {
    override val grantType: String = GrantHandlerKeys.DEVICE_CODE

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.DeviceCode

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256
        val serverConfig = context.serverConfig
        val deviceParams = params as GrantParameters.DeviceCode

        val verified =
            verifyDeviceCodeGrantCommand
                .execute(
                    VerifyDeviceCodeGrantArgs(
                        deviceCode = deviceParams.deviceCode,
                        clientId = tokenRequest.clientId,
                        now = clock.now(),
                    ),
                ).getOrElse { error -> return Err(error) }

        // RFC 9449 §10.1: device-code carries no upfront commitment, so the proof
        // presented at /token establishes the binding for the access token. There is
        // no `dpop_jkt` pinned at /device_authorization (the device opens that
        // request without a UA), so any matching proof binds the token.
        val deviceBoundJkt = proofJkt

        val grantedScope = verified.grantedScope

        // Registered clients may pin a default audience (RFC 8707 derivation order: explicit
        // request audiences win; the registration default fills the gap) and carry issuance
        // metadata that must ride the token as claims. Device-flow enrollment for infrastructure
        // clients such as hand-off screens relies on both: the screen never sends audience or
        // claim parameters itself, so everything derives from its registration.
        val registeredClient = clientRegistry.getClient(tokenRequest.clientId).getOrNull()
        val audience = verified.audience?.takeIf { it.isNotEmpty() }
            ?: registeredClient?.defaultAccessTokenAudience?.let { listOf(it) }
            ?: emptyList()
        val additionalClaims = registeredClient?.additionalMetadata.orEmpty().filterKeys { it in ENROLLMENT_CLAIM_ALLOWLIST }

        val accessToken =
            createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = grantedScope,
                        audience = audience,
                        dpopJkt = deviceBoundJkt,
                        certificateThumbprintS256 = certThumbprint,
                        authTime = verified.authTime?.epochSeconds,
                        baseUrlOverride = applied.baseUrlOverride,
                        additionalClaims = additionalClaims,
                    ),
                ).getOrElse { error -> return Err(error) }

        // OIDC Core 1.0 §12: persist the original authentication context on the
        // refresh-token row so a future refresh-grant can reissue an id_token with
        // auth_time / sid pinned to the verification-UI authentication. Mint a
        // refresh token only when offline_access is in the granted scope, mirroring
        // standard AS behaviour for refresh issuance gating.
        val grantedScopes = grantedScope?.split(" ")?.toSet() ?: emptySet()
        val authTimeEpochSeconds = verified.authTime?.epochSeconds
        val refreshToken =
            if ("offline_access" in grantedScopes) {
                createRefreshToken.value
                    .execute(
                        CreateRefreshTokenArgs(
                            subject = verified.subject,
                            clientId = tokenRequest.clientId,
                            scope = grantedScope,
                            dpopJkt = deviceBoundJkt,
                            authTime = authTimeEpochSeconds,
                            loginSessionId = verified.sessionId,
                        ),
                    ).getOrElse { error -> return Err(error) }
                    .value
            } else {
                null
            }

        val oidcEnabled = serverConfig.oidc.isEnabled
        val idToken =
            if (oidcEnabled && "openid" in grantedScopes) {
                createIdToken.value
                    .execute(
                        CreateIdTokenArgs(
                            subject = verified.subject,
                            clientId = tokenRequest.clientId,
                            authTime = authTimeEpochSeconds,
                            accessToken = accessToken.value,
                            sessionId = verified.sessionId,
                            baseUrlOverride = applied.baseUrlOverride,
                        ),
                    ).getOrElse { error -> return Err(error) }
                    .value
            } else {
                null
            }

        // Mark the record CONSUMED only after token issuance has succeeded. A
        // failure mid-issuance leaves the record APPROVED, letting the device retry
        // without re-running the verification UI; an idempotent retry on the same
        // device_code thus succeeds.
        deviceAuthorizationStorage
            .consume(verified.deviceCode)
            .getOrElse { error ->
                return Err(
                    IdkError.fromDTO(
                        AuthorizationServerError.ServerError(
                            details = "Failed to consume device-authorization record: ${error.details}",
                            exception = null,
                        ),
                    ),
                )
            }

        return createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(deviceBoundJkt),
                refreshToken = refreshToken,
                scope = grantedScope,
                idToken = idToken,
            ),
        )
    }

    private companion object {
        /**
         * Registration metadata keys the device grant may mint into access-token claims. The
         * allowlist keeps arbitrary registration metadata from leaking into tokens; a screen
         * client's `handoff_role` is REQUIRED downstream (fail closed at the resource), so an
         * under-specified registration produces a useless token rather than a dangerous one.
         */
        val ENROLLMENT_CLAIM_ALLOWLIST: Set<String> =
            setOf("handoff_role", "handoff_location", "device_ref", "label")
    }
}
