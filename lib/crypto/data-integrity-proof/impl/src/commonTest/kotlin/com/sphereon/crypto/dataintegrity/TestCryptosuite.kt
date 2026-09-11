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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import kotlinx.serialization.json.JsonObject

/**
 * Deterministic test cryptosuite. The "signature" is multibase base64url of
 * `<suite>:<JCS(document)>` — any tampering of the document or the proof
 * itself is detectable. Avoids pulling in real crypto so the algorithm
 * orchestrator can be tested in isolation.
 */
internal const val TEST_CRYPTOSUITE_ID = "test-jcs-2026"
internal const val TEST_CRYPTOSUITE_ID_ALT = "test-jcs-2026-alt"

private fun deterministicSignature(
    document: JsonObject,
    suiteId: String
): String {
    val payload = (suiteId + ":" + Jcs.canonicalString(document)).encodeToByteArray()
    // Multibase prefix 'u' = base64url-no-pad per W3C multibase table.
    return "u" + payload.encodeToBase64Url()
}

internal class TestCryptosuiteCreator(
    override val cryptosuiteId: String = TEST_CRYPTOSUITE_ID,
) : DataIntegrityCryptosuiteCreator {
    override suspend fun createProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
    ): IdkResult<DataIntegrityProof, IdkError> =
        Ok(
            DataIntegrityProof(
                type = DataIntegrityProof.TYPE_DATA_INTEGRITY,
                cryptosuite = cryptosuiteId,
                proofPurpose = options.proofPurpose,
                verificationMethod = options.verificationMethod,
                proofValue = deterministicSignature(unsecuredDocument, cryptosuiteId),
                id = options.proofId,
                created = options.created ?: "2026-04-29T12:00:00Z",
                expires = options.expires,
                domain = options.domain,
                domainSet = options.domainSet,
                challenge = options.challenge,
                nonce = options.nonce,
                previousProof = options.previousProof,
                additionalProofProperties = options.additionalProofProperties ?: JsonObject(emptyMap()),
            ),
        )
}

internal class TestCryptosuiteVerifier(
    override val cryptosuiteId: String = TEST_CRYPTOSUITE_ID,
    private val onResolutionPolicy: ((VerificationMethodResolutionPolicy) -> Unit)? = null,
) : DataIntegrityCryptosuiteVerifier {
    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
    ): IdkResult<CryptosuiteVerification, IdkError> {
        onResolutionPolicy?.invoke(verificationMethodResolutionPolicy)
        val expected = deterministicSignature(unsecuredDocument, cryptosuiteId)
        val ok = expected == proof.proofValue
        return Ok(
            CryptosuiteVerification(
                verified = ok,
                verifiedDocument = if (ok) unsecuredDocument else null,
                errors = if (ok) emptyList() else listOf("signature mismatch"),
            ),
        )
    }
}
