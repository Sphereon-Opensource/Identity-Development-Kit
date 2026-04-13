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


import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.Serializable
import com.sphereon.core.api.Base64Serializer
import com.sphereon.core.compat.JsExportCompat
/**
 * Represents input data to be signed.
 *
 * This is the canonical sign-input type for all signing operations across IDK and EDK.
 * It captures "what to sign" — the document data and metadata — but NOT "how to sign"
 * (algorithm, parameters, etc.), which are passed as separate method arguments.
 *
 * @property input The document or digest bytes to sign.
 * @property signMode Whether to sign a pre-computed digest or the full document. Defaults to [SigningMode.DOCUMENT].
 * @property name A human-readable name for the document being signed, defaulting to "document".
 * @property mimeType MIME type of the document (e.g. "application/pdf", "application/json").
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SignInput", exact = true)
@Serializable
@JsExportCompat
data class SignInput(
    @Serializable(with = Base64Serializer::class) val input: ByteArray,
    val signMode: SigningMode = SigningMode.DOCUMENT,
    val name: String? = "document",
    val mimeType: String? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SignInput

        if (!input.contentEquals(other.input)) return false
        if (signMode != other.signMode) return false
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = input.contentHashCode()
        result = 31 * result + signMode.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun pdf(data: ByteArray, name: String = "document.pdf"): SignInput =
            SignInput(input = data, name = name, mimeType = "application/pdf")

        fun xml(data: ByteArray, name: String = "document.xml"): SignInput =
            SignInput(input = data, name = name, mimeType = "application/xml")

        fun json(data: ByteArray, name: String = "document.json"): SignInput =
            SignInput(input = data, name = name, mimeType = "application/json")

        fun binary(data: ByteArray, name: String = "document"): SignInput =
            SignInput(input = data, name = name, mimeType = "application/octet-stream")

        fun digest(digestData: ByteArray, name: String = "digest"): SignInput =
            SignInput(input = digestData, name = name, signMode = SigningMode.DIGEST)
    }
}
