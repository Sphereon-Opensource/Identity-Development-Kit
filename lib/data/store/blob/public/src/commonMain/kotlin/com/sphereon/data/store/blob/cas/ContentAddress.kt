/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.cas

import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.MultihashAlgorithm
import com.sphereon.crypto.core.generic.MultihashCodec
import com.sphereon.crypto.core.generic.hash
import kotlinx.serialization.Serializable

/**
 * Content-derived address using multihash encoding.
 *
 * Uses the existing [MultihashCodec] and [DigestAlg] from crypto-core-public.
 */
@Serializable
@JsExportCompat
data class ContentAddress(
    val algorithm: DigestAlg,
    val digest: ByteArray,
) {
    init {
        require(digest.isNotEmpty()) { "Digest must not be empty" }
        require(algorithm != DigestAlg.NONE) { "DigestAlg.NONE is not valid for content addressing" }
    }

    /**
     * Encode as multibase-encoded multihash string.
     */
    fun toMultibaseString(encoding: MultibaseEncoding = MultibaseEncoding.BASE58BTC): String {
        val mhAlg =
            checkNotNull(MultihashAlgorithm.fromDigestAlg(algorithm)) {
                "No MultihashAlgorithm for ${algorithm.name}"
            }
        return MultihashCodec.encodeToMultibase(digest, mhAlg, encoding)
    }

    /**
     * Encode as hex digest string (algorithm:hexdigest).
     */
    fun toDigestString(): String = "${algorithm.internalName}:${digest.encodeToHex()}"

    /**
     * Encode as hashlink (hl:<multibase>).
     */
    fun toHashlink(): String = "hl:${toMultibaseString()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ContentAddress) {
            return false
        }
        return algorithm == other.algorithm && digest.contentEquals(other.digest)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + digest.contentHashCode()
        return result
    }

    override fun toString(): String = toMultibaseString()

    companion object {
        /**
         * Compute content address from raw data.
         */
        fun compute(
            data: ByteArray,
            algorithm: DigestAlg = DigestAlg.SHA256,
        ): ContentAddress {
            val digest = hash(data, algorithm)
            return ContentAddress(algorithm = algorithm, digest = digest)
        }

        /**
         * Parse from multibase-encoded multihash string.
         */
        fun fromMultibaseString(encoded: String): ContentAddress {
            val (mhAlg, digest) = MultihashCodec.decodeFromMultibase(encoded)
            return ContentAddress(algorithm = mhAlg.digestAlg, digest = digest)
        }

        /**
         * Parse from digest string (algorithm:hexdigest).
         */
        fun fromDigestString(digestString: String): ContentAddress {
            val separatorIdx = digestString.indexOf(':')
            require(separatorIdx > 0) { "Invalid digest string format: $digestString (expected algorithm:hexdigest)" }
            val algName = digestString.substring(0, separatorIdx)
            val hexDigest = digestString.substring(separatorIdx + 1)
            val algorithm = DigestAlg.fromValue(algName)
            val digest = hexDigest.decodeFromHex()
            return ContentAddress(algorithm = algorithm, digest = digest)
        }
    }
}
