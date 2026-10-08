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
import com.sphereon.crypto.core.generic.DigestAlg
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for decoy digest generation in SD-JWT
 * Based on RFC 9901 Section 5.1.4 - Decoy Digests
 *
 * Decoy digests are added to the _sd array to obscure the actual number of selective disclosures,
 * enhancing privacy by preventing analysis based on the number of claims.
 */
class DecoyTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-decoy-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "sdjwt-decoy-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
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

    /**
     * Test DecoyMode.NONE - No decoy digests added
     * The _sd array should contain exactly the number of disclosed claims
     */
    @Test
    fun testDecoyModeNone() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-none-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-none.example.com",
                        ),
                )

            // Create payload with 3 SD claims
            val payload =
                sdJwtPayload {
                    iss("https://decoy-none.example.com")
                    sub("user-decoy-none")
                    claimSd("email", "user@example.com")
                    claimSd("phone", "+1234567890")
                    claimSd("age", 30)
                }

            // Issue with DecoyMode.NONE
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.NONE),
                        ),
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            // Should have exactly 3 disclosures
            kotlin.test.assertEquals(3, sdJwtResult.disclosures.size, "Should have 3 disclosures")

            // Parse the JWT to check _sd array size
            val sdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult.sdJwt)
            val sdArray =
                sdJwt.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            // _sd array size should equal disclosure count (no decoys)
            kotlin.test.assertEquals(
                sdJwtResult.disclosures.size,
                sdArray?.size ?: 0,
                "With DecoyMode.NONE, _sd array size should equal disclosure count",
            )

            println("PASS: DecoyMode.NONE: _sd array size = ${sdArray?.size}, disclosures = ${sdJwtResult.disclosures.size}")
        }

    /**
     * Test DecoyMode.FIXED - Fixed number of decoy digests added
     */
    @Test
    fun testDecoyModeFixed() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-fixed-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-fixed.example.com",
                        ),
                )

            // Create payload with 2 SD claims
            val payload =
                sdJwtPayload {
                    iss("https://decoy-fixed.example.com")
                    sub("user-decoy-fixed")
                    claimSd("email", "user@example.com")
                    claimSd("name", "Alice")
                }

            // Issue with DecoyMode.FIXED(5) - add 5 decoy digests
            val decoyCount = 5
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.FIXED, count = decoyCount),
                        ),
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            // Should have 2 actual disclosures
            kotlin.test.assertEquals(2, sdJwtResult.disclosures.size, "Should have 2 actual disclosures")

            // Parse the JWT to check _sd array size
            val sdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult.sdJwt)
            val sdArray =
                sdJwt.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            // _sd array should be: actual disclosures + fixed decoy count
            val expectedSize = sdJwtResult.disclosures.size + decoyCount
            kotlin.test.assertEquals(
                expectedSize,
                sdArray?.size ?: 0,
                "With DecoyMode.FIXED($decoyCount), _sd array should be disclosures + decoy count",
            )

            println("PASS: DecoyMode.FIXED($decoyCount): _sd array size = ${sdArray?.size}, disclosures = ${sdJwtResult.disclosures.size}")
        }

    /**
     * Test DecoyMode.MINIMUM - Minimum number of digests in _sd array
     * Adds decoys only if needed to reach the minimum
     */
    @Test
    fun testDecoyModeMinimum() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-min-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-min.example.com",
                        ),
                )

            val minimumDigests = 10

            // Test Case 1: Few disclosures (< minimum) - should add decoys
            val payload1 =
                sdJwtPayload {
                    iss("https://decoy-min.example.com")
                    sub("user-decoy-min-1")
                    claimSd("email", "user@example.com")
                    claimSd("age", 25)
                }

            val issueArgs1 =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload1,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.MINIMUM, count = minimumDigests),
                        ),
                )

            val issueResult1 = sdJwtService.issueSdJwt(issueArgs1)
            assertTrue(issueResult1.isOk)

            val sdJwtResult1 = issueResult1.value
            val sdJwt1 =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult1.sdJwt)
            val sdArray1 =
                sdJwt1.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            // Should have minimum number of digests in _sd array
            assertTrue(
                (sdArray1?.size ?: 0) >= minimumDigests,
                "With DecoyMode.MINIMUM($minimumDigests), _sd array should have at least $minimumDigests digests",
            )

            println("PASS: DecoyMode.MINIMUM($minimumDigests) with 2 disclosures: _sd array size = ${sdArray1?.size}")

            // Test Case 2: Many disclosures (> minimum) - should not add decoys
            val payload2 =
                sdJwtPayload {
                    iss("https://decoy-min.example.com")
                    sub("user-decoy-min-2")
                    claimSd("claim1", "value1")
                    claimSd("claim2", "value2")
                    claimSd("claim3", "value3")
                    claimSd("claim4", "value4")
                    claimSd("claim5", "value5")
                    claimSd("claim6", "value6")
                    claimSd("claim7", "value7")
                    claimSd("claim8", "value8")
                    claimSd("claim9", "value9")
                    claimSd("claim10", "value10")
                    claimSd("claim11", "value11")
                    claimSd("claim12", "value12")
                }

            val issueArgs2 =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload2,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.MINIMUM, count = minimumDigests),
                        ),
                )

            val issueResult2 = sdJwtService.issueSdJwt(issueArgs2)
            assertTrue(issueResult2.isOk)

            val sdJwtResult2 = issueResult2.value
            kotlin.test.assertEquals(12, sdJwtResult2.disclosures.size, "Should have 12 disclosures")

            val sdJwt2 =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult2.sdJwt)
            val sdArray2 =
                sdJwt2.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            // Should have exactly the number of actual disclosures (no decoys needed)
            kotlin.test.assertEquals(
                sdJwtResult2.disclosures.size,
                sdArray2?.size ?: 0,
                "With DecoyMode.MINIMUM when disclosures >= minimum, no decoys should be added",
            )

            println("PASS: DecoyMode.MINIMUM($minimumDigests) with 12 disclosures: _sd array size = ${sdArray2?.size}")
        }

    /**
     * Test DecoyMode.RANDOM - Random number of decoy digests
     */
    @Test
    fun testDecoyModeRandom() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-random-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-random.example.com",
                        ),
                )

            val payload =
                sdJwtPayload {
                    iss("https://decoy-random.example.com")
                    sub("user-decoy-random")
                    claimSd("email", "user@example.com")
                    claimSd("phone", "+1234567890")
                }

            val maxDecoys = 10
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.RANDOM, count = maxDecoys),
                        ),
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            val actualDisclosures = sdJwtResult.disclosures.size

            val sdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult.sdJwt)
            val sdArray =
                sdJwt.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray
            val totalDigests = sdArray?.size ?: 0

            // _sd array should be at least the number of actual disclosures
            assertTrue(
                totalDigests >= actualDisclosures,
                "Random decoys: _sd array size should be >= actual disclosures",
            )

            // _sd array should not exceed actual + max random
            assertTrue(
                totalDigests <= actualDisclosures + maxDecoys,
                "Random decoys: _sd array size should be <= actual + maxRandomDecoys",
            )

            println("PASS: DecoyMode.RANDOM(max=$maxDecoys): _sd array size = $totalDigests, disclosures = $actualDisclosures")
        }

    /**
     * Test that decoy digests are properly formatted base64url strings
     */
    @Test
    fun testDecoyDigestsAreValid() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-valid-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-valid.example.com",
                        ),
                )

            val payload =
                sdJwtPayload {
                    iss("https://decoy-valid.example.com")
                    sub("user-decoy-valid")
                    claimSd("email", "user@example.com")
                }

            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.FIXED, count = 5),
                        ),
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(issueResult.value.sdJwt)
            val sdArray =
                sdJwt.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            assertNotNull(sdArray, "_sd array should be present")

            // All digests in _sd array should be valid base64url strings
            sdArray.forEach { digest ->
                val digestStr = digest.jsonPrimitive.content
                assertTrue(
                    isBase64Url(digestStr),
                    "Each digest in _sd array should be base64url encoded",
                )
            }

            println("PASS: All ${sdArray.size} digests are valid base64url strings")
        }

    /**
     * Test that decoy digests do not match any actual disclosure digests
     */
    @Test
    fun testDecoyDigestsDoNotMatchDisclosures() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "decoy-nomatch-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://decoy-nomatch.example.com",
                        ),
                )

            val payload =
                sdJwtPayload {
                    iss("https://decoy-nomatch.example.com")
                    sub("user-decoy-nomatch")
                    claimSd("email", "user@example.com")
                    claimSd("phone", "+1234567890")
                }

            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec =
                        com.sphereon.sdjwt.SdJwtSpec(
                            digestAlg = DigestAlg.SHA256,
                            decoyConfig = com.sphereon.sdjwt.DecoyConfig(mode = com.sphereon.sdjwt.DecoyMode.FIXED, count = 3),
                        ),
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            val sdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(sdJwtResult.sdJwt)
            val sdArray =
                sdJwt.value.payload.undisclosedPayload["_sd"]
                    ?.jsonArray

            assertNotNull(sdArray, "_sd array should be present")

            // Calculate actual disclosure digests
            val actualDigests =
                sdJwtResult.disclosures
                    .map {
                        com.sphereon.sdjwt.DisclosureDigest.Companion
                            .calculate(DigestAlg.SHA256, it)
                            .value
                    }.toSet()

            // Count digests in _sd array
            val totalDigests = sdArray.size
            val matchingDigests =
                sdArray.count { digest ->
                    actualDigests.contains(digest.jsonPrimitive.content)
                }

            // Number of matching digests should equal actual disclosures
            kotlin.test.assertEquals(
                actualDigests.size,
                matchingDigests,
                "Only actual disclosure digests should match",
            )

            // Remaining digests are decoys
            val decoyCount = totalDigests - matchingDigests
            assertEquals(3, decoyCount, "Should have 3 decoy digests")

            println("PASS: Decoy test: Total=$totalDigests, Actual=$matchingDigests, Decoys=$decoyCount")
        }

    /**
     * Check if a string is valid base64url encoding
     */
    private fun isBase64Url(str: String): Boolean = str.matches(Regex("^[A-Za-z0-9_-]+$"))
}
