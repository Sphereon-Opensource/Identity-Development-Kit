package com.sphereon.data.store.blob.cas

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
        val mhAlg = MultihashAlgorithm.fromDigestAlg(algorithm)
            ?: throw IllegalStateException("No MultihashAlgorithm for ${algorithm.name}")
        return MultihashCodec.encodeToMultibase(digest, mhAlg, encoding)
    }

    /**
     * Encode as hex digest string (algorithm:hexdigest).
     */
    fun toDigestString(): String {
        val hex = digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${algorithm.internalName}:$hex"
    }

    /**
     * Encode as hashlink (hl:<multibase>).
     */
    fun toHashlink(): String = "hl:${toMultibaseString()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentAddress) return false
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
        fun compute(data: ByteArray, algorithm: DigestAlg = DigestAlg.SHA256): ContentAddress {
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
            val digest = hexDigest.hexToByteArray()
            return ContentAddress(algorithm = algorithm, digest = digest)
        }

        private fun String.hexToByteArray(): ByteArray {
            require(length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
