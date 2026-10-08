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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.reconciliation.api.OidcConnectionResolver
import com.sphereon.identity.reconciliation.api.ReconciliationOidcClient
import com.sphereon.identity.reconciliation.api.ReconciliationOidcEndpoints
import com.sphereon.identity.reconciliation.api.ReconciliationPkceMaterial
import com.sphereon.identity.reconciliation.api.ReconciliationTokenExchangeRequest
import com.sphereon.identity.reconciliation.api.ReconciliationTokenExchangeResult
import com.sphereon.identity.reconciliation.api.ReconciliationUserInfoResult
import com.sphereon.identity.reconciliation.api.ResolvedOidcConnection
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Stub [ReconciliationOidcClient] for reconciliation unit tests (no protocols deps).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ReconciliationOidcClient>())
class StubReconciliationOidcClient : ReconciliationOidcClient {
    override suspend fun createPkce(): IdkResult<ReconciliationPkceMaterial, IdkError> =
        Ok(
            ReconciliationPkceMaterial(
                codeVerifier = "a".repeat(64),
                codeChallenge = "b".repeat(43),
                codeChallengeMethod = "S256",
            ),
        )

    override suspend fun discoverEndpoints(issuer: String): ReconciliationOidcEndpoints {
        val normalizedIssuer = issuer.trimEnd('/')
        return ReconciliationOidcEndpoints(
            authorizationEndpoint = "$normalizedIssuer/authorize",
            tokenEndpoint = "$normalizedIssuer/token",
            userinfoEndpoint = "$normalizedIssuer/userinfo",
        )
    }

    override suspend fun exchangeAuthorizationCode(
        request: ReconciliationTokenExchangeRequest,
    ): IdkResult<ReconciliationTokenExchangeResult, IdkError> =
        Ok(
            ReconciliationTokenExchangeResult(
                accessToken = "stub-access-token",
                idToken = "stub.header.payload",
            ),
        )

    override suspend fun fetchUserInfo(
        accessToken: String,
        userinfoEndpoint: String,
    ): IdkResult<ReconciliationUserInfoResult, IdkError> =
        Ok(
            ReconciliationUserInfoResult(
                sub = "external-user-123",
                claims =
                    mapOf(
                        "email" to JsonPrimitive("user@example.com"),
                        "name" to JsonPrimitive("Test User"),
                        "eduid" to JsonPrimitive("urn:mace:eduid.nl:1.0:d57b4355-c7c6-4924-869f-0e3229e"),
                        "schac_home_organization" to JsonPrimitive("example-university.nl"),
                    ),
            ),
        )

    override fun extractIdTokenClaims(idToken: String): Map<String, JsonElement> =
        mapOf(
            "iss" to JsonPrimitive("https://idp.example.com"),
            "sub" to JsonPrimitive("external-user-123"),
            "aud" to JsonPrimitive("test-client-id"),
            "email" to JsonPrimitive("user@example.com"),
            "name" to JsonPrimitive("Test User"),
        )
}

@ContributesTo(SessionScope::class)
interface TestOidcConnectionResolverModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOidcConnectionResolver(): OidcConnectionResolver =
        object : OidcConnectionResolver {
            override suspend fun resolve(oidcClientId: String): ResolvedOidcConnection =
                ResolvedOidcConnection(
                    discoveryUrl = "https://idp.example.com/.well-known/openid-configuration",
                    clientId = "test-client-id",
                    clientSecret = "test-client-secret",
                    scopes = listOf("openid", "profile", "email"),
                    userInfoEnabled = oidcClientId.contains("userinfo"),
                )
        }
}

@ContributesTo(SessionScope::class, replaces = [com.sphereon.identity.matching.impl.crypto.ReconciliationCryptoModule::class])
interface TestReconciliationCryptoModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideReconciliationCryptoService(): ReconciliationCryptoService = TestReconciliationCryptoService()
}

private class TestReconciliationCryptoService : ReconciliationCryptoService {
    override suspend fun hashHolderKey(holderKey: String): HashedIdentifier = HashedIdentifier(hash = "holder:$holderKey", keyVersion = "v1")

    override suspend fun hashExternalIdentifier(identifier: String): HashedIdentifier = HashedIdentifier(hash = "ext:$identifier", keyVersion = "v1")

    override suspend fun encrypt(plaintext: String): EncryptedPayload = EncryptedPayload(ciphertext = plaintext, keyVersion = "v1")

    override suspend fun decrypt(payload: EncryptedPayload): String = payload.ciphertext

    override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? = null

    override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? = null
}
