/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VerifiedClientAuthorizationBoundaryTest {
    @Test
    fun publicTypedCommandArgumentsCannotCarryTrustedClientAuthorization() {
        val publicArgs =
            listOf(
                VerifyClientAuthenticationArgs(
                    clientAuthentication = ClientAuthenticationConfig.Anonymous,
                    clientId = "client",
                    tokenEndpointUrl = "https://issuer.example/token",
                ),
                VerifyClientCredentialsGrantArgs(clientId = "client"),
                VerifyAuthorizationCodeGrantArgs(code = "code", redirectUri = "https://client.example/cb", clientId = "client"),
                VerifyTokenExchangeGrantArgs(
                    subjectToken = "token",
                    subjectTokenType = "urn:ietf:params:oauth:token-type:access_token",
                    clientId = "client",
                ),
            )

        publicArgs.forEach { args ->
            assertFalse(args.toString().contains("clientAuthorization"))
        }
    }

    @Test
    fun commandSafeClientAuthorizationRoundTripsWithoutCredentialFields() {
        val authorization =
            VerifiedClientAuthorization(
                clientId = "operator-client",
                grantTypes = listOf(GrantType.CLIENT_CREDENTIALS, GrantType.TOKEN_EXCHANGE),
                allowedScopes = listOf("internal"),
                defaultAccessTokenAudience = "enterprise-platform",
                allowedAccessTokenAudiences = setOf("enterprise-tenant-kms"),
                requirePkce = false,
                tlsClientCertificateBoundAccessTokens = true,
                tenantId = "tenant-acme",
            )

        val encoded = Json.encodeToString(authorization)
        val decoded = Json.decodeFromString<VerifiedClientAuthorization>(encoded)

        assertEquals(authorization, decoded)
        assertFalse(encoded.contains("clientSecret", ignoreCase = true))
        assertFalse(encoded.contains("secretRef", ignoreCase = true))
        assertFalse(encoded.contains("privateKey", ignoreCase = true))
        assertFalse(encoded.contains("jwks", ignoreCase = true))
        assertFalse(encoded.contains("redirectUri", ignoreCase = true))
    }
}
