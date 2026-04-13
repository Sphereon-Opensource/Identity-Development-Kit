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

import kotlinx.datetime.Instant
import com.sphereon.core.compat.JsExportCompat

/**
 * Output of a signing operation.
 *
 * IDK provides a base [SignOutputData] implementation for RAW/JWS/COSE.
 * EDK provides a richer `EidasSignOutput` with certificate and timestamp info.
 *
 * Since [com.sphereon.core.api.IdkResult] is covariant (`out V`),
 * `IdkResult<EidasSignOutput, IdkError>` is assignable to `IdkResult<SignOutput, IdkError>`.
 *
 * @property signedData The signed document or signature bytes
 * @property signatureLevel The signature level applied
 * @property signingTime The time when the signature was created
 * @property signatureId Unique identifier for this signature
 * @property name Optional name for the output document
 * @property mimeType MIME type of the signed document
 */
interface SignOutput {
    val signedData: ByteArray
    val signatureLevel: SignatureLevel
    val signingTime: Instant
    val signatureId: String?
    val name: String?
    val mimeType: String?
}

/**
 * Default data class implementation of [SignOutput] for RAW/JWS/COSE signing in IDK.
 */
data class SignOutputData(
    override val signedData: ByteArray,
    override val signatureLevel: SignatureLevel,
    override val signingTime: Instant,
    override val signatureId: String? = null,
    override val name: String? = null,
    override val mimeType: String? = null,
) : SignOutput {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignOutputData) return false

        if (!signedData.contentEquals(other.signedData)) return false
        if (signatureLevel != other.signatureLevel) return false
        if (signingTime != other.signingTime) return false
        if (signatureId != other.signatureId) return false
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signedData.contentHashCode()
        result = 31 * result + signatureLevel.hashCode()
        result = 31 * result + signingTime.hashCode()
        result = 31 * result + (signatureId?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}
