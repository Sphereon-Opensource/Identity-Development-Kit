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

package com.sphereon.oauth2.server.authorization.impl.command.oidc

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapperImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class GetUserInfoCommandImplTest {
    private val ctx = OAuth2ServerTestContext("userinfo-test", this)

    /** Parameterised user-auth stub so each test can supply a tailored [UserInfo]. */
    private class FixedUserAuthenticationProvider(
        private val userInfo: UserInfo
    ) : TestUserAuthenticationProvider() {
        override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Ok(userInfo)
    }

    private fun commandWith(userInfo: UserInfo,): Pair<GetUserInfoCommandImpl, InMemoryTokenStorageImpl> {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val command =
            GetUserInfoCommandImpl(
                execution = ctx.execution,
                tokenStorage = tokenStorage,
                userAuthenticationProvider = FixedUserAuthenticationProvider(userInfo),
                scopeClaimsMapper = OidcScopeClaimsMapperImpl(),
            )
        return command to tokenStorage
    }

    private suspend fun seedToken(
        tokenStorage: InMemoryTokenStorageImpl,
        token: String = "access-token-abc",
        subject: String = "user-1",
        scope: String = "openid",
    ) {
        val now = Clock.System.now()
        val data =
            AccessTokenData(
                accessToken = token,
                tokenType = "Bearer",
                clientId = "client-1",
                subject = subject,
                scope = scope,
                audience = emptyList(),
                issuer = "https://issuer.example.com",
                issuedAt = now,
                expiresAt = now + 3600.seconds,
            )
        val storeResult = tokenStorage.storeAccessToken(token, data)
        assertTrue(storeResult.isOk, "failed to seed token: ${if (!storeResult.isOk) storeResult.error else "unknown"}")
    }

    private fun emittedJson(response: UserInfoResponse): JsonObject = Json.parseToJsonElement(Json.encodeToString(UserInfoResponse.serializer(), response)) as JsonObject

    // ─── Wire-shape tests (the P0 finding) ───────────────────────────────────

    @Test
    fun userInfoResponseSerializesAsFlatJsonObject() {
        val response =
            UserInfoResponse(
                buildJsonObject {
                    put("sub", JsonPrimitive("248289761001"))
                    put("email", JsonPrimitive("jdoe@example.com"))
                    put("email_verified", JsonPrimitive(true))
                    put("given_name", JsonPrimitive("Jane"))
                },
            )

        val encoded = Json.encodeToString(UserInfoResponse.serializer(), response)

        assertFalse(encoded.contains("\"claims\""), "wire output must not wrap in a 'claims' envelope: $encoded")
        val parsed = Json.parseToJsonElement(encoded) as JsonObject
        assertEquals("248289761001", parsed["sub"]?.jsonPrimitive?.content)
        assertEquals("jdoe@example.com", parsed["email"]?.jsonPrimitive?.content)
        assertEquals(true, parsed["email_verified"]?.jsonPrimitive?.boolean)
        assertEquals("Jane", parsed["given_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun userInfoResponseSubGetterReadsFromClaims() {
        val response = UserInfoResponse(buildJsonObject { put("sub", JsonPrimitive("abc-123")) })
        assertEquals("abc-123", response.sub)
    }

    // ─── Command-level behavior tests ────────────────────────────────────────

    @Test
    fun getUserInfoOpenidScopeReturnsOnlySub() =
        runTest {
            val (command, tokenStorage) =
                commandWith(
                    UserInfo(
                        userId = "user-1",
                        username = "jane",
                        displayName = "Jane Doe",
                        email = "jane@example.com",
                        emailVerified = true,
                    ),
                )
            seedToken(tokenStorage, scope = "openid")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isOk, "execute failed: ${if (!result.isOk) result.error else "n/a"}")

            val claims = result.value.claims
            assertEquals("user-1", claims["sub"]?.jsonPrimitive?.content)
            assertEquals(setOf("sub"), claims.keys, "openid-only scope must not emit profile/email claims: ${claims.keys}")
        }

    @Test
    fun getUserInfoProfileScopeReturnsProfileClaims() =
        runTest {
            val (command, tokenStorage) =
                commandWith(
                    UserInfo(
                        userId = "user-2",
                        username = "jdoe",
                        displayName = "Jane Doe",
                        email = "jane@example.com",
                    ),
                )
            seedToken(tokenStorage, scope = "openid profile")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isOk)

            val claims = result.value.claims
            assertEquals("user-2", claims["sub"]?.jsonPrimitive?.content)
            assertEquals("Jane Doe", claims["name"]?.jsonPrimitive?.content)
            assertEquals("jdoe", claims["preferred_username"]?.jsonPrimitive?.content)
            assertFalse("email" in claims, "profile scope must not emit email claim: ${claims.keys}")
        }

    @Test
    fun getUserInfoEmailScopeReturnsEmailClaims() =
        runTest {
            val (command, tokenStorage) =
                commandWith(
                    UserInfo(
                        userId = "user-3",
                        email = "jane@example.com",
                        emailVerified = true,
                    ),
                )
            seedToken(tokenStorage, scope = "openid email")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isOk)

            val claims = result.value.claims
            assertEquals("user-3", claims["sub"]?.jsonPrimitive?.content)
            assertEquals("jane@example.com", claims["email"]?.jsonPrimitive?.content)
            assertEquals(true, claims["email_verified"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun getUserInfoProviderAttributeCannotOverwriteSub() =
        runTest {
            val (command, tokenStorage) =
                commandWith(
                    UserInfo(
                        userId = "real-sub-user-1",
                        // malicious/misconfigured provider tries to inject a different sub via attributes
                        attributes = mapOf("sub" to "attacker-controlled-sub"),
                    ),
                )
            seedToken(tokenStorage, scope = "openid")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isOk)

            val claims = result.value.claims
            assertEquals(
                "real-sub-user-1",
                claims["sub"]?.jsonPrimitive?.content,
                "provider attribute must not overwrite canonical sub",
            )
        }

    @Test
    fun getUserInfoProviderAttributeCannotInjectReservedOidcClaim() =
        runTest {
            val (command, tokenStorage) =
                commandWith(
                    UserInfo(
                        userId = "user-4",
                        attributes =
                            mapOf(
                                "iss" to "attacker.example.com",
                                "aud" to "attacker-audience",
                                "exp" to 9999999999L,
                                "iat" to 1,
                                "auth_time" to 1,
                                "nonce" to "attacker-nonce",
                                "acr" to "0",
                                "amr" to listOf("pwd"),
                                "azp" to "attacker-client",
                            ),
                    ),
                )
            seedToken(tokenStorage, scope = "openid")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isOk)

            val claims = result.value.claims
            assertEquals(setOf("sub"), claims.keys, "reserved OIDC claims must be dropped from provider attributes: ${claims.keys}")
        }

    @Test
    fun getUserInfoMissingOpenidScopeReturnsError() =
        runTest {
            val (command, tokenStorage) = commandWith(UserInfo(userId = "user-5"))
            seedToken(tokenStorage, scope = "profile email")

            val result = command.execute(GetUserInfoArgs(accessToken = "access-token-abc"))
            assertTrue(result.isErr, "access token without openid scope must fail")
        }

    @Test
    fun getUserInfoExpiredTokenReturnsError() =
        runTest {
            val (command, tokenStorage) = commandWith(UserInfo(userId = "user-6"))
            val expired =
                AccessTokenData(
                    accessToken = "expired-token",
                    tokenType = "Bearer",
                    clientId = "client-1",
                    subject = "user-6",
                    scope = "openid",
                    audience = emptyList(),
                    issuer = "https://issuer.example.com",
                    issuedAt = Clock.System.now() - 120.seconds,
                    expiresAt = Clock.System.now() - 60.seconds,
                )
            tokenStorage.storeAccessToken("expired-token", expired)

            val result = command.execute(GetUserInfoArgs(accessToken = "expired-token"))
            assertTrue(result.isErr)
        }
}
