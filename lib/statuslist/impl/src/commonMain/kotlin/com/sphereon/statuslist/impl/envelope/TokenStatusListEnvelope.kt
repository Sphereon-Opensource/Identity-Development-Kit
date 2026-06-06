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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Decoded content of an IETF Token Status List token payload. */
internal data class TokenStatusListContent(
    val bitsPerStatus: Int,
    val encodedList: String,
    val ttlSeconds: Long?,
    val expiresAtEpochSeconds: Long?,
)

/**
 * Builds and parses the IETF Token Status List JWT/CWT payload:
 * `{ iss, sub, iat, exp?, ttl?, status_list: { bits, lst } }` (`draft-ietf-oauth-status-list` §5).
 */
internal object TokenStatusListEnvelope {
    fun buildPayload(args: SignStatusListTokenArgs): JsonObject =
        buildJsonObject {
            put("iss", args.issuer)
            put("sub", args.statusListUri)
            put("iat", args.issuedAtEpochSeconds)
            args.expiresAtEpochSeconds?.let { put("exp", it) }
            args.ttlSeconds?.let { put("ttl", it) }
            putJsonObject("status_list") {
                put("bits", args.bitsPerStatus)
                put("lst", args.encodedList)
            }
        }

    fun parse(payload: JsonObject): TokenStatusListContent {
        val statusList =
            payload["status_list"]?.jsonObject
                ?: throw IllegalArgumentException("Token Status List token missing 'status_list' claim")
        val lst =
            statusList["lst"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Token Status List token missing 'status_list.lst'")
        val bits = statusList["bits"]?.jsonPrimitive?.intOrNull ?: 1
        return TokenStatusListContent(
            bitsPerStatus = bits,
            encodedList = lst,
            ttlSeconds = payload["ttl"]?.jsonPrimitive?.longOrNull,
            expiresAtEpochSeconds = payload["exp"]?.jsonPrimitive?.longOrNull,
        )
    }
}
