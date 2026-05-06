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
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.client.impl.dpop.ClientVerifyDpopProofCommandImpl
import com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl
import com.sphereon.oauth2.client.testutil.OAuth2ClientTestContext
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.command.CreateDpopProofArgs
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for DPoP command implementations
 */
class DpopCommandTest {
    private lateinit var keyManagerService: KeyManagerService

    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("dpop-test")
    val execution = session.asCoreApiServiceGraph().serviceExecution

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "dpop-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val softwareKmsProvider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(config, execution)

        // Get KeyManagerService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }

    @Test
    fun testCreateDpopProofBasic() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "test-client"),
                )

            val jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
            val createCommand = CreateDpopProofCommandImpl(execution, jwtService, defaultSecureRandom())

            // Create DPoP proof
            val result =
                createCommand.execute(
                    CreateDpopProofArgs(
                        options =
                            CreateDpopProofOptions(
                                issuer = issuer,
                                httpMethod = "POST",
                                httpUrl = "https://as.example.com/token",
                            ),
                        publicJwk = managedKeyPair.jose.publicJwk,
                    ),
                )

            // Verify result
            if (result.isErr) {
                println("Error creating DPoP proof: ${result.error}")
            }
            assertTrue(result.isOk, "Should create DPoP proof successfully, but got error: ${if (result.isErr) result.error else "N/A"}")
            val dpopResult = result.value
            println("DPoP proof: ${dpopResult.dpopProof}")
            println("JWK thumbprint: ${dpopResult.jwkThumbprint}")
            assertTrue(dpopResult.dpopProof.isNotEmpty())
            assertTrue(dpopResult.jwkThumbprint.isNotEmpty())
            assertEquals(2, dpopResult.dpopProof.count { it == '.' }, "JWT should have 2 dots (3 parts)")
        }

    @Test
    fun testCreateAndVerifyDpopProof() =
        runTest {
            // Setup
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "test-client"),
                )

            val jwtService = (session.graph as JwtServiceImpl.Graph).jwtService

            // Create DPoP proof
            val createCommand = CreateDpopProofCommandImpl(execution, jwtService, defaultSecureRandom())
            val createResult =
                createCommand.execute(
                    CreateDpopProofArgs(
                        options =
                            CreateDpopProofOptions(
                                issuer = issuer,
                                httpMethod = "GET",
                                httpUrl = "https://rs.example.com/resource",
                                issuedAt = 1704067200L,
                            ),
                        publicJwk = managedKeyPair.jose.publicJwk,
                    ),
                )

            assertTrue(createResult.isOk, "Create should succeed")
            val dpopProof = createResult.value.dpopProof
            val expectedThumbprint = createResult.value.jwkThumbprint

            // Verify DPoP proof
            val verifyCommand =
                ClientVerifyDpopProofCommandImpl(
                    execution,
                    jwtService,
                    (app as com.sphereon.core.api.conf.AppConfigService.Graph).appConfigService,
                )
            val verifyResult =
                verifyCommand.execute(
                    VerifyDpopProofOptions(
                        dpopProof = dpopProof,
                        httpMethod = "GET",
                        httpUrl = "https://rs.example.com/resource",
                        expectedJwkThumbprint = expectedThumbprint,
                        now = 1704067205L, // 5 seconds later
                        allowedSigningAlgs = listOf("ES256"),
                    ),
                )

            assertTrue(verifyResult.isOk, "Verify should succeed")
            val verified = verifyResult.value

            assertEquals("dpop+jwt", verified.header.typ)
            assertEquals("ES256", verified.header.alg)
            assertEquals("GET", verified.payload.htm)
            assertEquals("https://rs.example.com/resource", verified.payload.htu)
            assertEquals(1704067200L, verified.payload.iat)
            assertEquals(expectedThumbprint, verified.jwkThumbprint)
        }

    @Test
    fun testVerifyFailsWithWrongHttpMethod() =
        runTest {
            // Setup
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "test-client"),
                )

            val jwtService = (session.graph as JwtServiceImpl.Graph).jwtService

            // Create DPoP proof for POST
            val createCommand = CreateDpopProofCommandImpl(execution, jwtService, defaultSecureRandom())
            val createResult =
                createCommand.execute(
                    CreateDpopProofArgs(
                        options =
                            CreateDpopProofOptions(
                                issuer = issuer,
                                httpMethod = "POST",
                                httpUrl = "https://as.example.com/token",
                                issuedAt = 1704067200L,
                            ),
                        publicJwk = managedKeyPair.jose.publicJwk,
                    ),
                )

            assertTrue(createResult.isOk)
            val dpopProof = createResult.value.dpopProof

            // Try to verify with GET (should fail)
            val verifyCommand =
                ClientVerifyDpopProofCommandImpl(
                    execution,
                    jwtService,
                    (app as com.sphereon.core.api.conf.AppConfigService.Graph).appConfigService,
                )
            val verifyResult =
                verifyCommand.execute(
                    VerifyDpopProofOptions(
                        dpopProof = dpopProof,
                        httpMethod = "GET", // Wrong method
                        httpUrl = "https://as.example.com/token",
                        now = 1704067205L,
                    ),
                )

            assertTrue(verifyResult.isErr, "Should fail due to method mismatch")
        }

    @Test
    fun testDpopProofWithAccessToken() =
        runTest {
            // Setup
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "test-client"),
                )

            val jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
            val accessToken = "test-access-token-12345"

            // Create DPoP proof with access token
            val createCommand = CreateDpopProofCommandImpl(execution, jwtService, defaultSecureRandom())
            val createResult =
                createCommand.execute(
                    CreateDpopProofArgs(
                        options =
                            CreateDpopProofOptions(
                                issuer = issuer,
                                httpMethod = "GET",
                                httpUrl = "https://rs.example.com/resource",
                                accessToken = accessToken,
                                issuedAt = 1704067200L,
                            ),
                        publicJwk = managedKeyPair.jose.publicJwk,
                    ),
                )

            assertTrue(createResult.isOk)
            val dpopProof = createResult.value.dpopProof

            // Verify with access token
            val verifyCommand =
                ClientVerifyDpopProofCommandImpl(
                    execution,
                    jwtService,
                    (app as com.sphereon.core.api.conf.AppConfigService.Graph).appConfigService,
                )
            val verifyResult =
                verifyCommand.execute(
                    VerifyDpopProofOptions(
                        dpopProof = dpopProof,
                        httpMethod = "GET",
                        httpUrl = "https://rs.example.com/resource",
                        accessToken = accessToken,
                        now = 1704067205L,
                    ),
                )

            assertTrue(verifyResult.isOk, "Should succeed with matching access token")
            val verified = verifyResult.value
            assertTrue(verified.payload.ath != null, "Should have ath claim")
        }
}
