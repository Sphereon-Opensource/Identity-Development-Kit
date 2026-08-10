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
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
@ContributesIntoSet(SessionScope::class, binding = binding<GrantHandler>())
class DeviceCodeGrantHandlerImpl(
    private val verifyDeviceCodeGrantCommand: VerifyDeviceCodeGrantCommand,
    private val deviceAuthorizationStorage: DeviceAuthorizationStorage,
    private val clock: Clock,
) : GrantHandler {
    override val grantType: String = GrantType.DEVICE_CODE.value

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.DeviceCode

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val commands = context.commands
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

        val accessToken =
            commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = grantedScope,
                        audience = verified.audience ?: emptyList(),
                        dpopJkt = deviceBoundJkt,
                        certificateThumbprintS256 = certThumbprint,
                        authTime = verified.authTime?.epochSeconds,
                        baseUrlOverride = applied.baseUrlOverride,
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
                commands.createRefreshToken
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
                commands.createIdToken
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

        return commands.createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(deviceBoundJkt),
                refreshToken = refreshToken,
                scope = grantedScope,
                idToken = idToken,
            ),
        )
    }
}
