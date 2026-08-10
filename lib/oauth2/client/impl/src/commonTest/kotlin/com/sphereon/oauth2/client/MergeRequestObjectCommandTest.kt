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
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.MergeRequestObjectArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.model.AuthorizationRequest
import dev.whyoleg.cryptography.CryptographyProvider
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for MergeRequestObjectCommand implementation
 */
class MergeRequestObjectCommandTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jarService: JarService

    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("merge-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "merge-test-provider",
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
    fun testMergeRequestObjectWithNoQueryParams() =
        runTest {
            // Generate key pair for signing
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Create authorization request
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                    scope = "openid profile",
                    state = "test-state-123",
                )

            // Create signed JAR
            val signedJar =
                jarService
                    .createSignedJar(
                        CreateSignedJarArgs(
                            authorizationRequest = authRequest,
                            signingKey = privateKeyInfo,
                            issuer = "test-client",
                            audience = "https://as.example.com",
                            expirationSeconds = 300,
                        ),
                    ).value.value

            // Merge with empty query params
            val mergeResult =
                jarService.mergeRequestObject(
                    MergeRequestObjectArgs(
                        requestObjectJwt = signedJar,
                        queryParameters = io.ktor.http.Parameters.Empty,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = publicKeyInfo,
                    ),
                )

            // Verify merge succeeded
            assertTrue(mergeResult.isOk, "Should merge successfully")
            val merged = mergeResult.value

            // Verify request object parameters are present
            assertEquals("test-client", merged.mergedParameters["client_id"])
            assertEquals("https://client.example.com/callback", merged.mergedParameters["redirect_uri"])
            assertEquals("code", merged.mergedParameters["response_type"])
            assertEquals("openid profile", merged.mergedParameters["scope"])
            assertEquals("test-state-123", merged.mergedParameters["state"])

            // Verify it's not encrypted
            assertFalse(merged.isEncrypted, "Should not be encrypted")

            println("Successfully merged request object with no query params")
        }

    @Test
    fun testMergeRequestObjectWithAllowedDuplicates() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Create authorization request
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                    scope = "openid profile",
                )

            // Create signed JAR
            val signedJar =
                jarService
                    .createSignedJar(
                        CreateSignedJarArgs(
                            authorizationRequest = authRequest,
                            signingKey = privateKeyInfo,
                            issuer = "test-client",
                            audience = "https://as.example.com",
                            expirationSeconds = 300,
                        ),
                    ).value.value

            // Create query params with allowed duplicates (client_id, response_type)
            val queryParams =
                ParametersBuilder()
                    .apply {
                        append("client_id", "test-client")
                        append("response_type", "code")
                        append("state", "query-state")
                    }.build()

            // Merge
            val mergeResult =
                jarService.mergeRequestObject(
                    MergeRequestObjectArgs(
                        requestObjectJwt = signedJar,
                        queryParameters = queryParams,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = publicKeyInfo,
                    ),
                )

            // Verify merge succeeded
            assertTrue(mergeResult.isOk, "Should merge successfully with allowed duplicates")
            val merged = mergeResult.value

            // Request object params take precedence
            assertEquals("test-client", merged.mergedParameters["client_id"])
            assertEquals("code", merged.mergedParameters["response_type"])

            // Query param that wasn't in request object should be added
            assertEquals("query-state", merged.mergedParameters["state"])

            println("Successfully merged with allowed duplicates")
        }

    @Test
    fun testMergeRequestObjectRejectsInvalidDuplicates() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Create authorization request with scope
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                    scope = "openid profile",
                )

            // Create signed JAR
            val signedJar =
                jarService
                    .createSignedJar(
                        CreateSignedJarArgs(
                            authorizationRequest = authRequest,
                            signingKey = privateKeyInfo,
                            issuer = "test-client",
                            audience = "https://as.example.com",
                            expirationSeconds = 300,
                        ),
                    ).value.value

            // Create query params with invalid duplicate (scope)
            val queryParams =
                ParametersBuilder()
                    .apply {
                        append("scope", "openid email")
                    }.build()

            // Merge should fail
            val mergeResult =
                jarService.mergeRequestObject(
                    MergeRequestObjectArgs(
                        requestObjectJwt = signedJar,
                        queryParameters = queryParams,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = publicKeyInfo,
                    ),
                )

            // Verify merge failed
            assertTrue(mergeResult.isErr, "Should reject invalid parameter duplication")
            val errorMessage = mergeResult.error.message.defaultMessage
            assertTrue(
                errorMessage.contains("Invalid parameter duplication"),
                "Error should mention parameter duplication",
            )

            println("Successfully rejected invalid duplication: $errorMessage")
        }

    @Test
    fun testMergeRequestObjectWithoutVerification() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Create authorization request
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                )

            // Create signed JAR
            val signedJar =
                jarService
                    .createSignedJar(
                        CreateSignedJarArgs(
                            authorizationRequest = authRequest,
                            signingKey = privateKeyInfo,
                            issuer = "test-client",
                            audience = "https://as.example.com",
                            expirationSeconds = 300,
                        ),
                    ).value.value

            // Merge without verification key (allows unsigned requests)
            val mergeResult =
                jarService.mergeRequestObject(
                    MergeRequestObjectArgs(
                        requestObjectJwt = signedJar,
                        queryParameters = io.ktor.http.Parameters.Empty,
                        verificationKey = null, // Skip verification
                    ),
                )

            // Verify merge succeeded
            assertTrue(mergeResult.isOk, "Should merge successfully without verification")
            val merged = mergeResult.value

            assertEquals("test-client", merged.mergedParameters["client_id"])
            assertEquals("https://client.example.com/callback", merged.mergedParameters["redirect_uri"])

            println("Successfully merged without verification")
        }

    @Test
    fun testMergeRequestObjectAddsNonDuplicateQueryParams() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Create authorization request (minimal)
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    responseType = "code",
                )

            // Create signed JAR
            val signedJar =
                jarService
                    .createSignedJar(
                        CreateSignedJarArgs(
                            authorizationRequest = authRequest,
                            signingKey = privateKeyInfo,
                            issuer = "test-client",
                            audience = "https://as.example.com",
                            expirationSeconds = 300,
                        ),
                    ).value.value

            // Create query params with additional parameters not in request object
            val queryParams =
                ParametersBuilder()
                    .apply {
                        append("state", "query-state")
                        append("nonce", "query-nonce")
                        append("ui_locales", "en-US")
                    }.build()

            // Merge
            val mergeResult =
                jarService.mergeRequestObject(
                    MergeRequestObjectArgs(
                        requestObjectJwt = signedJar,
                        queryParameters = queryParams,
                        issuer = "test-client",
                        audience = "https://as.example.com",
                        verificationKey = publicKeyInfo,
                    ),
                )

            // Verify merge succeeded
            assertTrue(mergeResult.isOk, "Should merge successfully")
            val merged = mergeResult.value

            // Request object params
            assertEquals("test-client", merged.mergedParameters["client_id"])
            assertEquals("https://client.example.com/callback", merged.mergedParameters["redirect_uri"])
            assertEquals("code", merged.mergedParameters["response_type"])

            // Query params that weren't in request object should be added
            assertEquals("query-state", merged.mergedParameters["state"])
            assertEquals("query-nonce", merged.mergedParameters["nonce"])
            assertEquals("en-US", merged.mergedParameters["ui_locales"])

            println("Successfully merged with additional query params")
        }
}
