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

package com.sphereon.oauth2.common

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.command.ValidateIdTokenCommandImpl
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.testutil.createOauth2CommonTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for the OIDF-aligned ID token pre-validation behaviour:
 * alg allow-list, embedded-key rejection, and JWKS `kid` binding.
 *
 * These tests hand-craft JWS headers and assert the pre-validation rejection path runs BEFORE
 * signature verification — the signatures deliberately aren't valid. End-to-end signed-token
 * validation is covered by the existing `ValidateIdTokenCommandE2ETest`.
 */
class ValidateIdTokenJwksBindingTest {
    private val app = createOauth2CommonTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("validate-id-token-jwks-binding", principalType = com.sphereon.di.context.PrincipalType.USER)
    private val execution = session.asCoreApiServiceGraph().serviceExecution
    private val jwtService: JwtService = (session.graph as JwtServiceImpl.Graph).jwtService
    private val command: ValidateIdTokenCommand = ValidateIdTokenCommandImpl(execution, jwtService)

    private fun jwtWith(
        header: Map<String, JsonElement>,
        payload: Map<String, JsonElement> = defaultPayload(),
    ): String {
        val headerJson = buildJsonObject { header.forEach { (k, v) -> put(k, v) } }.toString()
        val payloadJson = buildJsonObject { payload.forEach { (k, v) -> put(k, v) } }.toString()
        val h = headerJson.encodeToByteArray().encodeToBase64Url()
        val p = payloadJson.encodeToByteArray().encodeToBase64Url()
        // Signature is deliberately bogus — these tests verify pre-validation rejects the token
        // before signature verification would have had a chance to run.
        val s = "bogus-signature".encodeToByteArray().encodeToBase64Url()
        return "$h.$p.$s"
    }

    private fun defaultPayload(): Map<String, JsonElement> =
        mapOf(
            "iss" to JsonPrimitive("https://example.com"),
            "sub" to JsonPrimitive("user-1"),
            "aud" to JsonArray(listOf(JsonPrimitive("client-1"))),
            "exp" to JsonPrimitive(9_999_999_999L),
            "iat" to JsonPrimitive(1_000_000_000L),
        )

    private val issuerOptions =
        IdTokenValidationOptions(
            expectedIssuer = "https://example.com",
            expectedAudience = "client-1",
        )

    @Test
    fun validate_algNotInAllowList_rejects() =
        runTest {
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("HS256"),
                            "kid" to JsonPrimitive("key-1"),
                        ),
                )

            val result = command.execute(ValidateIdTokenArgs(idToken, issuerOptions))
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("alg") == true,
                "expected alg-allow-list failure, got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_algCustomAllowList_acceptsHs256WhenExplicit() =
        runTest {
            // Sanity check: the alg check is purely about the allow-list; a caller opt-ing into
            // HS256 gets past the pre-validation (it will then fail on signature, not on alg).
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("HS256"),
                            "kid" to JsonPrimitive("key-1"),
                        ),
                )

            val result =
                command.execute(
                    ValidateIdTokenArgs(
                        idToken,
                        issuerOptions.copy(allowedAlgorithms = listOf("HS256")),
                    ),
                )
            assertTrue(result.isErr) // still fails, but NOT on alg
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("allow-list") != true,
                "alg allow-list must not be the reason for rejection: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_embeddedJwk_rejects() =
        runTest {
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("ES256"),
                            "kid" to JsonPrimitive("key-1"),
                            "jwk" to buildJsonObject { put("kty", JsonPrimitive("EC")) },
                        ),
                )

            val result = command.execute(ValidateIdTokenArgs(idToken, issuerOptions))
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("jwk") == true,
                "expected embedded-jwk rejection, got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_embeddedX5c_rejects() =
        runTest {
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("ES256"),
                            "kid" to JsonPrimitive("key-1"),
                            "x5c" to JsonArray(listOf(JsonPrimitive("cert-chain"))),
                        ),
                )

            val result = command.execute(ValidateIdTokenArgs(idToken, issuerOptions))
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("x5c") == true,
                "expected embedded-x5c rejection, got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_allowEmbeddedKeyInHeader_skipsRejection() =
        runTest {
            // Non-OIDC callers can flip the opt-out flag. The jwk/x5c presence no longer rejects.
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("ES256"),
                            "jwk" to buildJsonObject { put("kty", JsonPrimitive("EC")) },
                        ),
                )

            val result =
                command.execute(
                    ValidateIdTokenArgs(
                        idToken,
                        issuerOptions.copy(allowEmbeddedKeyInHeader = true),
                    ),
                )
            assertTrue(result.isErr) // still fails, but NOT on embedded-key pre-check
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("'jwk'") != true,
                "embedded-jwk must not be the reason when opt-in: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_trustedJwks_kidNotInSet_rejects() =
        runTest {
            val trustedJwks =
                JwkSet(
                    keys = arrayOf(Jwk(kty = JwaKeyType.EC, kid = "known-key")),
                )

            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("ES256"),
                            "kid" to JsonPrimitive("unknown-key"),
                        ),
                )

            val result =
                command.execute(
                    ValidateIdTokenArgs(
                        idToken,
                        issuerOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("No matching trusted JWK") == true,
                "expected kid-miss rejection, got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_trustedJwks_missingKid_multipleCandidates_rejects() =
        runTest {
            // Two EC keys in the JWKS — without a kid in the header, the verifier can't
            // disambiguate and MUST refuse instead of guessing.
            val trustedJwks =
                JwkSet(
                    keys =
                        arrayOf(
                            Jwk(kty = JwaKeyType.EC, kid = "ec-1"),
                            Jwk(kty = JwaKeyType.EC, kid = "ec-2"),
                        ),
                )

            val idToken = jwtWith(header = mapOf("alg" to JsonPrimitive("ES256"))) // no kid

            val result =
                command.execute(
                    ValidateIdTokenArgs(
                        idToken,
                        issuerOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("No matching trusted JWK") == true,
                "expected disambiguation rejection, got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_trustedJwks_kidPresent_passesPreCheck() =
        runTest {
            val trustedJwks =
                JwkSet(
                    keys = arrayOf(Jwk(kty = JwaKeyType.EC, kid = "key-1")),
                )
            val idToken =
                jwtWith(
                    header =
                        mapOf(
                            "alg" to JsonPrimitive("ES256"),
                            "kid" to JsonPrimitive("key-1"),
                        ),
                )

            val result =
                command.execute(
                    ValidateIdTokenArgs(
                        idToken,
                        issuerOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            // Still fails because the bogus signature can't be verified — but the reason must NOT
            // be the JWKS-binding pre-check, which is what this test guards.
            assertTrue(result.isErr)
            val msg =
                result.error.message.defaultMessage
                    .orEmpty()
            assertTrue(
                !msg.contains("No matching trusted JWK"),
                "JWKS lookup must succeed when kid is known — got: $msg",
            )
        }
}
