/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typed handle to a single attribute within an [AttributeBag].
 *
 * The value is an opaque string; interpretation (dotted path, JSON pointer, flat key) is the
 * responsibility of the producer. Consumers should treat the value as an identity key and not
 * attempt to parse structure out of it.
 *
 * Serialized as a JSON string primitive (the bare [value]) so it can be used as a map key in
 * containers like `Map<AttributePath, AttributeRecord>` — kotlinx-serialization rejects
 * `CLASS`-kind keys in JSON maps by default.
 */
@JsExportCompat
@Serializable(with = AttributePathSerializer::class)
data class AttributePath(
    val value: String,
)

object AttributePathSerializer : KSerializer<AttributePath> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.attribute.flow.AttributePath", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttributePath
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): AttributePath = AttributePath(decoder.decodeString())
}

/**
 * Identifier for a user-supplied input field, used by [UserInputAttribute] to bind the value
 * a user enters for a form / prompt into a downstream attribute.
 *
 * Serialized as a JSON string primitive so it works as a map key (see [AttributePath]).
 */
@JsExportCompat
@Serializable(with = InputFieldIdSerializer::class)
data class InputFieldId(
    val value: String,
)

object InputFieldIdSerializer : KSerializer<InputFieldId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.attribute.flow.InputFieldId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: InputFieldId
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): InputFieldId = InputFieldId(decoder.decodeString())
}

/**
 * Opaque identifier for the producer of an attribute value, carried on every
 * [AttributeRecord] as its `producerId`. The string payload is producer-defined (e.g. an IDV
 * node id, an OID4VCI credential request id, a tabular row id, a backend service id). Keeping
 * provenance generic avoids coupling the attribute-flow primitives to any particular
 * consumer's identifier type.
 *
 * Serialized as a JSON string primitive so it works as a map key (see [AttributePath]).
 */
@JsExportCompat
@Serializable(with = AttributeProvenanceRefSerializer::class)
data class AttributeProvenanceRef(
    val value: String,
) {
    companion object {
        /**
         * The canonical provenance ref for a connector-produced attribute: `connector:<bindingId>`.
         * Producers writing connector provenance and consumers checking membership against it must
         * both derive the ref through this factory so the string format exists once.
         */
        fun forConnector(bindingId: String): AttributeProvenanceRef = AttributeProvenanceRef("connector:$bindingId")
    }
}

object AttributeProvenanceRefSerializer : KSerializer<AttributeProvenanceRef> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.attribute.flow.AttributeProvenanceRef", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttributeProvenanceRef
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): AttributeProvenanceRef = AttributeProvenanceRef(decoder.decodeString())
}
