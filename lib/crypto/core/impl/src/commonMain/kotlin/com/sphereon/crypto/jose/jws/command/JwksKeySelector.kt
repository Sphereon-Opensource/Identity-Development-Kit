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

package com.sphereon.crypto.jose.jws.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Selects a JWK from a trusted JWKS document for JWS signature verification.
 *
 * Resolution rules (RFC 7517 §4.5 + OIDC Core §10.1.1 spirit):
 *  - When the JWS header carries a `kid` and the JWKS holds a key with that `kid`, return it.
 *  - When the header carries a `kid` and no JWKS entry matches, return null (caller MUST reject).
 *  - When the header has no `kid` and exactly one JWKS entry is compatible with the header `alg`
 *    (matching `kty`, optional `alg`, optional `use=sig`), return that entry.
 *  - Otherwise (zero or multiple compatible entries when `kid` is absent), return null.
 */
internal fun selectJwk(
    trustedJwks: JsonObject,
    headerKid: String?,
    headerAlg: String,
): JsonElement? {
    val keys = trustedJwks["keys"]?.jsonArray ?: return null
    if (headerKid != null) {
        return keys.firstOrNull { it.jsonObject["kid"]?.jsonPrimitive?.content == headerKid }
    }
    val expectedKty = ktyForAlg(headerAlg) ?: return null
    val candidates =
        keys.filter { jwk ->
            val obj = jwk.jsonObject
            val ktyMatches = obj["kty"]?.jsonPrimitive?.content == expectedKty
            val algCompatible = obj["alg"]?.jsonPrimitive?.content?.let { it == headerAlg } != false
            val useCompatible = obj["use"]?.jsonPrimitive?.content?.let { it == "sig" } != false
            ktyMatches && algCompatible && useCompatible
        }
    return if (candidates.size == 1) candidates.single() else null
}

/** Maps a JOSE `alg` value to the JWK `kty` it requires. */
private fun ktyForAlg(alg: String): String? =
    when {
        alg.startsWith("RS") || alg.startsWith("PS") -> "RSA"
        alg.startsWith("ES") -> "EC"
        alg == "EdDSA" -> "OKP"
        else -> null
    }
