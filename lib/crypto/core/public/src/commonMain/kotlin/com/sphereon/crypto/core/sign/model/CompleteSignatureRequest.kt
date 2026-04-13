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

/**
 * Request for two-step signing step 2: complete the signature with an externally-computed value.
 *
 * Wraps the [DigestResponse] from step 1 together with the externally-signed value.
 *
 * Usage:
 * ```kotlin
 * val digest = signatureService.createDigest(request).getOrThrow()
 * val signatureValue = externalSigner.sign(digest.digestToSign)
 * val result = signatureService.completeSignature(CompleteSignatureRequest(digest, signatureValue))
 * ```
 *
 * @property digestResponse The digest response from [com.sphereon.crypto.core.sign.SignatureService.createDigest]
 * @property signatureValue The externally computed signature value (e.g. from HSM or smart card)
 */
data class CompleteSignatureRequest(
    val digestResponse: DigestResponse,
    val signatureValue: ByteArray,
) {
    /** Session identifier from the digest creation step */
    val sessionId: String get() = digestResponse.sessionId

    /** Intermediate document state from the digest creation step */
    val signatureDocumentBytes: ByteArray get() = digestResponse.signatureDocumentBytes

    /** Original document name from DigestResponse */
    val documentName: String? get() = digestResponse.documentName

    /** Key information - carried over from createDigest */
    val keyInfo: KeyInfoType<*> get() = digestResponse.keyInfo

    /** Signature parameters - carried over from createDigest */
    val parameters: SignatureParameters get() = digestResponse.parameters

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompleteSignatureRequest) return false
        if (digestResponse != other.digestResponse) return false
        if (!signatureValue.contentEquals(other.signatureValue)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = digestResponse.hashCode()
        result = 31 * result + signatureValue.contentHashCode()
        return result
    }
}
