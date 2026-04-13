/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import kotlinx.serialization.json.*

import com.sphereon.sdjwt.DecoyConfig
import com.sphereon.sdjwt.DecoyMode
import com.sphereon.sdjwt.DefaultSaltProvider
import com.sphereon.sdjwt.Disclosure
import com.sphereon.sdjwt.DisclosureDigest
import com.sphereon.sdjwt.SaltProvider
import com.sphereon.sdjwt.SdJwtSpec
import com.sphereon.sdjwt.UnsignedSdJwt
import com.sphereon.sdjwt.dsl.SdJwtPayload
import kotlin.collections.iterator
import kotlin.random.Random

/**
 * Issues SD-JWTs from [SdJwtPayload] objects built using the SD-JWT DSL.
 *
 * Internal implementation detail - use [IssueSdJwtCommand] instead.
 *
 * This issuer works with payloads built using [sdJwtPayload] DSL builder.
 * It transforms claims marked as selectively disclosable into disclosures and
 * replaces them with digests in the JWT payload.
 *
 * The issuer performs the following steps:
 * 1. Identifies claims marked as selectively disclosable via [SdJwtPayload.sdClaims]
 * 2. Generates disclosures for those claims
 * 3. Replaces selectively disclosable claims with their digests in the `_sd` array
 * 4. Optionally adds decoy digests for privacy protection
 * 5. Returns an unsigned SD-JWT
 *
 * @property spec The SD-JWT specification (algorithm, decoy configuration)
 * @property saltProvider Provides cryptographically secure salts for disclosures
 */
internal class SdJwtIssuer(
    private val spec: SdJwtSpec = SdJwtSpec.Default,
    private val saltProvider: SaltProvider = DefaultSaltProvider(),
) {
    /**
     * Issues an unsigned SD-JWT from an [SdJwtPayload].
     *
     * The payload should be built using the `sdJwtPayload` DSL with calls to `claimSd()`
     * for claims that should be selectively disclosable.
     *
     * Example:
     * ```kotlin
     * val issuer = SdJwtIssuer()
     * val payload = sdJwtPayload {
     *     iss("https://issuer.example.com")
     *     claimSd("email", "user@example.com")  // Will become a disclosure
     * }
     * val unsigned = issuer.issue(payload)
     * ```
     *
     * @param payload The SD-JWT payload built with the DSL
     * @return An [UnsignedSdJwt] containing the transformed payload and disclosures
     */
    fun issue(payload: SdJwtPayload): UnsignedSdJwt {
        val disclosures = mutableListOf<Disclosure>()
        val sdDigests = mutableListOf<String>()
        val plainClaims = mutableMapOf<String, JsonElement>()

        // Process each claim in the payload
        for ((name, value) in payload.claims) {
            if (payload.isClaimSelectivelyDisclosable(name)) {
                // Selectively disclosable - create disclosure and add digest
                val disclosure = Disclosure.Companion.objectProperty(
                    saltProvider = saltProvider,
                    claimName = name,
                    claimValue = value
                )
                disclosures.add(disclosure)

                val digest = DisclosureDigest.Companion.calculate(
                    digestAlg = spec.digestAlg,
                    disclosure = disclosure
                )
                sdDigests.add(digest.value)
            } else {
                // Plain claim - process for nested SD elements
                plainClaims[name] = processValue(
                    name = name,
                    value = value,
                    sdClaims = payload.sdClaims,
                    disclosures = disclosures
                )
            }
        }

        // Add decoy digests if configured
        val decoysToAdd = calculateDecoysNeeded(sdDigests.size, payload.minimumDigests, spec.decoyConfig)
        repeat(decoysToAdd) {
            sdDigests.add(generateDecoyDigest())
        }

        // Build final JWT payload
        val finalPayload = buildJsonObject {
            // Add plain claims
            for ((name, value) in plainClaims) {
                put(name, value)
            }

            // Add _sd array if there are any digests
            if (sdDigests.isNotEmpty()) {
                putJsonArray(SD_CLAIM) {
                    // Sort digests to prevent correlation attacks (RFC 9901 §5.1.4)
                    sdDigests.sorted().forEach { add(it) }
                }
            }

            // Add _sd_alg claim if there are disclosures (RFC 9901 §5.1.2)
            if (disclosures.isNotEmpty()) {
                put(SD_ALG_CLAIM, JsonPrimitive(spec.digestAlg.httpHeaderId?.lowercase() ?: "sha-256"))
            }
        }

        return UnsignedSdJwt(
            jwtPayload = finalPayload,
            disclosures = disclosures
        )
    }

    /**
     * Processes a value, recursively handling nested objects that may have SD claims.
     *
     * Nested SD claims are identified using path-based lookups in the sdClaims set.
     * For example, if sdClaims contains "address.zip", and we're processing the "address"
     * object, then the "zip" field within it is selectively disclosable.
     *
     * @param name The current claim name (used to build paths for nested claims)
     * @param value The value to process
     * @param sdClaims The set of SD claim paths (e.g., ["sub", "email", "address.zip"])
     * @param disclosures Accumulator for disclosures found in nested objects
     * @return The processed value (with nested SD claims transformed if any)
     */
    private fun processValue(
        name: String,
        value: JsonElement,
        sdClaims: Set<String>,
        disclosures: MutableList<Disclosure>
    ): JsonElement {
        return when (value) {
            is JsonObject -> processNestedObject(name, value, sdClaims, disclosures)
            is JsonArray -> JsonArray(value.mapIndexed { index, element ->
                processValue("$name.$index", element, sdClaims, disclosures)
            })
            else -> value
        }
    }

    /**
     * Processes a nested object, handling SD claims within it.
     *
     * This method checks each field in the nested object to see if it's marked as SD
     * (by checking for paths like "parent.field" in sdClaims).
     */
    private fun processNestedObject(
        parentPath: String,
        obj: JsonObject,
        sdClaims: Set<String>,
        disclosures: MutableList<Disclosure>
    ): JsonObject {
        val nestedSdDigests = mutableListOf<String>()
        val nestedPlainClaims = mutableMapOf<String, JsonElement>()

        for ((fieldName, fieldValue) in obj) {
            val fieldPath = "$parentPath.$fieldName"

            if (fieldPath in sdClaims) {
                // This nested field is selectively disclosable
                val disclosure = Disclosure.Companion.objectProperty(
                    saltProvider = saltProvider,
                    claimName = fieldName,
                    claimValue = fieldValue
                )
                disclosures.add(disclosure)

                val digest = DisclosureDigest.Companion.calculate(
                    digestAlg = spec.digestAlg,
                    disclosure = disclosure
                )
                nestedSdDigests.add(digest.value)
            } else {
                // Recursively process for deeper nesting
                nestedPlainClaims[fieldName] = processValue(fieldPath, fieldValue, sdClaims, disclosures)
            }
        }

        // Build the nested object
        return buildJsonObject {
            // Add plain claims
            for ((name, value) in nestedPlainClaims) {
                put(name, value)
            }

            // Add _sd array if there are any nested SD claims
            if (nestedSdDigests.isNotEmpty()) {
                putJsonArray(SD_CLAIM) {
                    nestedSdDigests.sorted().forEach { add(it) }
                }
            }
        }
    }

    /**
     * Calculates how many decoy digests are needed based on configuration.
     */
    private fun calculateDecoysNeeded(
        actualDisclosures: Int,
        minimumDigests: Int?,
        decoyConfig: DecoyConfig
    ): Int {
        return when (decoyConfig.mode) {
            DecoyMode.NONE -> 0
            DecoyMode.FIXED -> decoyConfig.count
            DecoyMode.MINIMUM -> {
                val minimum = minimumDigests ?: decoyConfig.count
                (minimum - actualDisclosures).coerceAtLeast(0)
            }
            DecoyMode.RANDOM -> Random.nextInt(0, decoyConfig.count + 1)
        }
    }

    /**
     * Generates a decoy digest (random string that looks like a digest).
     */
    private fun generateDecoyDigest(): String {
        val salt = saltProvider.generateSalt()
        // Use the salt as input to generate a fake digest
        val decoyDisclosure = Disclosure.Companion.objectProperty(
            saltProvider = saltProvider,
            claimName = "_decoy",
            claimValue = JsonPrimitive(salt)
        )
        return DisclosureDigest.Companion.calculate(spec.digestAlg, decoyDisclosure).value
    }

    companion object {
        /** Claim name for the _sd array containing digests (RFC 9901 §5.1.1) */
        private const val SD_CLAIM = "_sd"

        /** Claim name for the _sd_alg indicating hash algorithm (RFC 9901 §5.1.2) */
        private const val SD_ALG_CLAIM = "_sd_alg"
    }
}
