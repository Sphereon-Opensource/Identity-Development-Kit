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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Codec for encoding, decoding, and serializing SD-JWT structures
 * Handles the SD-JWT compact serialization format: jwt~disclosure1~disclosure2~...~kbJwt
 */
object SdJwtCodec {
    /**
     * Regular expression pattern for validating SD-JWT format
     * Groups: sdjwt, header, body, signature, disclosures, kbjwt
     */
    private val SD_JWT_PATTERN =
        Regex(
            "^(?<sdjwt>(?<header>[A-Za-z0-9_-]+)\\.(?<body>[A-Za-z0-9_-]+)\\.(?<signature>[A-Za-z0-9_-]+))" +
                "(?<disclosures>(~[A-Za-z0-9_-]+)+)?" +
                "(~(?<kbjwt>([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)))?~?$",
        )

    /**
     * Parse an SD-JWT from its compact serialization format
     *
     * Format: jwt~disclosure1~disclosure2~...~kbJwt
     * - jwt: Standard compact JWS
     * - disclosures: Base64url-encoded disclosure arrays
     * - kbJwt: Optional Key Binding JWT
     *
     * @param sdJwtString The SD-JWT string to parse
     * @param hashAlgorithm The hash algorithm to use for computing disclosure digests (default: SHA-256)
     * @return Parsed SdJwtCompact object
     * @throws IllegalArgumentException if the format is invalid
     */
    fun parse(
        sdJwtString: String,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): IdkResult<SdJwtCompact, IdkError> =
        try {
            IdkResult.ok(parseInternal(sdJwtString, hashAlgorithm))
        } catch (expected: Exception) {
            IdkResult.err(IdkError.fromString("Failed to parse SD-JWT: ${expected.message}", exception = expected))
        }

    private fun parseInternal(
        sdJwtString: String,
        hashAlgorithm: DigestAlg,
    ): SdJwtCompact {
        val matchResult =
            SD_JWT_PATTERN.matchEntire(sdJwtString)
                ?: throw IllegalArgumentException("Invalid SD-JWT format: $sdJwtString")

        val groups = matchResult.groups as MatchNamedGroupCollection

        // Extract JWT parts
        val jwtString =
            groups["sdjwt"]?.value
                ?: throw IllegalArgumentException("Missing JWT in SD-JWT")

        val headerBase64 =
            groups["header"]?.value
                ?: throw IllegalArgumentException("Missing JWT header")

        val bodyBase64 =
            groups["body"]?.value
                ?: throw IllegalArgumentException("Missing JWT body")

        // Parse JWT header and body using existing infrastructure
        val header = JwsUtils.decodeBase64UrlToJson(headerBase64)
        val undisclosedPayload = JwsUtils.decodeBase64UrlToJson(bodyBase64)

        // Extract and parse disclosures
        val disclosureStrings =
            groups["disclosures"]
                ?.value
                ?.trim(SdJwt.SEPARATOR)
                ?.split(SdJwt.SEPARATOR)
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        val disclosures = disclosureStrings.map { parseDisclosure(it, hashAlgorithm) }

        // Create digest map
        val digestedDisclosures = disclosures.associateBy { it.digest ?: "" }

        // Reconstruct full payload
        val fullPayload = reconstructPayload(undisclosedPayload, digestedDisclosures)

        // Parse Key Binding JWT if present
        val kbJwt = groups["kbjwt"]?.value?.let { parseKeyBindingJwt(it) }

        return SdJwt(
            jwt = JwsCompact(jwtString),
            header = header,
            payload =
                SdJwtPayload(
                    undisclosedPayload = undisclosedPayload,
                    fullPayload = fullPayload,
                    digestedDisclosures = digestedDisclosures,
                ),
            disclosures = disclosures,
            keyBindingJwt = kbJwt,
        )
    }

    /**
     * Serialize an SD-JWT to its compact format
     *
     * @param sdJwt The SD-JWT to serialize
     * @param includeKeyBinding Whether to include the key binding JWT
     * @param forPresentation Format for presentation (always end with ~ even if no KB-JWT)
     * @return Serialized SD-JWT string
     */
    fun serialize(
        sdJwt: SdJwtCompact,
        includeKeyBinding: Boolean = true,
        forPresentation: Boolean = false,
    ): String {
        val parts = mutableListOf<String>()

        // Add JWT
        parts.add(sdJwt.jwt.value)

        // Add disclosures
        parts.addAll(sdJwt.disclosures.map { it.encoded })

        // Add KB-JWT if present and requested
        if (includeKeyBinding && sdJwt.keyBindingJwt != null) {
            parts.add(sdJwt.keyBindingJwt.jwt)
        } else if (forPresentation) {
            // For presentations, always end with ~ to indicate presentation format
            parts.add("")
        }

        return parts.joinToString(SdJwt.SEPARATOR.toString())
    }

    /**
     * Parse a disclosure from its base64url-encoded format
     *
     * An object-property disclosure is a JSON array [salt, claim_name, claim_value]; an
     * array-element disclosure (RFC 9901) is [salt, claim_value] and yields a null key.
     *
     * @param encoded The base64url-encoded disclosure
     * @param hashAlgorithm The hash algorithm to use for computing the digest
     * @return Parsed Disclosure object
     */
    fun parseDisclosure(
        encoded: String,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): Disclosure {
        val decoded = encoded.decodeFromBase64Url().decodeToString()
        val array = Json.parseToJsonElement(decoded).jsonArray

        require(array.size == 2 || array.size == 3) {
            "Invalid disclosure format: expected [salt, key, value] or [salt, value], got $decoded"
        }

        val salt = array[0].jsonPrimitive.content
        val key = if (array.size == 3) array[1].jsonPrimitive.content else null
        val value = array[array.size - 1]

        // Compute digest
        val digest = DisclosureDigest.calculateDigest(encoded, hashAlgorithm)

        return Disclosure(
            salt = salt,
            key = key,
            value = value,
            encoded = encoded,
            digest = digest,
        )
    }

    /**
     * Encode a disclosure to its base64url format
     *
     * @param salt The salt value
     * @param key The claim name
     * @param value The claim value
     * @param hashAlgorithm The hash algorithm to use for computing the digest
     * @return Encoded Disclosure object
     */
    fun encodeDisclosure(
        salt: String,
        key: String,
        value: JsonElement,
        hashAlgorithm: DigestAlg = DigestAlg.SHA256,
    ): Disclosure {
        val array =
            buildJsonArray {
                add(salt)
                add(key)
                add(value)
            }

        val encoded =
            array
                .toString()
                .encodeToByteArray()
                .encodeToBase64Url()

        val digest = DisclosureDigest.calculateDigest(encoded, hashAlgorithm)

        return Disclosure(
            salt = salt,
            key = key,
            value = value,
            encoded = encoded,
            digest = digest,
        )
    }

    /**
     * Parse a Key Binding JWT
     *
     * @param kbJwtString The KB-JWT in compact format
     * @return Parsed KeyBindingJwt object
     */
    private fun parseKeyBindingJwt(kbJwtString: String): KeyBindingJwt {
        val parts = kbJwtString.split(".")
        require(parts.size == 3) { "Invalid KB-JWT format: expected 3 parts, got ${parts.size}" }

        val header = JwsUtils.decodeBase64UrlToJson(parts[0])
        val payload = JwsUtils.decodeBase64UrlToJson(parts[1])

        return KeyBindingJwt(
            jwt = kbJwtString,
            header = header,
            payload = payload,
        )
    }

    /**
     * Reconstruct the full payload by resolving all disclosures recursively
     *
     * @param undisclosedPayload The payload from the JWT (with _sd arrays)
     * @param digestedDisclosures Map of digests to disclosures
     * @return The full payload with all disclosures resolved
     */
    private fun reconstructPayload(
        undisclosedPayload: JsonObject,
        digestedDisclosures: Map<String, Disclosure>,
    ): JsonObject = reconstructPayloadRecursive(undisclosedPayload, digestedDisclosures)

    /**
     * Recursively reconstruct payload by unveiling disclosures
     */
    private fun reconstructPayloadRecursive(
        payload: JsonObject,
        digestedDisclosures: Map<String, Disclosure>,
    ): JsonObject =
        buildJsonObject {
            payload.forEach { (key, value) ->
                when {
                    key == SdJwt.SD_CLAIM && value is JsonArray -> {
                        // Process _sd array: unveil each disclosure
                        value.forEach { digestElement ->
                            val digest = digestElement.jsonPrimitive.content
                            val disclosure = digestedDisclosures[digest]
                            // Only object-property disclosures (non-null key) belong in _sd;
                            // a malformed array-element disclosure here is skipped.
                            if (disclosure?.key != null) {
                                val unveiledValue =
                                    if (disclosure.value is JsonObject) {
                                        // Recursively reconstruct nested objects
                                        reconstructPayloadRecursive(
                                            disclosure.value.jsonObject,
                                            digestedDisclosures,
                                        )
                                    } else {
                                        disclosure.value
                                    }
                                put(disclosure.key, unveiledValue)
                            }
                        }
                    }

                    value is JsonObject -> {
                        // Recursively process nested objects
                        put(key, reconstructPayloadRecursive(value, digestedDisclosures))
                    }

                    value is JsonArray -> {
                        put(key, reconstructArrayRecursive(value, digestedDisclosures))
                    }

                    else -> {
                        // Regular claim, keep as-is
                        put(key, value)
                    }
                }
            }
        }

    /**
     * Reconstruct a JSON array, unveiling RFC 9901 array-element disclosures: an element of the
     * form {"...": "<digest>"} is replaced by the matching disclosure's value (recursively
     * reconstructed), or dropped entirely when the element stays undisclosed.
     */
    private fun reconstructArrayRecursive(
        array: JsonArray,
        digestedDisclosures: Map<String, Disclosure>,
    ): JsonArray =
        buildJsonArray {
            array.forEach { element ->
                val arrayDigest =
                    (element as? JsonObject)
                        ?.takeIf { it.size == 1 }
                        ?.get(SdJwt.SD_ARRAY_ELEMENT_CLAIM)
                        ?.let { it as? JsonPrimitive }
                        ?.content
                when {
                    arrayDigest != null -> {
                        val disclosure = digestedDisclosures[arrayDigest] ?: return@forEach
                        val unveiled =
                            when (disclosure.value) {
                                is JsonObject -> reconstructPayloadRecursive(disclosure.value.jsonObject, digestedDisclosures)
                                is JsonArray -> reconstructArrayRecursive(disclosure.value.jsonArray, digestedDisclosures)
                                else -> disclosure.value
                            }
                        add(unveiled)
                    }

                    element is JsonObject -> add(reconstructPayloadRecursive(element, digestedDisclosures))
                    element is JsonArray -> add(reconstructArrayRecursive(element, digestedDisclosures))
                    else -> add(element)
                }
            }
        }

    /**
     * Check if a string matches the SD-JWT format pattern
     *
     * @param value The string to check
     * @param requireDisclosures If true, only match if disclosures are present
     * @return True if the string matches the SD-JWT pattern
     */
    fun isSdJwtFormat(
        value: String,
        requireDisclosures: Boolean = false,
    ): Boolean {
        val matches = SD_JWT_PATTERN.matches(value)
        return if (requireDisclosures) {
            matches && value.contains(SdJwt.SEPARATOR)
        } else {
            matches
        }
    }
}
