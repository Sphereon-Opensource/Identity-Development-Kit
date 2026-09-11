/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.issuer.config.ResolveWalletProviderTrustArgs
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialRequestProofBatchVerifierTest {
    @Test
    fun batchProofsWithSharedNonceConsumeOnceForWholeRequest() =
        runTest {
            val nonceStore = RecordingNonceStore(validNonces = mutableSetOf("nonce-1"))
            val proofVerifier = RecordingProofVerifier()
            val verifier = CredentialRequestProofBatchVerifier(setOf(proofVerifier), NonceManager(nonceStore))

            val result =
                verifier.verify(
                    proofType = "jwt",
                    proofValues = listOf(JsonPrimitive("nonce-1"), JsonPrimitive("nonce-1")),
                    audience = "https://issuer.example.com",
                    expectedClientId = "wallet-client",
                    credentialConfigId = "UniversityDegree",
                    walletProviderTrustArgs = null,
                )

            assertTrue(result.isOk, "batch proof verification should succeed: ${result.errorOrNull()?.message?.defaultMessage}")
            assertEquals(2, result.value.size)
            assertEquals(listOf("nonce-1"), nonceStore.consumed)
            assertEquals(
                listOf(
                    ProofCall(nonce = "nonce-1", expectedNonce = "nonce-1", consumeNonce = false),
                    ProofCall(nonce = "nonce-1", expectedNonce = "nonce-1", consumeNonce = false),
                ),
                proofVerifier.calls,
            )
        }

    @Test
    fun batchProofsWithDifferentNoncesFailBeforeConsuming() =
        runTest {
            val nonceStore = RecordingNonceStore(validNonces = mutableSetOf("nonce-1", "nonce-2"))
            val proofVerifier = RecordingProofVerifier()
            val verifier = CredentialRequestProofBatchVerifier(setOf(proofVerifier), NonceManager(nonceStore))

            val result =
                verifier.verify(
                    proofType = "jwt",
                    proofValues = listOf(JsonPrimitive("nonce-1"), JsonPrimitive("nonce-2")),
                    audience = "https://issuer.example.com",
                    expectedClientId = "wallet-client",
                    credentialConfigId = "UniversityDegree",
                    walletProviderTrustArgs = null,
                )

            assertTrue(result.isErr, "mixed batch nonces must fail")
            assertEquals("invalid_nonce", result.error.code)
            assertEquals(emptyList(), nonceStore.consumed)
            assertEquals(emptyList(), proofVerifier.calls)
        }

    @Test
    fun singleProofKeepsVerifierOwnedNonceConsumption() =
        runTest {
            val nonceStore = RecordingNonceStore(validNonces = mutableSetOf("nonce-1"))
            val proofVerifier = RecordingProofVerifier()
            val verifier = CredentialRequestProofBatchVerifier(setOf(proofVerifier), NonceManager(nonceStore))

            val result =
                verifier.verify(
                    proofType = "jwt",
                    proofValues = listOf(JsonPrimitive("nonce-1")),
                    audience = "https://issuer.example.com",
                    expectedClientId = "wallet-client",
                    credentialConfigId = "UniversityDegree",
                    walletProviderTrustArgs = null,
                )

            assertTrue(result.isOk, "single proof verification should succeed")
            assertEquals(emptyList(), nonceStore.consumed)
            assertEquals(listOf(ProofCall(nonce = "nonce-1", expectedNonce = null, consumeNonce = true)), proofVerifier.calls)
        }

    private data class ProofCall(
        val nonce: String,
        val expectedNonce: String?,
        val consumeNonce: Boolean,
    )

    private class RecordingProofVerifier : ProofVerifier {
        val calls = mutableListOf<ProofCall>()
        override val supportedProofType: String = "jwt"

        override suspend fun verify(
            proofValue: JsonElement,
            expectedAudience: String,
            expectedClientId: String?,
            credentialConfigId: String,
            walletProviderTrustArgs: ResolveWalletProviderTrustArgs?,
            proofTypeSupported: ProofTypeSupported?,
            expectedNonce: String?,
            consumeNonce: Boolean,
        ): IdkResult<VerifiedProof, IdkError> {
            val nonce = proofValue.jsonPrimitive.content
            calls += ProofCall(nonce = nonce, expectedNonce = expectedNonce, consumeNonce = consumeNonce)
            return Ok(VerifiedProof(holderBindingKey = buildJsonObject {}))
        }

        override fun extractNonce(proofValue: JsonElement): IdkResult<String?, IdkError> = Ok(proofValue.jsonPrimitive.content)
    }

    private class RecordingNonceStore(
        private val validNonces: MutableSet<String>,
    ) : CredentialNonceStore {
        val consumed = mutableListOf<String>()

        override suspend fun create(
            nonce: String,
            ttlSeconds: Long,
        ): IdkResult<NonceEntry, IdkError> = Ok(NonceEntry(nonce = nonce, createdAt = 0, expiresAt = Long.MAX_VALUE))

        override suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> {
            consumed += nonce
            return Ok(
                if (validNonces.remove(nonce)) {
                    NonceEntry(nonce = nonce, createdAt = 0, expiresAt = Long.MAX_VALUE)
                } else {
                    null
                },
            )
        }
    }
}
