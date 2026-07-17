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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.token.VerifiedDeviceCodeGrant
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryDeviceAuthorizationStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Per-handler unit tests for the [com.sphereon.oauth2.server.authorization.command.token.GrantHandler]
 * implementations: each handler exposes its [com.sphereon.oauth2.server.authorization.command.token.GrantHandler.grantType]
 * wire string and answers [com.sphereon.oauth2.server.authorization.command.token.GrantHandler.supports]
 * for exactly the matching [GrantParameters] variant.
 *
 * The deeper happy-path / error-path behaviour is covered by [com.sphereon.oauth2.server.authorization.impl.command.orchestration.HandleTokenRequestCommandImplTest]
 * (orchestrator dispatches into handlers) and the OIDF conformance harness (`tests-oidf-conformance-oidc-op`).
 * These per-handler tests guard the dispatcher contract: `grantType` matches the spec wire string
 * and `supports()` partitions [GrantParameters] without overlap.
 */
class GrantHandlerImplTest {
    private val tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl())
    private val deviceStorage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())

    // The dedicated VerifyDeviceCodeGrantCommandImplTest covers the verify state machine.
    // For supports() / grantType assertions the handler does not call into the verifier.
    private val rejectingDeviceCodeVerify: VerifyDeviceCodeGrantCommand =
        object : VerifyDeviceCodeGrantCommand {
            override val inputTypeToken = typeToken<VerifyDeviceCodeGrantArgs>()
            override val outputTypeToken = typeToken<VerifiedDeviceCodeGrant>()
            override val isEnabled = true

            override suspend fun execute(args: VerifyDeviceCodeGrantArgs) = Err(IdkError.fromString(code = "invalid_grant", message = "test stub"))
        }

    @Test
    fun authorizationCodeHandlerAdvertisesAuthorizationCodeWireString() {
        val handler =
            AuthorizationCodeGrantHandlerImpl(
                authorizationCodeStorage = InMemoryAuthorizationCodeStorageImpl(InMemoryOAuth2BackingStorageImpl()),
                scopeClaimsMapper = null,
            )
        assertEquals("authorization_code", handler.grantType)
        assertTrue(handler.supports(GrantParameters.AuthorizationCode(code = "c", redirectUri = "https://example.com/cb")))
        assertFalse(handler.supports(GrantParameters.RefreshToken(refreshToken = "r")))
        assertFalse(handler.supports(GrantParameters.ClientCredentials()))
        assertFalse(handler.supports(GrantParameters.PreAuthorizedCode(preAuthorizedCode = "p")))
        assertFalse(handler.supports(GrantParameters.TokenExchange(subjectToken = "s", subjectTokenType = "t")))
        assertFalse(handler.supports(GrantParameters.DeviceCode(deviceCode = "d")))
    }

    @Test
    fun refreshTokenHandlerAdvertisesRefreshTokenWireString() {
        val handler =
            RefreshTokenGrantHandlerImpl(
                tokenStorage = tokenStorage,
                auditEmitter = com.sphereon.oauth2.server.authorization.audit.NoOpOAuth2AuditEmitter,
            )
        assertEquals("refresh_token", handler.grantType)
        assertTrue(handler.supports(GrantParameters.RefreshToken(refreshToken = "r")))
        assertFalse(handler.supports(GrantParameters.AuthorizationCode(code = "c", redirectUri = "https://example.com/cb")))
    }

    @Test
    fun clientCredentialsHandlerAdvertisesClientCredentialsWireString() {
        val handler = ClientCredentialsGrantHandlerImpl()
        assertEquals("client_credentials", handler.grantType)
        assertTrue(handler.supports(GrantParameters.ClientCredentials()))
        assertFalse(handler.supports(GrantParameters.RefreshToken(refreshToken = "r")))
    }

    @Test
    fun tokenExchangeHandlerAdvertisesTokenExchangeUrn() {
        val handler = TokenExchangeGrantHandlerImpl()
        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", handler.grantType)
        assertTrue(handler.supports(GrantParameters.TokenExchange(subjectToken = "s", subjectTokenType = "t")))
        assertFalse(handler.supports(GrantParameters.ClientCredentials()))
    }

    @Test
    fun preAuthorizedCodeHandlerAdvertisesPreAuthorizedCodeUrn() {
        val handler = PreAuthorizedCodeGrantHandlerImpl()
        assertEquals("urn:ietf:params:oauth:grant-type:pre-authorized_code", handler.grantType)
        assertTrue(handler.supports(GrantParameters.PreAuthorizedCode(preAuthorizedCode = "p")))
        assertFalse(handler.supports(GrantParameters.AuthorizationCode(code = "c", redirectUri = "https://example.com/cb")))
    }

    @Test
    fun deviceCodeHandlerAdvertisesDeviceCodeUrn() {
        val handler =
            DeviceCodeGrantHandlerImpl(
                verifyDeviceCodeGrantCommand = rejectingDeviceCodeVerify,
                deviceAuthorizationStorage = deviceStorage,
                clock = Clock.System,
            )
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", handler.grantType)
        assertTrue(handler.supports(GrantParameters.DeviceCode(deviceCode = "d")))
        assertFalse(handler.supports(GrantParameters.RefreshToken(refreshToken = "r")))
    }
}
