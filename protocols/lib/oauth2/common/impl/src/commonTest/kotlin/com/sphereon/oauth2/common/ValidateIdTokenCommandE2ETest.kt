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

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.command.ValidateIdTokenCommandImpl
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.testutil.createOauth2CommonTestAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * E2E tests for ID Token validation with real JWT tokens
 *
 * These tests use the software KMS provider to create and sign real JWTs,
 * then validate them using the ValidateIdTokenCommand.
 */
class ValidateIdTokenCommandE2ETest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var validateCommand: ValidateIdTokenCommand

    val app = createOauth2CommonTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("id-token-e2e-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "id-token-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val softwareKmsProvider =
            (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                config,
                session.asCoreApiServiceGraph().serviceExecution,
            )

        // Get services from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        validateCommand = ValidateIdTokenCommandImpl(session.asCoreApiServiceGraph().serviceExecution, jwtService)
    }

    /**
     * Helper to create and sign an ID Token
     */
    private suspend fun createSignedIdToken(
        issuer: ManagedOptsKeyInfo,
        payload: Map<String, Any>,
    ): String {
        val payloadJson =
            buildJsonObject {
                payload.forEach { (key, value) ->
                    when (value) {
                        is String -> {
                            put(key, value)
                        }

                        is Long -> {
                            put(key, value)
                        }

                        is List<*> -> {
                            // Handle list of strings for aud
                            if (value.all { it is String }) {
                                put(key, JsonArray(value.map { JsonPrimitive(it as String) }))
                            }
                        }
                    }
                }
            }

        // Create JWS with JsonObject payload
        val result =
            jwtService.createJwsCompact(
                CreateJwsArgs(
                    issuer = issuer,
                    payload = payloadJson, // Use JsonObject directly
                ),
            )

        if (result.isErr) {
            throw Exception("Failed to create signed ID Token: ${result.error}")
        }

        return result.value.jwt
    }

    @Test
    fun `test validate valid ID token with all required claims`() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user123",
                    "aud" to listOf("client123"),
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                            ),
                    ),
                )

            if (result.isErr) {
                println("Validation failed with error: ${result.error}")
            }
            assertTrue(result.isOk, "Validation should succeed, but got error: ${if (result.isErr) result.error else "N/A"}")
            val validated = result.value
            assertEquals("https://example.com", validated.payload.iss)
            assertEquals("user123", validated.payload.sub)
            assertEquals(listOf("client123"), validated.payload.aud)
        }

    @Test
    fun `test validate ID token with nonce`() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val expectedNonce = "nonce123"
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user123",
                    "aud" to listOf("client123"),
                    "exp" to (now + 3600),
                    "iat" to now,
                    "nonce" to expectedNonce,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                                expectedNonce = expectedNonce,
                            ),
                    ),
                )

            if (result.isErr) {
                println("Nonce test validation failed with error: ${result.error}")
            }
            assertTrue(result.isOk, "Validation should succeed with matching nonce, but got error: ${if (result.isErr) result.error else "N/A"}")
            assertTrue(result.value.nonceMatched == true)
        }

    @Test
    fun `test validate fails with wrong issuer`() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://evil.com",
                    "sub" to "user123",
                    "aud" to listOf("client123"),
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                            ),
                    ),
                )

            assertTrue(result.isErr, "Validation should fail with wrong issuer")
        }

    @Test
    fun `test validate fails with wrong audience`() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user123",
                    "aud" to listOf("wrong-client"),
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                            ),
                    ),
                )

            assertTrue(result.isErr, "Validation should fail with wrong audience")
        }

    @Test
    fun `test validate fails with expired token`() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user123",
                    "aud" to listOf("client123"),
                    "exp" to (now - 3600), // Expired 1 hour ago
                    "iat" to (now - 7200),
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                            ),
                    ),
                )

            assertTrue(result.isErr, "Validation should fail with expired token")
        }

    @Test
    fun `test validate fails with wrong nonce`() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user123",
                    "aud" to listOf("client123"),
                    "exp" to (now + 3600),
                    "iat" to now,
                    "nonce" to "wrong-nonce",
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client123",
                                expectedNonce = "expected-nonce",
                            ),
                    ),
                )

            assertTrue(result.isErr, "Validation should fail with wrong nonce")
        }

    // OIDC Core §3.1.3.7 claim validations (aud array + azp + max_age).

    @Test
    fun validate_audArrayWithoutAzp_rejects() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1", "other-audience"),
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client-1",
                            ),
                    ),
                )

            assertTrue(result.isErr, "aud-array without azp must be rejected")
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("azp") == true,
                "rejection reason must cite azp; got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_audArrayWithMatchingAzp_accepts() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1", "other-audience"),
                    "azp" to "client-1",
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client-1",
                            ),
                    ),
                )

            assertTrue(
                result.isOk,
                "aud-array + matching azp must pass; got: ${if (result.isErr) result.error else ""}",
            )
        }

    @Test
    fun validate_azpPresent_mismatchRejects() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            // Even with a single aud, if azp is present it MUST equal client_id.
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1"),
                    "azp" to "other-client",
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client-1",
                            ),
                    ),
                )

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("azp") == true,
                "rejection must cite azp; got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_maxAgeExceeded_rejects() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1"),
                    "exp" to (now + 3600),
                    "iat" to now,
                    // auth_time is 1 hour ago; caller requires max_age=60s → stale.
                    "auth_time" to (now - 3600),
                )

            val idToken = createSignedIdToken(issuer, payload)

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client-1",
                                maxAge = 60,
                            ),
                    ),
                )

            assertTrue(result.isErr, "stale authentication must be rejected")
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("Authentication is too old") == true,
                "rejection must cite age; got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun validate_audMissing_rejects() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val now = Clock.System.now().epochSeconds
            // Construct payload WITHOUT 'aud' — the payload parser/structure validator must reject.
            val payload =
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "exp" to (now + 3600),
                    "iat" to now,
                )

            // createSignedIdToken's current implementation requires aud during serialization, so
            // sign a payload with an empty aud list and rely on the validator to reject it.
            val idToken = createSignedIdToken(issuer, payload + ("aud" to emptyList<String>()))

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://example.com",
                                expectedAudience = "client-1",
                            ),
                    ),
                )
            assertTrue(result.isErr, "missing/empty aud must be rejected")
        }

    @Test
    fun validate_futureIssuedAt_rejects() =
        runTest {
            val keyInfo = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256).joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer = ManagedOptsKeyInfo(identifier = keyInfo, context = IdentifierContext(clientId = "https://example.com"))
            val now = Clock.System.now().epochSeconds
            val idToken =
                createSignedIdToken(
                    issuer,
                    mapOf(
                        "iss" to "https://example.com",
                        "sub" to "user",
                        "aud" to listOf("client-1"),
                        "exp" to (now + 7200),
                        "iat" to (now + 3600),
                    ),
                )

            val result = validateCommand.execute(
                ValidateIdTokenArgs(
                    idToken,
                    IdTokenValidationOptions("https://example.com", "client-1", clockSkewSeconds = 0),
                ),
            )
            assertTrue(result.isErr, "an ID token issued in the future must be rejected")
        }

    @Test
    fun validate_invalidAtHash_rejects() =
        runTest {
            val keyInfo = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256).joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer = ManagedOptsKeyInfo(identifier = keyInfo, context = IdentifierContext(clientId = "https://example.com"))
            val now = Clock.System.now().epochSeconds
            val idToken = createSignedIdToken(
                issuer,
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1"),
                    "exp" to (now + 3600),
                    "iat" to now,
                    "at_hash" to "deliberately-invalid",
                ),
            )

            val result = validateCommand.execute(
                ValidateIdTokenArgs(
                    idToken,
                    IdTokenValidationOptions("https://example.com", "client-1", accessToken = "access-token"),
                ),
            )
            assertTrue(result.isErr, "an invalid at_hash must be rejected")
        }

    @Test
    fun validate_invalidCHash_rejects() =
        runTest {
            val keyInfo = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256).joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer = ManagedOptsKeyInfo(identifier = keyInfo, context = IdentifierContext(clientId = "https://example.com"))
            val now = Clock.System.now().epochSeconds
            val idToken = createSignedIdToken(
                issuer,
                mapOf(
                    "iss" to "https://example.com",
                    "sub" to "user",
                    "aud" to listOf("client-1"),
                    "exp" to (now + 3600),
                    "iat" to now,
                    "c_hash" to "deliberately-invalid",
                ),
            )

            val result = validateCommand.execute(
                ValidateIdTokenArgs(
                    idToken,
                    IdTokenValidationOptions("https://example.com", "client-1", authorizationCode = "authorization-code"),
                ),
            )
            assertTrue(result.isErr, "an invalid c_hash must be rejected")
        }
}
