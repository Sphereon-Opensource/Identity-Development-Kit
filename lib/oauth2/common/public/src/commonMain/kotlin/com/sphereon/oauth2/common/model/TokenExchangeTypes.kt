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

package com.sphereon.oauth2.common.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Token type identifiers for OAuth 2.0 Token Exchange (RFC 8693 Section 3)
 */
object TokenTypeIdentifier {
    const val ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token"
    const val REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token"
    const val ID_TOKEN = "urn:ietf:params:oauth:token-type:id_token"
    const val SAML1 = "urn:ietf:params:oauth:token-type:saml1"
    const val SAML2 = "urn:ietf:params:oauth:token-type:saml2"
    const val JWT = "urn:ietf:params:oauth:token-type:jwt"
}

/**
 * Actor claim for delegation chains (RFC 8693 Section 4.1)
 *
 * Represents the `act` claim in a JWT, which identifies the acting party
 * in a delegation chain. Supports nested delegation via the recursive `act` field.
 */
@Serializable(with = ActorClaimSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class ActorClaim(
    val sub: String,
    val act: ActorClaim? = null,
    val additionalClaims: Map<String, JsonElement> = emptyMap(),
)

/**
 * May-act claim (RFC 8693 Section 4.3)
 *
 * Represents the `may_act` claim on a subject token, which constrains
 * which actors are authorized to act on behalf of the subject.
 */
@Serializable(with = MayActClaimSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class MayActClaim(
    val sub: String,
    val additionalClaims: Map<String, JsonElement> = emptyMap(),
)

internal object ActorClaimSerializer : KSerializer<ActorClaim> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ActorClaim")

    private val knownKeys = setOf("sub", "act")

    override fun serialize(
        encoder: Encoder,
        value: ActorClaim,
    ) {
        require(encoder is JsonEncoder) { "ActorClaimSerializer only works with JSON format" }
        val jsonObject =
            buildJsonObject {
                put("sub", JsonPrimitive(value.sub))
                value.act?.let { actClaim ->
                    put("act", encoder.json.encodeToJsonElement(ActorClaimSerializer, actClaim))
                }
                value.additionalClaims.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ActorClaim {
        require(decoder is JsonDecoder) { "ActorClaimSerializer only works with JSON format" }
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalClaims = jsonObject.filterKeys { it !in knownKeys }
        return ActorClaim(
            sub =
                jsonObject["sub"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("sub is required in act claim"),
            act = jsonObject["act"]?.let { decoder.json.decodeFromJsonElement(ActorClaimSerializer, it) },
            additionalClaims = additionalClaims,
        )
    }
}

internal object MayActClaimSerializer : KSerializer<MayActClaim> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MayActClaim")

    private val knownKeys = setOf("sub")

    override fun serialize(
        encoder: Encoder,
        value: MayActClaim,
    ) {
        require(encoder is JsonEncoder) { "MayActClaimSerializer only works with JSON format" }
        val jsonObject =
            buildJsonObject {
                put("sub", JsonPrimitive(value.sub))
                value.additionalClaims.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): MayActClaim {
        require(decoder is JsonDecoder) { "MayActClaimSerializer only works with JSON format" }
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalClaims = jsonObject.filterKeys { it !in knownKeys }
        return MayActClaim(
            sub =
                jsonObject["sub"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("sub is required in may_act claim"),
            additionalClaims = additionalClaims,
        )
    }
}
