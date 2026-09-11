/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.dataintegrity.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.builtins.ListSerializer

/**
 * JSON serializer for a Data Integrity proof.
 *
 * Data Integrity cryptosuites are extensible: suite-defined properties are
 * top-level members of the proof object, rather than a nested extension bag.
 * A generated serializer cannot retain those members, so this serializer
 * overlays [DataIntegrityProof.additionalProofProperties] on the typed fields
 * and rejects collisions instead of silently changing signed input.
 */
object DataIntegrityProofSerializer : KSerializer<DataIntegrityProof> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DataIntegrityProof")

    private const val TYPE = "type"
    private const val CRYPTOSUITE = "cryptosuite"
    private const val PROOF_PURPOSE = "proofPurpose"
    private const val VERIFICATION_METHOD = "verificationMethod"
    private const val PROOF_VALUE = "proofValue"
    private const val ID = "id"
    private const val CREATED = "created"
    private const val EXPIRES = "expires"
    private const val DOMAIN = "domain"
    private const val CHALLENGE = "challenge"
    private const val NONCE = "nonce"
    private const val PREVIOUS_PROOF = "previousProof"

    private val typedKeys: Set<String> = DataIntegrityProof.TYPED_PROPERTY_NAMES

    override fun serialize(
        encoder: Encoder,
        value: DataIntegrityProof,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("DataIntegrityProofSerializer is JSON-only")
        val conflicts = value.additionalProofProperties.keys intersect typedKeys
        require(conflicts.isEmpty()) {
            "DataIntegrityProof extension properties shadow typed properties: ${conflicts.sorted().joinToString()}"
        }

        val json = jsonEncoder.json
        val objectValue =
            buildJsonObject {
                // Required proof members are always emitted, including the
                // default type, because they are part of the proof config.
                put(TYPE, JsonPrimitive(value.type))
                put(CRYPTOSUITE, JsonPrimitive(value.cryptosuite))
                put(PROOF_PURPOSE, json.encodeToJsonElement(ProofPurpose.serializer(), value.proofPurpose))
                put(VERIFICATION_METHOD, JsonPrimitive(value.verificationMethod))
                put(PROOF_VALUE, JsonPrimitive(value.proofValue))
                value.id?.let { put(ID, JsonPrimitive(it)) }
                value.created?.let { put(CREATED, JsonPrimitive(it)) }
                value.expires?.let { put(EXPIRES, JsonPrimitive(it)) }
                value.domain?.let { put(DOMAIN, JsonPrimitive(it)) }
                value.domainSet?.let { values ->
                    put(DOMAIN, JsonArray(values.map(::JsonPrimitive)))
                }
                value.challenge?.let { put(CHALLENGE, JsonPrimitive(it)) }
                value.nonce?.let { put(NONCE, JsonPrimitive(it)) }
                value.previousProof?.let { previous ->
                    require(previous.isNotEmpty()) { "previousProof must not be empty" }
                    put(
                        PREVIOUS_PROOF,
                        if (previous.size == 1) {
                            JsonPrimitive(previous.single())
                        } else {
                            kotlinx.serialization.json.JsonArray(previous.map { JsonPrimitive(it) })
                        },
                    )
                }
                value.additionalProofProperties.forEach { (key, extension) -> put(key, extension) }
            }
        jsonEncoder.encodeJsonElement(objectValue)
    }

    override fun deserialize(decoder: Decoder): DataIntegrityProof {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DataIntegrityProofSerializer is JSON-only")
        val element = jsonDecoder.decodeJsonElement()
        val objectValue = element as? JsonObject ?: error("DataIntegrityProof must be a JSON object")
        val json = jsonDecoder.json

        fun requiredString(key: String): String =
            objectValue[key]?.let { json.decodeFromJsonElement(String.serializer(), it) }
                ?: error("DataIntegrityProof.$key is required")

        fun optionalString(key: String): String? =
            objectValue[key]
                ?.takeUnless { it is JsonNull }
                ?.let { json.decodeFromJsonElement(String.serializer(), it) }

        val previousProof =
            objectValue[PREVIOUS_PROOF]?.takeUnless { it is JsonNull }?.let {
                json.decodeFromJsonElement(PreviousProofSerializer, it)
            }
        val domainElement = objectValue[DOMAIN]?.takeUnless { it is JsonNull }
        val domain: String?
        val domainSet: List<String>?
        when (domainElement) {
            null -> {
                domain = null
                domainSet = null
            }
            is JsonPrimitive -> {
                domain = json.decodeFromJsonElement(String.serializer(), domainElement)
                domainSet = null
            }
            is JsonArray -> {
                domain = null
                domainSet = json.decodeFromJsonElement(ListSerializer(String.serializer()), domainElement)
            }
            else -> error("DataIntegrityProof.domain must be a string or an array of strings")
        }
        return DataIntegrityProof(
            type = objectValue[TYPE]?.let { json.decodeFromJsonElement(String.serializer(), it) }
                ?: DataIntegrityProof.TYPE_DATA_INTEGRITY,
            cryptosuite = requiredString(CRYPTOSUITE),
            proofPurpose =
                objectValue[PROOF_PURPOSE]?.let {
                    json.decodeFromJsonElement(ProofPurpose.serializer(), it)
                } ?: error("DataIntegrityProof.$PROOF_PURPOSE is required"),
            verificationMethod = requiredString(VERIFICATION_METHOD),
            proofValue = requiredString(PROOF_VALUE),
            id = optionalString(ID),
            created = optionalString(CREATED),
            expires = optionalString(EXPIRES),
            domain = domain,
            challenge = optionalString(CHALLENGE),
            nonce = optionalString(NONCE),
            previousProof = previousProof,
            additionalProofProperties = JsonObject(objectValue.filterKeys { it !in typedKeys }),
            domainSet = domainSet,
        )
    }
}
