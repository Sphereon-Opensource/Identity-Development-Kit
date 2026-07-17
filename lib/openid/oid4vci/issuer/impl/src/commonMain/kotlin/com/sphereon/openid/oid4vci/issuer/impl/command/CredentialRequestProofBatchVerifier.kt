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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement

/**
 * Verifies the Credential Request proof set and owns request-scoped nonce consumption.
 *
 * OID4VCI batch issuance sends multiple proofs in one Credential Request. Those proofs share
 * the request c_nonce, so the issuer consumes that nonce once after all proofs have verified.
 */
internal class CredentialRequestProofBatchVerifier(
    private val proofVerifiers: Set<ProofVerifier>,
    private val nonceManager: NonceManager,
) {
    suspend fun verify(
        proofType: String,
        proofValues: List<JsonElement>,
        audience: String,
        expectedClientId: String?,
        credentialConfigId: String,
        proofTypeSupported: ProofTypeSupported? = null,
    ): IdkResult<List<VerifiedProof>, IdkError> {
        val verifier = proofVerifierFor(proofType).getOrElse { return Err(it) }
        if (proofValues.size <= 1) {
            val proofValue =
                proofValues.firstOrNull()
                    ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Credential request proofs must not be empty"))
            return Ok(
                listOf(
                    verifier
                        .verify(
                            proofValue = proofValue,
                            expectedAudience = audience,
                            expectedClientId = expectedClientId,
                            credentialConfigId = credentialConfigId,
                            proofTypeSupported = proofTypeSupported,
                        ).getOrElse { return Err(it) },
                ),
            )
        }

        val requestNonce = batchRequestNonce(verifier, proofValues).getOrElse { return Err(it) }
        val results =
            coroutineScope {
                proofValues
                    .map { proofValue ->
                        async {
                            verifier.verify(
                                proofValue = proofValue,
                                expectedAudience = audience,
                                expectedClientId = expectedClientId,
                                credentialConfigId = credentialConfigId,
                                proofTypeSupported = proofTypeSupported,
                                expectedNonce = requestNonce,
                                consumeNonce = false,
                            )
                        }
                    }.awaitAll()
            }
        val verifiedProofs = results.map { result -> result.getOrElse { return Err(it) } }

        if (requestNonce != null) {
            val nonceEntry = nonceManager.consume(requestNonce).getOrElse { return Err(it) }
            if (nonceEntry == null) {
                return Err(IdkError.fromString(code = "invalid_nonce", message = "Invalid credential request: c_nonce is invalid or expired"))
            }
        }
        return Ok(verifiedProofs)
    }

    private fun batchRequestNonce(
        verifier: ProofVerifier,
        proofValues: List<JsonElement>,
    ): IdkResult<String?, IdkError> {
        val nonces =
            proofValues.map { proofValue ->
                verifier.extractNonce(proofValue).getOrElse { return Err(it) }
            }
        val requestNonce = nonces.firstOrNull()
        if (nonces.any { it != requestNonce }) {
            return Err(
                IdkError.fromString(
                    code = "invalid_nonce",
                    message = "Invalid credential request: all batch proofs must carry the same c_nonce",
                ),
            )
        }
        return Ok(requestNonce)
    }

    private fun proofVerifierFor(proofType: String): IdkResult<ProofVerifier, IdkError> {
        val verifier =
            proofVerifiers.firstOrNull { it.supportedProofType == proofType }
                ?: return Err(IdkError.fromString(code = "UNSUPPORTED_PROOF_TYPE", message = "Proof type '$proofType' is not supported"))
        return Ok(verifier)
    }
}
