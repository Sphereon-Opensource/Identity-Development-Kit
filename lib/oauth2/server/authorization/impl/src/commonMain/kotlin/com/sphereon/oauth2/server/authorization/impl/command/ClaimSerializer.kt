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

package com.sphereon.oauth2.server.authorization.impl.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Claims that the STS sets itself and must not be overridden by external/additional claims.
 */
val JWT_RESERVED_CLAIMS =
    setOf(
        "iss",
        "sub",
        "aud",
        "exp",
        "iat",
        "nbf",
        "jti",
        "auth_time",
        "nonce",
        "at_hash",
        "c_hash",
        "azp",
        "typ",
        "sid",
        "acr",
        "amr",
        "scope",
        "token_type",
        "client_id",
        "cnf",
    )

/**
 * Puts additional claims into a JsonObjectBuilder, skipping reserved JWT claims
 * that the STS controls itself.
 */
fun JsonObjectBuilder.putClaims(
    claims: Map<String, Any>,
    reservedClaims: Set<String> = JWT_RESERVED_CLAIMS,
) {
    claims.forEach { (key, value) ->
        if (key !in reservedClaims) {
            putClaimValue(key, value)
        }
    }
}

/**
 * Serializes a single claim value into the JsonObjectBuilder.
 *
 * Accepts the native Kotlin types (String / Number / Boolean / List) the AS
 * produces directly, **and** the [JsonElement] family that flows in from cached
 * upstream-claim bags (the federation outcome handler stores claims as
 * `Map<String, JsonElement>`, then projects them through `UserInfo.attributes`
 * unchanged). Without explicit handling for [JsonPrimitive] / [JsonNull] /
 * [JsonElement], every value coming off that path falls through the `when`
 * unmatched and is silently dropped — the symptom we saw with the federated
 * id_token retaining only `name` + `email` (the two claims `UserInfo` re-emits
 * as `String`).
 */
fun JsonObjectBuilder.putClaimValue(
    key: String,
    value: Any,
) {
    when (value) {
        is String -> {
            put(key, value)
        }

        is Number -> {
            put(key, JsonPrimitive(value))
        }

        is Boolean -> {
            put(key, value)
        }

        is JsonObject -> {
            put(key, value)
        }

        is JsonArray -> {
            put(key, value)
        }

        // JsonElement subtypes (other than JsonObject/JsonArray, handled above).
        // Putting the JsonElement straight in preserves the source type
        // information (string vs number vs boolean vs null) without round-tripping
        // through Any-typed conversions that would lose it.
        is JsonNull -> {
            put(key, JsonNull)
        }

        is JsonPrimitive -> {
            put(key, value)
        }

        is JsonElement -> {
            put(key, value)
        }

        is List<*> -> {
            put(
                key,
                buildJsonArray {
                    value.forEach { item ->
                        when (item) {
                            is String -> add(JsonPrimitive(item))
                            is Number -> add(JsonPrimitive(item))
                            is Boolean -> add(JsonPrimitive(item))
                            is JsonObject -> add(item)
                            is JsonArray -> add(item)
                            is JsonNull -> add(JsonNull)
                            is JsonPrimitive -> add(item)
                            is JsonElement -> add(item)
                            else -> item?.toString()?.let { add(JsonPrimitive(it)) }
                        }
                    }
                },
            )
        }
    }
}
