/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.sign.model

import com.sphereon.crypto.core.KeyInfoType
import kotlinx.datetime.Instant

/**
 * Response from two-step signing step 1: contains the digest to be signed externally.
 *
 * @property digestToSign The digest bytes to be signed externally (e.g. by HSM or smart card)
 * @property signatureDocumentBytes Intermediate document state needed for completion
 * @property documentName The document name (used for deterministic ID in PAdES)
 * @property sessionId Session identifier for completing the signature
 * @property signingDate The signing date used during digest creation
 * @property keyInfo Key information preserved from createDigest for use in completeSignature
 * @property parameters Signature parameters with signingDate already set
 */
data class DigestResponse(
    val digestToSign: ByteArray = ByteArray(0),
    val signatureDocumentBytes: ByteArray,
    val documentName: String? = null,
    val sessionId: String,
    val signingDate: Instant = Instant.DISTANT_PAST,
    val keyInfo: KeyInfoType<*>,
    val parameters: SignatureParameters
) {

    /**
     * Creates a [CompleteSignatureRequest] from this digest response.
     *
     * All required information is already contained in the digest response -
     * the developer only needs to provide the externally computed signature value.
     *
     * Usage:
     * ```kotlin
     * val digest = signatureService.createDigest(request).getOrThrow()
     * val signatureValue = externalSigner.sign(digest.digestToSign)
     * val result = signatureService.completeSignature(CompleteSignatureRequest(digest, signatureValue))
     * ```
     *
     * @param signatureValue The externally computed signature value (e.g., from HSM or smart card)
     * @return [CompleteSignatureRequest] ready to be passed to [com.sphereon.crypto.core.sign.SignatureService.completeSignature]
     */
    fun toCompleteSignatureRequest(signatureValue: ByteArray) = CompleteSignatureRequest(
        digestResponse = this,
        signatureValue = signatureValue
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DigestResponse) return false
        if (!digestToSign.contentEquals(other.digestToSign)) return false
        if (!signatureDocumentBytes.contentEquals(other.signatureDocumentBytes)) return false
        if (documentName != other.documentName) return false
        if (sessionId != other.sessionId) return false
        if (signingDate != other.signingDate) return false
        if (keyInfo != other.keyInfo) return false
        if (parameters != other.parameters) return false
        return true
    }

    override fun hashCode(): Int {
        var result = digestToSign.contentHashCode()
        result = 31 * result + signatureDocumentBytes.contentHashCode()
        result = 31 * result + (documentName?.hashCode() ?: 0)
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + signingDate.hashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }
}
