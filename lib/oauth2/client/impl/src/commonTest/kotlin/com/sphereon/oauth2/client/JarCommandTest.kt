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

package com.sphereon.oauth2.client

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.oauth2.client.command.CreateEncryptedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.ParseJarArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.model.AuthorizationRequest
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for JAR command implementations
 */
class JarCommandTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jarService: JarService

    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jar-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "jar-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val softwareKmsProvider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(config, session.asCoreApiServiceGraph().serviceExecution)

        // Get KeyManagerService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        // Get JarService from the session graph
        jarService = (session.graph as JarServiceImpl.Graph).jarService
    }

    @Test
    fun testCreateAndParseSignedJar() =
        runTest {
            // Generate key pair for signing/verification
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            // For verification, we use the private key info but the JWS verification will extract the public key
            val publicKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Create authorization request
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                    scope = "openid profile email",
                    state = "test-state-123",
                )

            // Create signed JAR
            val createResult =
                jarService.createSignedJar(
                    CreateSignedJarArgs(
                        authorizationRequest = authRequest,
                        signingKey = privateKeyInfo,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        expirationSeconds = 300,
                    ),
                )

            // Verify creation succeeded
            assertTrue(createResult.isOk, "Should create signed JAR successfully")
            val signedJar = createResult.value.value
            println("Created signed JAR: ${signedJar.take(100)}...")

            // Verify format
            assertTrue(signedJar.isNotEmpty())
            assertEquals(2, signedJar.count { it == '.' }, "JWS should have 2 dots (3 parts)")

            // Parse the signed JAR back
            val parseResult =
                jarService.parseJar(
                    ParseJarArgs(
                        jarToken = signedJar,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = publicKeyInfo,
                    ),
                )

            // Verify parsing succeeded
            assertTrue(parseResult.isOk, "Should parse signed JAR successfully, but got: ${if (parseResult.isErr) parseResult.error else "N/A"}")
            val parsed = parseResult.value

            // Verify it's not encrypted
            assertFalse(parsed.isEncrypted, "Signed JAR should not be marked as encrypted")

            // Verify authorization request was parsed correctly
            assertEquals(authRequest.clientId, parsed.authorizationRequest.clientId)
            assertEquals(authRequest.redirectUri, parsed.authorizationRequest.redirectUri)
            assertEquals(authRequest.responseType, parsed.authorizationRequest.responseType)
            assertEquals(authRequest.scope, parsed.authorizationRequest.scope)
            assertEquals(authRequest.state, parsed.authorizationRequest.state)

            // Verify JWT claims are present
            assertTrue(parsed.claims.containsKey("iss"), "Should have 'iss' claim")
            assertTrue(parsed.claims.containsKey("aud"), "Should have 'aud' claim")
            assertTrue(parsed.claims.containsKey("exp"), "Should have 'exp' claim")
            assertTrue(parsed.claims.containsKey("iat"), "Should have 'iat' claim")
            assertTrue(parsed.claims.containsKey("jti"), "Should have 'jti' claim")

            println("Successfully parsed signed JAR and verified all claims")
        }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testCreateSignedJarPreservesCallerProvidedX5c() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val callerChain = listOf("protocol-leaf", "protocol-intermediate")

            val result =
                jarService.createSignedJar(
                    CreateSignedJarArgs(
                        authorizationRequest = AuthorizationRequest(clientId = "x509_hash:test", responseType = "vp_token"),
                        signingKey = privateKeyInfo,
                        issuer = "x509_hash:test",
                        audience = "https://self-issued.me/v2",
                        x5c = callerChain,
                    ),
                )

            assertTrue(result.isOk, "Should create signed JAR with caller-provided x5c")
            val encodedHeader = result.value.value.substringBefore('.')
            val header =
                Json.parseToJsonElement(
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(encodedHeader).decodeToString(),
                ).jsonObject
            assertEquals(callerChain, header.getValue("x5c").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("ES256", header.getValue("alg").jsonPrimitive.content)
        }

    @Test
    fun testCreateAndParseEncryptedJar() =
        runTest {
            // Generate client key pair for signing
            val clientKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val clientPrivateKey = clientKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val clientPublicKey = clientKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Generate AS key pair for encryption
            val asKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val asPublicKey = asKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val asPrivateKey = asKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Create authorization request
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                    scope = "openid profile email address phone",
                    state = "test-state-encrypted-456",
                )

            // Step 1: Create signed JAR
            val signedResult =
                jarService.createSignedJar(
                    CreateSignedJarArgs(
                        authorizationRequest = authRequest,
                        signingKey = clientPrivateKey,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        expirationSeconds = 300,
                    ),
                )
            assertTrue(signedResult.isOk, "Should create signed JAR")
            val signedJar = signedResult.value.value
            println("Created signed JAR (${signedJar.length} chars)")

            // Step 2: Encrypt the signed JAR
            val encryptedResult =
                jarService.createEncryptedJar(
                    CreateEncryptedJarArgs(
                        signedJar = signedJar,
                        recipientPublicKey = asPublicKey,
                        keyEncryptionAlgorithm = "RSA-OAEP-256",
                        contentEncryptionAlgorithm = "A256GCM",
                    ),
                )
            assertTrue(encryptedResult.isOk, "Should create encrypted JAR successfully")
            val encryptedJar = encryptedResult.value.value
            println("Created encrypted JAR: ${encryptedJar.take(100)}...")

            // Verify format - JWE has 5 parts
            assertTrue(encryptedJar.isNotEmpty())
            assertEquals(4, encryptedJar.count { it == '.' }, "JWE should have 4 dots (5 parts)")

            // Step 3: Parse the encrypted JAR
            val parseResult =
                jarService.parseJar(
                    ParseJarArgs(
                        jarToken = encryptedJar,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = clientPublicKey,
                        decryptionKey = asPrivateKey,
                    ),
                )

            // Verify parsing succeeded
            assertTrue(parseResult.isOk, "Should parse encrypted JAR successfully, but got: ${if (parseResult.isErr) parseResult.error else "N/A"}")
            val parsed = parseResult.value

            // Verify it IS encrypted
            assertTrue(parsed.isEncrypted, "Encrypted JAR should be marked as encrypted")

            // Verify authorization request was decrypted and parsed correctly
            assertEquals(authRequest.clientId, parsed.authorizationRequest.clientId)
            assertEquals(authRequest.redirectUri, parsed.authorizationRequest.redirectUri)
            assertEquals(authRequest.responseType, parsed.authorizationRequest.responseType)
            assertEquals(authRequest.scope, parsed.authorizationRequest.scope)
            assertEquals(authRequest.state, parsed.authorizationRequest.state)

            // Verify JWT claims are present after decryption
            assertTrue(parsed.claims.containsKey("iss"), "Should have 'iss' claim")
            assertTrue(parsed.claims.containsKey("aud"), "Should have 'aud' claim")
            assertTrue(parsed.claims.containsKey("exp"), "Should have 'exp' claim")

            println("Successfully decrypted and parsed encrypted JAR")
        }
}
