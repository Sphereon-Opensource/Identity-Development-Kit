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
 *
 */

package com.sphereon.ktor.server.jwt

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.defaults.session.DefaultSessionContextFactory
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.IdentityMetadata
import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.context.IdentityResolutionPipeline
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import com.sphereon.di.session.SessionContextFactory
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.JwtValidationService
import com.sphereon.oauth2.jwt.validation.TokenClaims
import com.sphereon.oauth2.jwt.validation.ValidatedAccessToken
import com.sphereon.oauth2.jwt.validation.ValidatedIdToken
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtAuthenticationPluginTest {
    private val validationService =
        StubValidationService(
            mapOf(
                "valid-token" to
                    ValidatedAccessToken(
                        subject = "user-42",
                        issuer = "https://issuer.example.com",
                        audiences = listOf("api"),
                        expiresAt = 0,
                        issuedAt = 0,
                        notBefore = null,
                        scopes = emptySet(),
                        clientId = "client-1",
                        jwtId = null,
                        rawToken = "valid-token",
                        claims =
                            mapOf(
                                "sub" to JsonPrimitive("user-42"),
                                "iss" to JsonPrimitive("https://issuer.example.com"),
                                "aud" to JsonPrimitive("api"),
                            ),
                        idpId = "default",
                    ),
            ),
        )

    private val resolutionPipeline =
        StubResolutionPipeline { input ->
            if (input.tokenClaims == null) {
                IdentityResolutionResult(
                    tenantId = null,
                    principalId = null,
                    principalType = PrincipalType.ANONYMOUS,
                    metadata = IdentityMetadata(resolvedFrom = ResolutionSource.DEFAULT),
                )
            } else {
                IdentityResolutionResult(
                    tenantId = "tenant-a",
                    principalId = "user-42",
                    principalType = PrincipalType.USER,
                    metadata =
                        IdentityMetadata(
                            issuer = "https://issuer.example.com",
                            audience = "api",
                            resolvedFrom = ResolutionSource.TOKEN,
                        ),
                )
            }
        }

    private val factory: SessionContextFactory = DefaultSessionContextFactory()

    @Test
    fun `valid token yields 200 and populates session context`() =
        testApplication {
            install(JwtAuthentication) {
                jwtValidationService = { validationService }
                identityResolutionPipeline = { resolutionPipeline }
                sessionContextFactory = { factory }
            }
            routing {
                get("/secured") {
                    val session = call.attributes[SessionContextAttributeKey]
                    call.respond("${session.context.tenant.tenantId}:${session.context.principal}")
                }
            }

            val response =
                client.get("/secured") {
                    header(HttpHeaders.Authorization, "Bearer valid-token")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("tenant-a:user-42", response.bodyAsText())
        }

    @Test
    fun `missing token returns 401 with rfc 6750 header`() =
        testApplication {
            install(JwtAuthentication) {
                jwtValidationService = { validationService }
                identityResolutionPipeline = { resolutionPipeline }
                sessionContextFactory = { factory }
            }
            routing {
                get("/secured") { call.respond("ok") }
            }

            val response = client.get("/secured")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val www = response.headers[HttpHeaders.WWWAuthenticate]
            assertNotNull(www)
            assertTrue(www.startsWith("Bearer error=\"invalid_token\""))
        }

    @Test
    fun `anonymous path bypasses auth and still populates anonymous session`() =
        testApplication {
            install(JwtAuthentication) {
                jwtValidationService = { validationService }
                identityResolutionPipeline = { resolutionPipeline }
                sessionContextFactory = { factory }
                anonymousPaths = listOf("/health", "/api/v1/public/**")
            }
            routing {
                get("/health") {
                    val session = call.attributes[SessionContextAttributeKey]
                    val body =
                        if (session.context.tenant.tenantId == IdentityConstants.ANONYMOUS_TENANT_ID) {
                            "anon"
                        } else {
                            "bound"
                        }
                    call.respond(body)
                }
                get("/api/v1/public/info") {
                    call.respond("public")
                }
            }

            val healthResponse = client.get("/health")
            assertEquals(HttpStatusCode.OK, healthResponse.status)
            assertEquals("anon", healthResponse.bodyAsText())

            val publicResponse = client.get("/api/v1/public/info")
            assertEquals(HttpStatusCode.OK, publicResponse.status)
        }

    @Test
    fun `invalid token returns 401 with rfc 6750 header`() =
        testApplication {
            install(JwtAuthentication) {
                jwtValidationService = { validationService }
                identityResolutionPipeline = { resolutionPipeline }
                sessionContextFactory = { factory }
            }
            routing {
                get("/secured") { call.respond("ok") }
            }

            val response =
                client.get("/secured") {
                    header(HttpHeaders.Authorization, "Bearer not-known")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val www = response.headers[HttpHeaders.WWWAuthenticate]
            assertNotNull(www)
            assertTrue(www.contains("error=\"invalid_token\""))
        }

    private class StubValidationService(
        private val validTokens: Map<String, ValidatedAccessToken>,
    ) : JwtValidationService {
        override suspend fun validateAccessToken(
            token: String,
            options: AccessTokenValidationOptions,
        ): IdkResult<ValidatedAccessToken, JwtValidationError> = validTokens[token]?.let { Ok(it) } ?: Err(JwtValidationError.signatureInvalid(issuer = null))

        override suspend fun validateIdToken(
            token: String,
            options: IdTokenValidationOptions,
        ): IdkResult<ValidatedIdToken, JwtValidationError> = Err(JwtValidationError.validationError("ID tokens not supported in test stub"))

        override suspend fun extractClaims(token: String): IdkResult<TokenClaims, JwtValidationError> = Err(JwtValidationError.validationError("extractClaims not supported in test stub"))
    }

    private class StubResolutionPipeline(
        private val resolver: suspend (IdentityResolutionInput) -> IdentityResolutionResult,
    ) : IdentityResolutionPipeline {
        override suspend fun resolve(input: IdentityResolutionInput): IdentityResolutionResult = resolver(input)
    }
}
