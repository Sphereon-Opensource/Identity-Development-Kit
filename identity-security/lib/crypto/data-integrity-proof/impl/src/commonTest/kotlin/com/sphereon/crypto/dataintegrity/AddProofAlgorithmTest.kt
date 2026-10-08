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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddProofAlgorithmTest {
    private val unsecured =
        buildJsonObject {
            put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
            put("id", JsonPrimitive("urn:test:1"))
        }

    @Test
    fun singleProof() =
        runTest {
            val env = makeTestEnvironment()
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(option(id = null)),
                )
            assertTrue(result.isOk, "addProof should succeed: ${if (result.isErr) result.error else ""}")
            val secured = result.value
            val proof = secured["proof"]
            assertNotNull(proof)
            assertTrue(proof is JsonObject, "single proof must be encoded as object, got ${proof::class}")
        }

    @Test
    fun proofOptionExtensionsAreTopLevelProofProperties() =
        runTest {
            val env = makeTestEnvironment()
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(
                        option(id = null).copy(
                            additionalProofProperties =
                                buildJsonObject {
                                    put("suiteParameter", JsonPrimitive("suite-value"))
                                },
                        ),
                    ),
                )

            assertTrue(result.isOk, "addProof should succeed: ${if (result.isErr) result.error else ""}")
            val proof = result.value["proof"]
            assertTrue(proof is JsonObject)
            assertEquals(JsonPrimitive("suite-value"), proof["suiteParameter"])
            assertTrue("additionalProofProperties" !in proof)
        }

    @Test
    fun proofOptionExtensionsCannotShadowTypedProofProperties() =
        runTest {
            val env = makeTestEnvironment()
            var failure: IllegalArgumentException? = null
            try {
                env.addProof.addProofs(
                    unsecured,
                    listOf(
                        option(id = null).copy(
                            additionalProofProperties =
                                buildJsonObject {
                                    put("proofValue", JsonPrimitive("attacker-controlled"))
                                },
                        ),
                    ),
                )
            } catch (expected: IllegalArgumentException) {
                failure = expected
            }
            assertNotNull(failure)
        }

    @Test
    fun domainSetIsPropagatedToGeneratedProof() =
        runTest {
            val env = makeTestEnvironment()
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(option(id = null).copy(domainSet = listOf("urn:second", "urn:first"))),
                )

            assertTrue(result.isOk, "addProof should succeed: ${if (result.isErr) result.error else ""}")
            val proof = result.value["proof"] as JsonObject
            assertEquals(JsonArray(listOf(JsonPrimitive("urn:second"), JsonPrimitive("urn:first"))), proof["domain"])
        }

    @Test
    fun proofSet() =
        runTest {
            val env =
                makeTestEnvironment(
                    creators =
                        setOf(
                            TestCryptosuiteCreator(TEST_CRYPTOSUITE_ID),
                            TestCryptosuiteCreator(TEST_CRYPTOSUITE_ID_ALT),
                        ),
                    verifiers =
                        setOf(
                            TestCryptosuiteVerifier(TEST_CRYPTOSUITE_ID),
                            TestCryptosuiteVerifier(TEST_CRYPTOSUITE_ID_ALT),
                        ),
                )
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(
                        option(id = null, suiteId = TEST_CRYPTOSUITE_ID),
                        option(id = null, suiteId = TEST_CRYPTOSUITE_ID_ALT),
                    ),
                )
            assertTrue(result.isOk)
            val proof = result.value["proof"]
            assertNotNull(proof)
            assertTrue(proof is JsonArray, "proof set must be encoded as array, got ${proof::class}")
            assertEquals(2, proof.size)
        }

    @Test
    fun proofChainBindsLinks() =
        runTest {
            val env = makeTestEnvironment()
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(
                        option(id = "urn:proof:1"),
                        option(id = "urn:proof:2", previousProof = listOf("urn:proof:1")),
                        option(id = "urn:proof:3", previousProof = listOf("urn:proof:2")),
                    ),
                )
            assertTrue(result.isOk, "chain should succeed: ${if (result.isErr) result.error else ""}")
            val proof = result.value["proof"]
            assertTrue(proof is JsonArray)
            assertEquals(3, proof.size)
        }

    @Test
    fun proofChainRejectsUnknownPreviousProof() =
        runTest {
            val env = makeTestEnvironment()
            val result =
                env.addProof.addProofs(
                    unsecured,
                    listOf(
                        option(id = "urn:proof:1"),
                        option(id = "urn:proof:2", previousProof = listOf("urn:not-here")),
                    ),
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.code == "PROOF_GENERATION_ERROR",
                "expected PROOF_GENERATION_ERROR, got ${result.error.code}",
            )
        }

    @Test
    fun rejectsAlreadySecuredInput() =
        runTest {
            val env = makeTestEnvironment()
            val secured = env.addProof.addProofs(unsecured, listOf(option(id = null))).value
            val again = env.addProof.addProofs(secured, listOf(option(id = null)))
            assertTrue(again.isErr, "should refuse to re-sign already-secured input")
        }

    private fun option(
        id: String?,
        suiteId: String = TEST_CRYPTOSUITE_ID,
        previousProof: List<String>? = null,
    ) = ProofOptions(
        cryptosuite = suiteId,
        verificationMethod = "did:test:issuer#key-1",
        proofPurpose = ProofPurpose.ASSERTION_METHOD,
        signingKeyRef = "test-key-1",
        proofId = id,
        previousProof = previousProof,
    )
}
