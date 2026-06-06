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

package com.sphereon.statuslist.impl.envelope

import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Decoded content of a W3C `BitstringStatusListCredential` credentialSubject. */
internal data class BitstringStatusListContent(
    val encodedList: String,
    val statusPurpose: String,
    val statusSize: Int,
)

/**
 * Builds and parses the W3C `BitstringStatusListCredential` (VCDM 2.0), enveloped as a VC-JWT.
 * The credential JSON is the JWT payload (typ `vc+jwt`). See W3C Bitstring Status List v1.0 §3.
 */
internal object BitstringStatusListEnvelope {
    fun buildCredential(args: SignStatusListTokenArgs): JsonObject =
        buildJsonObject {
            putJsonArray("@context") {
                add("https://www.w3.org/ns/credentials/v2")
                // The dedicated status vocabulary; the spec requires it be treated as pre-resolved.
                add("https://www.w3.org/ns/credentials/status/v1")
            }
            put("id", args.statusListUri)
            putJsonArray("type") {
                add("VerifiableCredential")
                add("BitstringStatusListCredential")
            }
            put("issuer", args.issuer)
            put("validFrom", Instant.fromEpochSeconds(args.issuedAtEpochSeconds).toString())
            args.expiresAtEpochSeconds?.let { put("validUntil", Instant.fromEpochSeconds(it).toString()) }
            putJsonObject("credentialSubject") {
                put("id", args.statusListUri + "#list")
                put("type", "BitstringStatusList")
                put("statusPurpose", args.purposes.firstOrNull()?.value ?: "revocation")
                put("encodedList", args.encodedList)
                if (args.bitsPerStatus > 1) {
                    put("statusSize", args.bitsPerStatus)
                    // Spec §3: when statusSize > 1, statusMessage MUST be present with one entry per
                    // possible value (length 2^statusSize), each mapping a `0x`-prefixed value to a
                    // debug message (SHOULD NOT be shown to end users).
                    putJsonArray("statusMessage") {
                        for (value in 0 until (1 shl args.bitsPerStatus)) {
                            add(
                                buildJsonObject {
                                    put("status", "0x" + value.toString(16))
                                    put("message", statusMessageLabel(value))
                                },
                            )
                        }
                    }
                }
                // ttl is OPTIONAL and expressed in milliseconds; servers SHOULD align HTTP
                // Cache-Control with it. We carry the same hint the token TTL uses (seconds → ms).
                args.ttlSeconds?.let { put("ttl", it * 1000) }
            }
        }

    /** Debug label for a status value in the `statusMessage` array (not for end-user display). */
    private fun statusMessageLabel(value: Int): String =
        when (value) {
            0x00 -> "valid"
            0x01 -> "invalid"
            0x02 -> "suspended"
            else -> "application_specific"
        }

    fun parse(payload: JsonObject): BitstringStatusListContent {
        val subject =
            payload["credentialSubject"]?.jsonObject
                ?: throw IllegalArgumentException("BitstringStatusListCredential missing 'credentialSubject'")
        val encodedList =
            subject["encodedList"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("BitstringStatusListCredential missing 'encodedList'")
        return BitstringStatusListContent(
            encodedList = encodedList,
            statusPurpose = subject["statusPurpose"]?.jsonPrimitive?.content ?: "revocation",
            statusSize = subject["statusSize"]?.jsonPrimitive?.intOrNull ?: 1,
        )
    }
}
