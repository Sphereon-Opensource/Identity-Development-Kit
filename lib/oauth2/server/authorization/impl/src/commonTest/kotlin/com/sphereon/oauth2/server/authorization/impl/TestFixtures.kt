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

package com.sphereon.oauth2.server.authorization.impl

import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Test fixtures for Authorization Server tests
 */
object TestFixtures {
    // Test client registrations
    val confidentialClient =
        ClientRegistration(
            clientId = "confidential-client",
            clientSecret = "secret123",
            clientType = ClientType.CONFIDENTIAL,
            grantTypes =
                listOf(
                    GrantType.AUTHORIZATION_CODE,
                    GrantType.REFRESH_TOKEN,
                    GrantType.CLIENT_CREDENTIALS,
                ),
            redirectUris =
                listOf(
                    "https://client.example.com/callback",
                    "https://client.example.com/callback2",
                ),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            requirePkce = false,
            requirePushedAuthorizationRequests = false,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("read", "write"),
            clientName = "Test Confidential Client",
        )

    val publicClient =
        ClientRegistration(
            clientId = "public-client",
            clientSecret = null,
            clientType = ClientType.PUBLIC,
            grantTypes =
                listOf(
                    GrantType.AUTHORIZATION_CODE,
                    GrantType.REFRESH_TOKEN,
                ),
            redirectUris =
                listOf(
                    "myapp://callback",
                ),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
            requirePkce = true,
            requirePushedAuthorizationRequests = false,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("read"),
            clientName = "Test Public Client",
        )

    val parRequiredClient =
        ClientRegistration(
            clientId = "par-client",
            clientSecret = "par-secret",
            clientType = ClientType.CONFIDENTIAL,
            grantTypes =
                listOf(
                    GrantType.AUTHORIZATION_CODE,
                    GrantType.REFRESH_TOKEN,
                ),
            redirectUris =
                listOf(
                    "https://parclient.example.com/callback",
                ),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST,
            requirePkce = true,
            requirePushedAuthorizationRequests = true,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("read", "write"),
            clientName = "Test PAR Client",
        )

    val tokenExchangeClient =
        ClientRegistration(
            clientId = "token-exchange-client",
            clientSecret = "exchange-secret",
            clientType = ClientType.CONFIDENTIAL,
            grantTypes =
                listOf(
                    GrantType.TOKEN_EXCHANGE,
                ),
            redirectUris = emptyList(),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST,
            requirePkce = false,
            requirePushedAuthorizationRequests = false,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("read", "write"),
            clientName = "Test Token Exchange Client",
        )

    // Test authorization code
    fun createAuthorizationCode(
        code: String = "test-code-123",
        clientId: String = confidentialClient.clientId,
        subject: String = "user123",
        redirectUri: String = confidentialClient.redirectUris.first(),
        scope: String? = "read write",
        codeChallenge: String? = null,
        codeChallengeMethod: PkceMethod? = null,
        dpopJkt: String? = null,
    ): AuthorizationCodeData {
        val now = Clock.System.now()
        return AuthorizationCodeData(
            code = code,
            clientId = clientId,
            subject = subject,
            redirectUri = redirectUri,
            scope = scope,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            dpopJkt = dpopJkt,
            issuedAt = now,
            expiresAt = now + 10.minutes,
            used = false,
            additionalData = emptyMap(),
        )
    }

    // Test access token
    fun createAccessToken(
        accessToken: String = "test-access-token",
        clientId: String = confidentialClient.clientId,
        subject: String = "user123",
        scope: String? = "read write",
        dpopJkt: String? = null,
    ): AccessTokenData {
        val now = Clock.System.now()
        return AccessTokenData(
            accessToken = accessToken,
            tokenType = if (dpopJkt != null) "DPoP" else "Bearer",
            clientId = clientId,
            subject = subject,
            scope = scope,
            audience = listOf("https://api.example.com"),
            issuer = "https://auth.example.com",
            issuedAt = now,
            expiresAt = now + 1.minutes,
            dpopJkt = dpopJkt,
            revoked = false,
            refreshTokenId = null,
            additionalData = emptyMap(),
        )
    }

    // Test refresh token
    fun createRefreshToken(
        refreshToken: String = "test-refresh-token",
        clientId: String = confidentialClient.clientId,
        subject: String = "user123",
        scope: String? = "read write",
        dpopJkt: String? = null,
    ): RefreshTokenData {
        val now = Clock.System.now()
        return RefreshTokenData(
            refreshToken = refreshToken,
            clientId = clientId,
            subject = subject,
            scope = scope,
            issuedAt = now,
            expiresAt = now + 1.minutes,
            dpopJkt = dpopJkt,
            revoked = false,
            used = false,
            additionalData = emptyMap(),
        )
    }

    // Test authorization session
    fun createAuthorizationSession(
        sessionId: String = "session-123",
        clientId: String = confidentialClient.clientId,
        status: SessionStatus = SessionStatus.PENDING_AUTHENTICATION,
    ): AuthorizationSession {
        val now = Clock.System.now()
        return AuthorizationSession(
            sessionId = sessionId,
            clientId = clientId,
            redirectUri = confidentialClient.redirectUris.first(),
            scope = "read write",
            state = "state-xyz",
            responseType = "code",
            codeChallenge = null,
            codeChallengeMethod = null,
            status = status,
            authenticatedUserId = null,
            consentDecision = null,
            createdAt = now,
            expiresAt = now + 15.minutes,
            additionalData = emptyMap(),
        )
    }

    // Test consent decision
    fun createConsentDecision(
        userId: String = "user123",
        clientId: String = confidentialClient.clientId,
        granted: Boolean = true,
    ): ConsentDecision {
        val now = Clock.System.now()
        return ConsentDecision(
            userId = userId,
            clientId = clientId,
            granted = granted,
            grantedScopes = if (granted) listOf("read", "write") else null,
            grantedAt = now,
            rememberConsent = true,
            expiresAt = now + 30.minutes,
            additionalData = emptyMap(),
        )
    }

    // PKCE test values
    object Pkce {
        const val CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        const val CODE_CHALLENGE_S256 = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        const val CODE_CHALLENGE_PLAIN = CODE_VERIFIER
    }

    // DPoP test values
    object DPoP {
        const val JKT = "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I"
    }
}
