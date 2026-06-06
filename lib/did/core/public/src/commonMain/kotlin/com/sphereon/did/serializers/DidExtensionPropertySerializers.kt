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
 *
 */

package com.sphereon.did.serializers

import com.sphereon.core.api.json.StringOrStringListSerializer
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * Surrogate-based JSON serializer for [DidDocument].
 *
 * DID Core extension properties live as unknown top-level keys on the wire, not under a
 * literal `"extensions"` object. To support that while keeping the model-friendly
 * `extensions: Map<String, JsonElement>` bag, the serializer round-trips through the
 * private [DidDocumentSurrogate] (the W3C-known fields only) and merges the unknown keys
 * via [collectExtensions] / overlay on encode.
 *
 * On decode, any value originally written under the legacy `"extensions"` object is also
 * folded into `extensions` so older payloads round-trip cleanly.
 *
 * JSON-only: requires a [JsonEncoder] / [JsonDecoder]; throws on any other format.
 */
object DidDocumentWithExtensionsSerializer : KSerializer<DidDocument> {
    override val descriptor: SerialDescriptor = DidDocumentSurrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: DidDocument
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("DidDocumentWithExtensionsSerializer is JSON-only")
        val base =
            jsonEncoder.json
                .encodeToJsonElement(
                    DidDocumentSurrogate.serializer(),
                    DidDocumentSurrogate(
                        context = value.context,
                        id = value.id,
                        controller = value.controller,
                        alsoKnownAs = value.alsoKnownAs,
                        verificationMethod = value.verificationMethod,
                        authentication = value.authentication,
                        assertionMethod = value.assertionMethod,
                        keyAgreement = value.keyAgreement,
                        capabilityInvocation = value.capabilityInvocation,
                        capabilityDelegation = value.capabilityDelegation,
                        service = value.service,
                    ),
                ).jsonObject
                .toMutableMap()
        value.extensions
            .filterKeys { it !in DID_DOCUMENT_KEYS }
            .forEach { (key, json) -> base[key] = json }
        jsonEncoder.encodeJsonElement(JsonObject(base))
    }

    override fun deserialize(decoder: Decoder): DidDocument {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DidDocumentWithExtensionsSerializer is JSON-only")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val surrogate =
            jsonDecoder.json.decodeFromJsonElement(
                DidDocumentSurrogate.serializer(),
                JsonObject(element.filterKeys { it in DID_DOCUMENT_KEYS }),
            )
        return DidDocument(
            context = surrogate.context,
            id = surrogate.id,
            controller = surrogate.controller,
            alsoKnownAs = surrogate.alsoKnownAs,
            verificationMethod = surrogate.verificationMethod,
            authentication = surrogate.authentication,
            assertionMethod = surrogate.assertionMethod,
            keyAgreement = surrogate.keyAgreement,
            capabilityInvocation = surrogate.capabilityInvocation,
            capabilityDelegation = surrogate.capabilityDelegation,
            service = surrogate.service,
            extensions = element.collectExtensions(DID_DOCUMENT_KEYS),
        )
    }
}

/**
 * Surrogate-based JSON serializer for [VerificationMethod].
 *
 * Mirrors the [DidDocumentWithExtensionsSerializer] pattern at the VM level: round-trips
 * through [VerificationMethodSurrogate] for the W3C-known properties, and folds any
 * unknown top-level keys into [VerificationMethod.extensions] so the wire form is
 * preserved verbatim across decode/encode.
 */
object VerificationMethodWithExtensionsSerializer : KSerializer<VerificationMethod> {
    override val descriptor: SerialDescriptor = VerificationMethodSurrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: VerificationMethod
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("VerificationMethodWithExtensionsSerializer is JSON-only")
        val base =
            jsonEncoder.json
                .encodeToJsonElement(
                    VerificationMethodSurrogate.serializer(),
                    VerificationMethodSurrogate(
                        id = value.id,
                        type = value.type,
                        controller = value.controller,
                        publicKeyJwk = value.publicKeyJwk,
                        publicKeyMultibase = value.publicKeyMultibase,
                        blockchainAccountId = value.blockchainAccountId,
                        expiresAt = value.expiresAt,
                        revokedAt = value.revokedAt,
                    ),
                ).jsonObject
                .toMutableMap()
        value.extensions
            .filterKeys { it !in VERIFICATION_METHOD_KEYS }
            .forEach { (key, json) -> base[key] = json }
        jsonEncoder.encodeJsonElement(JsonObject(base))
    }

    override fun deserialize(decoder: Decoder): VerificationMethod {
        val jsonDecoder = decoder as? JsonDecoder ?: error("VerificationMethodWithExtensionsSerializer is JSON-only")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val surrogate =
            jsonDecoder.json.decodeFromJsonElement(
                VerificationMethodSurrogate.serializer(),
                JsonObject(element.filterKeys { it in VERIFICATION_METHOD_KEYS }),
            )
        return VerificationMethod(
            id = surrogate.id,
            type = surrogate.type,
            controller = surrogate.controller,
            publicKeyJwk = surrogate.publicKeyJwk,
            publicKeyMultibase = surrogate.publicKeyMultibase,
            blockchainAccountId = surrogate.blockchainAccountId,
            expiresAt = surrogate.expiresAt,
            revokedAt = surrogate.revokedAt,
            extensions = element.collectExtensions(VERIFICATION_METHOD_KEYS),
        )
    }
}

/**
 * Surrogate-based JSON serializer for [DidService].
 *
 * Same pattern as [DidDocumentWithExtensionsSerializer], scoped to a single service entry.
 * Unknown top-level keys on the service object are preserved in [DidService.extensions]
 * for lossless round-trip.
 */
object DidServiceWithExtensionsSerializer : KSerializer<DidService> {
    override val descriptor: SerialDescriptor = DidServiceSurrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: DidService
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("DidServiceWithExtensionsSerializer is JSON-only")
        val base =
            jsonEncoder.json
                .encodeToJsonElement(
                    DidServiceSurrogate.serializer(),
                    DidServiceSurrogate(
                        id = value.id,
                        type = value.type,
                        serviceEndpoint = value.serviceEndpoint,
                    ),
                ).jsonObject
                .toMutableMap()
        value.extensions
            .filterKeys { it !in DID_SERVICE_KEYS }
            .forEach { (key, json) -> base[key] = json }
        jsonEncoder.encodeJsonElement(JsonObject(base))
    }

    override fun deserialize(decoder: Decoder): DidService {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DidServiceWithExtensionsSerializer is JSON-only")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val surrogate =
            jsonDecoder.json.decodeFromJsonElement(
                DidServiceSurrogate.serializer(),
                JsonObject(element.filterKeys { it in DID_SERVICE_KEYS }),
            )
        return DidService(
            id = surrogate.id,
            type = surrogate.type,
            serviceEndpoint = surrogate.serviceEndpoint,
            extensions = element.collectExtensions(DID_SERVICE_KEYS),
        )
    }
}

/**
 * Plain-shape mirror of [DidDocument] containing only the W3C DID Core schema fields.
 *
 * Used by [DidDocumentWithExtensionsSerializer] to delegate the boring half of the
 * encode/decode (the canonical fields) to kotlinx.serialization's generated codec, so the
 * outer serializer only has to worry about overlaying / harvesting the extension keys.
 * Private — never leaks outside this file.
 */
@Serializable
private data class DidDocumentSurrogate(
    @SerialName("@context")
    val context: List<String> = listOf(DidDocument.DEFAULT_CONTEXT),
    val id: String,
    @Serializable(with = StringOrStringListSerializer::class)
    val controller: List<String> = emptyList(),
    val alsoKnownAs: List<String>? = null,
    val verificationMethod: List<VerificationMethod>? = null,
    val authentication: List<VerificationMethodOrReference>? = null,
    val assertionMethod: List<VerificationMethodOrReference>? = null,
    val keyAgreement: List<VerificationMethodOrReference>? = null,
    val capabilityInvocation: List<VerificationMethodOrReference>? = null,
    val capabilityDelegation: List<VerificationMethodOrReference>? = null,
    val service: List<DidService>? = null,
)

/**
 * Plain-shape mirror of [VerificationMethod] containing only the W3C-known VM fields.
 * Same role as [DidDocumentSurrogate], scoped to a verification method.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class VerificationMethodSurrogate(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: com.sphereon.crypto.core.jose.Jwk? = null,
    val publicKeyMultibase: String? = null,
    val blockchainAccountId: String? = null,
    // Canonical wire key is "expires" (W3C); legacy "expiresAt" still accepted on input.
    @SerialName("expires")
    @JsonNames("expiresAt")
    val expiresAt: Instant? = null,
    // Canonical wire key is "revoked" (W3C); legacy "revokedAt" still accepted on input.
    @SerialName("revoked")
    @JsonNames("revokedAt")
    val revokedAt: Instant? = null,
)

/**
 * Plain-shape mirror of [DidService] containing only the W3C-known service-entry fields.
 * Same role as [DidDocumentSurrogate], scoped to one service entry.
 */
@Serializable
private data class DidServiceSurrogate(
    val id: String,
    @Serializable(with = StringOrStringListSerializer::class)
    val type: List<String>,
    val serviceEndpoint: JsonElement,
)

/**
 * Returns every key on this JSON object that is *not* part of the W3C-defined [knownKeys]
 * set, plus any keys nested under a legacy `"extensions"` object. Top-level wins on key
 * collision. Used during decode to populate the model's `extensions` bag.
 */
private fun JsonObject.collectExtensions(knownKeys: Set<String>): Map<String, JsonElement> {
    val explicit = (this["extensions"] as? JsonObject)?.toMap().orEmpty()
    val topLevel = filterKeys { it !in knownKeys }
    return explicit + topLevel
}

/** W3C DID Core property names recognised on a DID document. Anything else is an extension. */
private val DID_DOCUMENT_KEYS =
    setOf(
        "@context",
        "id",
        "controller",
        "alsoKnownAs",
        "verificationMethod",
        "authentication",
        "assertionMethod",
        "keyAgreement",
        "capabilityInvocation",
        "capabilityDelegation",
        "service",
        "extensions",
    )

/** W3C DID Core property names recognised on a verification method. */
private val VERIFICATION_METHOD_KEYS =
    setOf(
        "id",
        "type",
        "controller",
        "publicKeyJwk",
        "publicKeyMultibase",
        "blockchainAccountId",
        "expires",
        "expiresAt",
        "revoked",
        "revokedAt",
        "extensions",
    )

/** W3C DID Core property names recognised on a service entry. */
private val DID_SERVICE_KEYS =
    setOf(
        "id",
        "type",
        "serviceEndpoint",
        "extensions",
    )
