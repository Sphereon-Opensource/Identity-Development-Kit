/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.oauth2.common.testutil.createOauth2CommonTestAppComponent

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.jose.jws.CreateJwsArgs
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.command.ValidateIdTokenCommandImpl
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    val app = createOauth2CommonTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("id-token-e2e-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "id-token-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val softwareKmsProvider = (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get services from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwtService = (session.component as JwtServiceImpl.Component).jwtService
        validateCommand = ValidateIdTokenCommandImpl(session.asCoreApiServiceComponent().serviceExecution, jwtService)
    }

    /**
     * Helper to create and sign an ID Token
     */
    private suspend fun createSignedIdToken(
        issuer: ManagedOptsKeyInfo,
        payload: Map<String, Any>
    ): String {
        val payloadJson = buildJsonObject {
            payload.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Long -> put(key, value)
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
        val result = jwtService.createJwsCompact(
            CreateJwsArgs(
                issuer = issuer,
                payload = payloadJson  // Use JsonObject directly
            )
        )

        if (result.isErr) {
            throw Exception("Failed to create signed ID Token: ${result.error}")
        }

        return result.value.jwt
    }

    @Test
    fun `test validate valid ID token with all required claims`() = runTest {
        // Generate key pair
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val payload = mapOf(
            "iss" to "https://example.com",
            "sub" to "user123",
            "aud" to listOf("client123"),
            "exp" to (now + 3600),
            "iat" to now
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123"
                )
            )
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
    fun `test validate ID token with nonce`() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val expectedNonce = "nonce123"
        val payload = mapOf(
            "iss" to "https://example.com",
            "sub" to "user123",
            "aud" to listOf("client123"),
            "exp" to (now + 3600),
            "iat" to now,
            "nonce" to expectedNonce
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123",
                    expectedNonce = expectedNonce
                )
            )
        )

        if (result.isErr) {
            println("Nonce test validation failed with error: ${result.error}")
        }
        assertTrue(result.isOk, "Validation should succeed with matching nonce, but got error: ${if (result.isErr) result.error else "N/A"}")
        assertTrue(result.value.nonceMatched == true)
    }

    @Test
    fun `test validate fails with wrong issuer`() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val payload = mapOf(
            "iss" to "https://evil.com",
            "sub" to "user123",
            "aud" to listOf("client123"),
            "exp" to (now + 3600),
            "iat" to now
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123"
                )
            )
        )

        assertTrue(result.isErr, "Validation should fail with wrong issuer")
    }

    @Test
    fun `test validate fails with wrong audience`() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val payload = mapOf(
            "iss" to "https://example.com",
            "sub" to "user123",
            "aud" to listOf("wrong-client"),
            "exp" to (now + 3600),
            "iat" to now
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123"
                )
            )
        )

        assertTrue(result.isErr, "Validation should fail with wrong audience")
    }

    @Test
    fun `test validate fails with expired token`() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val payload = mapOf(
            "iss" to "https://example.com",
            "sub" to "user123",
            "aud" to listOf("client123"),
            "exp" to (now - 3600), // Expired 1 hour ago
            "iat" to (now - 7200)
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123"
                )
            )
        )

        assertTrue(result.isErr, "Validation should fail with expired token")
    }

    @Test
    fun `test validate fails with wrong nonce`() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(clientId = "https://example.com")
        )

        val now = Clock.System.now().epochSeconds
        val payload = mapOf(
            "iss" to "https://example.com",
            "sub" to "user123",
            "aud" to listOf("client123"),
            "exp" to (now + 3600),
            "iat" to now,
            "nonce" to "wrong-nonce"
        )

        val idToken = createSignedIdToken(issuer, payload)

        val result = validateCommand.execute(
            ValidateIdTokenArgs(
                idToken = idToken,
                options = IdTokenValidationOptions(
                    expectedIssuer = "https://example.com",
                    expectedAudience = "client123",
                    expectedNonce = "expected-nonce"
                )
            )
        )

        assertTrue(result.isErr, "Validation should fail with wrong nonce")
    }
}
