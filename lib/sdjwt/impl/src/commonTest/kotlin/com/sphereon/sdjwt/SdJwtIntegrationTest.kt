/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.sdjwt

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.sdjwt.dsl.sdJwtPayload
import com.sphereon.sdjwt.testutil.createSdJwtTestAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SdJwtIntegrationTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "sdjwt-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app
        val softwareKmsProvider =
            (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                config,
                session.asCoreApiServiceGraph().serviceExecution,
            )

        // Get KeyManagerService and SdJwtService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        sdJwtService = (session.graph as com.sphereon.sdjwt.SdJwtServiceImpl.Graph).sdJwtService
    }

    @Test
    fun testCreateAndVerifyBasicSdJwt() =
        runTest {
            // Generate key pair for issuer
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Create issuer identifier
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://example.com/issuer",
                        ),
                )

            // Create payload using the payload builder with SD-JWT extensions
            // Make email and age selectively disclosable using claimSd()
            val payload =
                sdJwtPayload {
                    iss("https://example.com/issuer")
                    sub("user-123")
                    claimSd("email", "user@example.com") // Selectively disclosable
                    claimSd("age", "25") // Selectively disclosable
                    claim("verified", true) // Not selectively disclosable
                }

            // Issue SD-JWT
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            if (!issueResult.isOk) {
                throw AssertionError("Failed to issue SD-JWT: ${issueResult.error}")
            }

            val sdJwtResult = issueResult.value
            assertNotNull(sdJwtResult)
            assertNotNull(sdJwtResult.sdJwt)

            println("Issued SD-JWT: ${sdJwtResult.sdJwt}")
            println("Disclosures: ${sdJwtResult.disclosures.size}")

            // Verify we have 2 disclosures (email and age)
            kotlin.test.assertEquals(2, sdJwtResult.disclosures.size, "Should have 2 disclosures")

            // Verify SD-JWT
            val verifyArgs =
                com.sphereon.sdjwt.VerifySdJwtArgs(
                    sdJwt = sdJwtResult.sdJwt,
                    identifier = issuer,
                )

            val verifyResult = sdJwtService.verifySdJwt(verifyArgs)
            if (!verifyResult.isOk) {
                throw AssertionError("Failed to verify SD-JWT: ${verifyResult.error}")
            }

            val verificationResult = verifyResult.value
            println("Verification result: ${verificationResult.isValid}")
            println("Signature valid: ${verificationResult.signatureValid}")
            println("Disclosures valid: ${verificationResult.disclosuresValid}")

            assertTrue(verificationResult.isValid, "SD-JWT verification should succeed")
            assertTrue(verificationResult.signatureValid, "Signature should be valid")
            assertTrue(verificationResult.disclosuresValid, "Disclosures should be valid")

            // Verify that the full payload includes the disclosed claims
            val fullPayload = verificationResult.sdJwt.payload.fullPayload
            assertEquals("user@example.com", fullPayload["email"]?.toString()?.trim('"'))
            assertEquals("25", fullPayload["age"]?.toString()?.trim('"'))
        }

    @Test
    fun testVerifyTamperedSdJwtFails() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test-tamper",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://example.com/issuer",
                        ),
                )

            // Create payload
            val payload =
                sdJwtPayload {
                    iss("https://example.com/issuer")
                    sub("user-789")
                    claimSd("data", "sensitive-data")
                }

            // Issue SD-JWT
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            val originalSdJwt = sdJwtResult.sdJwt

            // Tamper with the SD-JWT by appending text
            val tamperedSdJwt = originalSdJwt + "XXX"

            // Verify tampered SD-JWT should fail
            val verifyArgs =
                com.sphereon.sdjwt.VerifySdJwtArgs(
                    sdJwt = tamperedSdJwt,
                    identifier = issuer,
                )

            val verifyResult = sdJwtService.verifySdJwt(verifyArgs)

            // Verification should either return error or return result with isValid=false
            if (verifyResult.isOk) {
                val verificationResult = verifyResult.value
                assertFalse(verificationResult.isValid, "Tampered SD-JWT should not verify successfully")
            } else {
                // Expected: verification error
                assertNotNull(verifyResult.error)
            }
        }

    @Test
    fun testSdJwtWithMultipleAlgorithms() =
        runTest {
            val algorithms =
                listOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.ECDSA_SHA512,
                    SignatureAlgorithm.RSA_SHA256,
                )

            algorithms.forEach { alg ->
                println("\n=== Testing SD-JWT with algorithm: $alg ===")

                // Generate key pair
                val managedKeyPair = keyManagerService.generateKeyAsync(alg = alg)
                val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

                val algName = alg.jose?.value ?: alg::class.simpleName ?: alg.toString()
                val issuer =
                    ManagedOptsKeyInfo(
                        identifier = keyInfo,
                        context =
                            IdentifierContext(
                                clientId = "test-$algName",
                                clientIdScheme = "jwt_vc_json",
                                issuer = "https://example.com/issuer-$algName",
                            ),
                    )

                // Create payload
                val payload =
                    sdJwtPayload {
                        iss("https://example.com/issuer-$algName")
                        sub("user-$algName")
                        claimSd("secret", "classified-data")
                    }

                // Issue SD-JWT
                val issueArgs =
                    com.sphereon.sdjwt.IssueSdJwtArgs(
                        issuer = issuer,
                        payload = payload,
                    )

                val issueResult = sdJwtService.issueSdJwt(issueArgs)
                assertTrue(issueResult.isOk, "SD-JWT issuance with $alg should succeed")

                val sdJwtResult = issueResult.value
                assertNotNull(sdJwtResult)

                // Verify SD-JWT
                val verifyArgs =
                    com.sphereon.sdjwt.VerifySdJwtArgs(
                        sdJwt = sdJwtResult.sdJwt,
                        identifier = issuer,
                    )

                val verifyResult = sdJwtService.verifySdJwt(verifyArgs)
                assertTrue(verifyResult.isOk, "SD-JWT verification with $alg should succeed")

                val verificationResult = verifyResult.value
                assertTrue(verificationResult.isValid, "SD-JWT with $alg should be valid")

                println("PASS: Algorithm $alg passed")
            }
        }
}
