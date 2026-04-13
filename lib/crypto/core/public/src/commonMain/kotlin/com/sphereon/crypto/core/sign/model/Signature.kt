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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoSerializer
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
/**
 * Data class that represents a cryptographic signature.
 *
 * @property value The byte array representing the signature, serialized in Base64 URL format.
 * @property algorithm The algorithm used for creating the signature.
 * @property signMode The mode used for signing.
 * @property keyInfo The resolved key information used for the signature.
 * @property providerId The ID of the provider used for signing.
 * @property date The timestamp when the signature was created.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Signature", exact = true)
@JsExportCompat
// Note: @Serializable disabled due to compiler exception with ByteArray/Instant/ResolvedKeyInfoType combination
// Use manual serialization if needed
data class Signature(
//    @Serializable(with = Base64Serializer::class)
    val value: ByteArray,
    val algorithm: SignatureAlgorithm,
    val signMode: SigningMode,
    @Serializable(with = ResolvedKeyInfoSerializer::class)
    val keyInfo: ResolvedKeyInfoType<*>,
    val level: SignatureLevel,
//    @Transient
    val date: Instant = Clock.System.now(),
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false

        if (!value.contentEquals(other.value)) return false
        if (algorithm != other.algorithm) return false
        if (signMode != other.signMode) return false
        if (keyInfo != other.keyInfo) return false
        if (level != other.level) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + signMode.hashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + level.hashCode()
        result = 31 * result + date.hashCode()
        return result
    }
}
