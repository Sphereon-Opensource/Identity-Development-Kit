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

package com.sphereon.crypto.dataintegrity

import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VerifyProofAlgorithmTest {
    private val unsecured =
        buildJsonObject {
            put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
            put("id", JsonPrimitive("urn:test:1"))
        }

    @Test
    fun singleProofRoundTrip() =
        runTest {
            val env = makeTestEnvironment()
            val secured = env.addProof.addProofs(unsecured, listOf(option(null))).value
            val result = env.verifyProof.verify(secured).value
            assertTrue(result.verified, "errors=${result.errors}")
            assertEquals(unsecured, result.verifiedDocument)
        }

    @Test
    fun verifierOwnedResolutionPolicyReachesCryptosuiteVerifier() =
        runTest {
            var capturedPolicy: VerificationMethodResolutionPolicy? = null
            val env =
                makeTestEnvironment(
                    verifiers =
                        setOf(
                            TestCryptosuiteVerifier(
                                onResolutionPolicy = { capturedPolicy = it },
                            ),
                        ),
                )
            val secured = env.addProof.addProofs(unsecured, listOf(option(null))).value
            val policy = VerificationMethodResolutionPolicy.empty()

            val result =
                env.verifyProof
                    .verify(
                        securedDocument = secured,
                        verificationMethodResolutionPolicy = policy,
                    ).value

            assertTrue(result.verified, "errors=${result.errors}")
            assertSame(policy, capturedPolicy)
        }

    @Test
    fun verifyRejectsTamperedDocument() =
        runTest {
            val env = makeTestEnvironment()
            val secured = env.addProof.addProofs(unsecured, listOf(option(null))).value
            val tampered = JsonObject(secured + ("id" to JsonPrimitive("urn:test:tampered")))
            val result = env.verifyProof.verify(tampered).value
            assertFalse(result.verified)
            assertTrue(result.errors.isNotEmpty())
        }

    @Test
    fun verifyRejectsTamperedChainLink() =
        runTest {
            val env = makeTestEnvironment()
            val secured =
                env.addProof
                    .addProofs(
                        unsecured,
                        listOf(
                            option("urn:proof:1"),
                            option("urn:proof:2", previousProof = listOf("urn:proof:1")),
                            option("urn:proof:3", previousProof = listOf("urn:proof:2")),
                        ),
                    ).value

            // Tamper the middle proof's proofValue.
            val proofArray = secured["proof"] as JsonArray
            val tamperedArray =
                JsonArray(
                    proofArray.mapIndexed { idx, element ->
                        if (idx == 1) {
                            val obj = element as JsonObject
                            JsonObject(obj + ("proofValue" to JsonPrimitive("zTAMPERED")))
                        } else {
                            element
                        }
                    },
                )
            val tampered = JsonObject(secured + ("proof" to tamperedArray))

            val result = env.verifyProof.verify(tampered).value
            assertFalse(result.verified, "tampered chain link must fail verification")
        }

    @Test
    fun verifyRejectsUnknownCryptosuite() =
        runTest {
            val env = makeTestEnvironment()
            val secured = env.addProof.addProofs(unsecured, listOf(option(null))).value
            // Substitute the cryptosuite to one not in the registry.
            val proof = secured["proof"] as JsonObject
            val swapped = JsonObject(proof + ("cryptosuite" to JsonPrimitive("does-not-exist")))
            val tampered = JsonObject(secured + ("proof" to swapped))

            val result = env.verifyProof.verify(tampered).value
            assertFalse(result.verified)
            assertTrue(result.errors.any { "does-not-exist" in it })
        }

    @Test
    fun expectedProofPurposeMismatchFailsAllProofs() =
        runTest {
            val env = makeTestEnvironment()
            val secured = env.addProof.addProofs(unsecured, listOf(option(null))).value
            val result =
                env.verifyProof
                    .verify(
                        secured,
                        expectedProofPurpose = ProofPurpose.AUTHENTICATION.value,
                    ).value
            assertFalse(result.verified, "purpose mismatch must fail per W3C VC-DI 1.0 §4.4 step 5")
        }

    @Test
    fun missingProofFieldYieldsParsingError() =
        runTest {
            val env = makeTestEnvironment()
            val result = env.verifyProof.verify(unsecured).value
            assertFalse(result.verified)
            assertTrue(result.errors.any { it.startsWith("PARSING_ERROR") })
        }

    private fun option(
        id: String?,
        previousProof: List<String>? = null
    ) = ProofOptions(
        cryptosuite = TEST_CRYPTOSUITE_ID,
        verificationMethod = "did:test:issuer#key-1",
        proofPurpose = ProofPurpose.ASSERTION_METHOD,
        signingKeyRef = "test-key-1",
        proofId = id,
        previousProof = previousProof,
    )
}
