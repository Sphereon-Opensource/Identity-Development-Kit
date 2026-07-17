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
 */

package com.sphereon.sdjwt

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Represents a disclosure digest value.
 */
@JsExportCompat
data class DisclosureDigest(
    val value: String,
) {
    companion object {
        /**
         * Calculate the digest of a disclosure
         *
         * @param digestAlg The hash algorithm to use
         * @param disclosure The disclosure object
         * @return DisclosureDigest containing the digest value
         */
        @JvmStatic
        fun calculate(
            digestAlg: DigestAlg,
            disclosure: Disclosure,
        ): DisclosureDigest {
            val digestValue = calculateDigest(disclosure.encoded, digestAlg)
            return DisclosureDigest(digestValue)
        }

        /**
         * Calculate the digest of an encoded disclosure string
         *
         * @param encodedDisclosure The base64url-encoded disclosure string
         * @param hashAlgorithm The hash algorithm to use (default SHA-256)
         * @return The base64url-encoded digest
         */
        @JvmStatic
        @JvmOverloads
        fun calculateDigest(
            encodedDisclosure: String,
            hashAlgorithm: DigestAlg = DigestAlg.SHA256,
        ): String {
            val hashBytes = hash(encodedDisclosure.encodeToByteArray(), hashAlgorithm)
            return hashBytes.encodeToBase64Url()
        }
    }
}

/**
 * Utilities for computing and verifying disclosure digests in SD-JWT
 *
 * According to RFC 9901, disclosure digests are computed by:
 * 1. Taking the base64url-encoded disclosure string
 * 2. UTF-8 encoding it
 * 3. Hashing with the specified algorithm (default SHA-256)
 * 4. Base64url-encoding the hash
 */
object DisclosureDigestUtil {
    /**
     * Verify that a disclosure's digest matches the expected digest
     *
     * @param disclosure The disclosure to verify
     * @param expectedDigest The expected digest value
     * @param hashAlgorithm The hash algorithm to use
     * @return True if the digest matches, false otherwise
     */
    fun verifyDisclosure(
        disclosure: Disclosure,
        expectedDigest: String,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): Boolean {
        val computedDigest = DisclosureDigest.calculateDigest(disclosure.encoded, hashAlgorithm)
        return computedDigest == expectedDigest
    }

    /**
     * Verify all disclosures in a payload against their digests
     *
     * @param payload The SD-JWT payload containing _sd arrays
     * @param disclosures List of disclosures to verify
     * @param hashAlgorithm The hash algorithm to use
     * @return True if all disclosures are valid, false otherwise
     */
    fun verifyAllDisclosures(
        payload: SdJwtPayload,
        disclosures: List<Disclosure>,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): Boolean {
        // Create a mutable copy of digested disclosures
        val remainingDisclosures = payload.digestedDisclosures.toMutableMap()

        // Verify each disclosure against its digest
        for (disclosure in disclosures) {
            val digest = disclosure.digest ?: continue
            val expectedDisclosure = remainingDisclosures.remove(digest) ?: return false

            if (!verifyDisclosure(disclosure, digest, hashAlgorithm)) {
                return false
            }
        }

        // All disclosures should be accounted for
        return remainingDisclosures.isEmpty()
    }

    /**
     * Generate a decoy digest (random salt hashed)
     *
     * Used to obscure the number of actual selective disclosures in the _sd array
     *
     * @param saltProvider The salt provider to generate random salt
     * @param hashAlgorithm The hash algorithm to use
     * @return A base64url-encoded decoy digest
     */
    fun generateDecoyDigest(
        saltProvider: SaltProvider,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): String {
        // Generate random salt - it's already base64url-encoded by the provider
        val encodedSalt = saltProvider.generateSalt()

        // Hash the encoded salt to create a decoy digest
        return DisclosureDigest.calculateDigest(encodedSalt, hashAlgorithm)
    }

    /**
     * Generate multiple decoy digests
     *
     * @param count The number of decoy digests to generate
     * @param saltProvider The salt provider to generate random salts
     * @param hashAlgorithm The hash algorithm to use
     * @return List of base64url-encoded decoy digests
     */
    fun generateDecoyDigests(
        count: Int,
        saltProvider: SaltProvider,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): List<String> = List(count) { generateDecoyDigest(saltProvider, hashAlgorithm) }

    /**
     * Extract all disclosure digests from an SD-JWT payload
     *
     * This includes digests from top-level and nested _sd arrays, plus RFC 9901
     * array-element digest markers ({"...": "<digest>"}) at any depth.
     *
     * @param payload The JSON payload to extract digests from
     * @return Set of all disclosure digests found
     */
    fun extractDigests(payload: JsonObject): Set<String> {
        val digests = mutableSetOf<String>()
        extractDigestsRecursive(payload, digests)
        return digests
    }

    /**
     * Recursively extract digests from nested objects
     */
    private fun extractDigestsRecursive(
        obj: JsonObject,
        digests: MutableSet<String>,
    ) {
        obj.forEach { (key, value) ->
            when {
                key == SdJwt.SD_CLAIM && value is JsonArray -> {
                    // Extract digests from _sd array
                    value.forEach { element ->
                        if (element is JsonPrimitive) {
                            digests.add(element.content)
                        }
                    }
                }

                value is JsonObject -> {
                    // Recursively process nested objects
                    extractDigestsRecursive(value, digests)
                }

                value is JsonArray -> {
                    extractDigestsFromArray(value, digests)
                }
            }
        }
    }

    /**
     * Walk a JSON array collecting {"...": "<digest>"} array-element markers and recursing
     * into nested containers.
     */
    private fun extractDigestsFromArray(
        array: JsonArray,
        digests: MutableSet<String>,
    ) {
        array.forEach { element ->
            when (element) {
                is JsonObject -> {
                    val marker = element.takeIf { it.size == 1 }?.get(SdJwt.SD_ARRAY_ELEMENT_CLAIM)
                    if (marker is JsonPrimitive) {
                        digests.add(marker.content)
                    } else {
                        extractDigestsRecursive(element, digests)
                    }
                }

                is JsonArray -> extractDigestsFromArray(element, digests)
                else -> Unit
            }
        }
    }
}
