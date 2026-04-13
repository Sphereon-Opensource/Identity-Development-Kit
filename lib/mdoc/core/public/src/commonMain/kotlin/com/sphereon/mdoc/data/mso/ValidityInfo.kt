/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.mdoc.data.mso

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.nullable
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.TDate
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTDate
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.api.Base64Serializer
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.mdoc.MdocConst
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic




@JsExportCompat
@Serializable(with = ValidityInfoSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidityInfo", exact = true)
data class ValidityInfo(
    val signed: TDate,
    val validFrom: TDate,
    val validUntil: TDate,
    val expectedUpdate: TDate? = null,
) : CborStructure<ValidityInfo, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ValidityInfo

        if (signed != other.signed) return false
        if (validFrom != other.validFrom) return false
        if (validUntil != other.validUntil) return false
        if (expectedUpdate != other.expectedUpdate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signed.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + validUntil.hashCode()
        result = 31 * result + (expectedUpdate?.hashCode() ?: 0)
        return result
    }

    override fun cborBuilder(): CborBuilder<ValidityInfo> = cborMapBuilder(this) {
        SIGNED to signed.toCborStructure()
        VALID_FROM to validFrom.toCborStructure()
        VALID_UNTIL to validUntil.toCborStructure()
        optional(EXPECTED_UPDATE, expectedUpdate?.toCborStructure())
    }

    override fun toString(): String {
        return "ValidityInfo(signed=$signed, validFrom=$validFrom, validUntil=$validUntil, expectedUpdate=$expectedUpdate)"
    }


    companion object Decoder : HasFromCbor<CborMap<StringLabel, CborItem<*>>, ValidityInfo> {
        @JsStatic
        val SIGNED = StringLabel("signed")

        @JsStatic
        val VALID_FROM = StringLabel("validFrom")

        @JsStatic
        val VALID_UNTIL = StringLabel("validUntil")

        @JsStatic
        val EXPECTED_UPDATE = StringLabel("expectedUpdate")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>) = ValidityInfo(
            SIGNED.required<CborItem<*>>(structure).let {
                when (it) {
                    is CborTDate -> TDate(it.value)
                    is CborString -> TDate(it.value) // Note: Validity info should have tagged dates
                    is CborTagged<*> -> TDate(it.value as String)
                    else -> throw IllegalArgumentException(
                        "tdate object expected. Got ${it.cddl}"
                    )
                }
            },
            VALID_FROM.required<CborItem<*>>(structure).let {
                when (it) {
                    is CborTDate -> TDate(it.value)
                    is CborString -> TDate(it.value) // Note: Validity info should have tagged dates
                    is CborTagged<*> -> TDate(it.value as String)
                    else -> throw IllegalArgumentException(
                        "tdate object expected. Got ${it.cddl}"
                    )
                }
            },
            VALID_UNTIL.required<CborItem<*>>(structure).let {
                when (it) {
                    is CborTDate -> TDate(it.value)
                    is CborString -> TDate(it.value) // Note: Validity info should have tagged dates
                    is CborTagged<*> -> TDate(it.value as String)
                    else -> throw IllegalArgumentException(
                        "tdate object expected. Got ${it.cddl}"
                    )
                }
            },
            EXPECTED_UPDATE.optional<CborItem<*>>(structure)?.let {
                when (it) {
                    is CborTDate -> TDate(it.value)
                    is CborString -> TDate(it.value) // Note: Validity info should have tagged dates
                    is CborTagged<*> -> TDate(it.value as String)
                    else -> throw IllegalArgumentException("tdate object expected. Got ${it.cddl}")
                }
            }
        )

        @JsStatic
        fun fromDates(
            signed: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
            validFrom: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
            validUntil: LocalDateTimeKMP,
            expectedUpdate: LocalDateTimeKMP? = null,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = ValidityInfo(
            signed = TDate(signed.toMdocTdateString()),
            validFrom = TDate(validFrom.toMdocTdateString()),
            validUntil = TDate(validUntil.toMdocTdateString()),
            expectedUpdate = expectedUpdate?.toMdocTdateString()?.let { TDate(it) }
        )


        override fun decodeCbor(bytes: ByteArray): ValidityInfo = fromCborStructure(cborSerializer.decode(bytes))
    }



}

object ValidityInfoSerializer : KSerializer<ValidityInfo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.sphereon.mdoc.data.mso.ValidityInfo") {
        element(ValidityInfo.Decoder.SIGNED.value, TDate.serializer().descriptor)
        element(ValidityInfo.Decoder.VALID_FROM.value, TDate.serializer().descriptor)
        element(ValidityInfo.Decoder.VALID_UNTIL.value, TDate.serializer().descriptor)
        element(ValidityInfo.Decoder.EXPECTED_UPDATE.value, TDate.serializer().nullable.descriptor, isOptional = true)
        // Omit CDDL; include only 'original' from superclass when present
        element("original",Base64Serializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: ValidityInfo) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, TDate.serializer(), value.signed)
        composite.encodeSerializableElement(descriptor, 1, TDate.serializer(), value.validFrom)
        composite.encodeSerializableElement(descriptor, 2, TDate.serializer(), value.validUntil)
        val eu = value.expectedUpdate
        if (eu != null) {
            composite.encodeSerializableElement(descriptor, 3, TDate.serializer(), eu)
        }
        val orig = value.original
        if (orig != null) {
            composite.encodeSerializableElement(descriptor, 4, Base64Serializer, orig)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ValidityInfo {
        val dec = decoder.beginStructure(descriptor)
        var signed: TDate? = null
        var validFrom: TDate? = null
        var validUntil: TDate? = null
        var expectedUpdate: TDate? = null
        // We cannot set 'original' because it's defined in the superclass and not exposed in the data class constructor.
        // Still, consume it if present to support round-trip JSON compatibility.
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break@loop
                0 -> signed = dec.decodeSerializableElement(descriptor, 0, TDate.serializer())
                1 -> validFrom = dec.decodeSerializableElement(descriptor, 1, TDate.serializer())
                2 -> validUntil = dec.decodeSerializableElement(descriptor, 2, TDate.serializer())
                3 -> expectedUpdate = dec.decodeSerializableElement(descriptor, 3, TDate.serializer())
                4 -> {
                    // Consume and ignore 'original' for ValidityInfo
                    dec.decodeSerializableElement(descriptor, 4, Base64Serializer)
                }
                else -> throw kotlinx.serialization.SerializationException("Unknown index $index for ValidityInfo")
            }
        }
        dec.endStructure(descriptor)
        if (signed == null || validFrom == null || validUntil == null) {
            throw kotlinx.serialization.SerializationException("Missing required fields for ValidityInfo")
        }
        return ValidityInfo(
            signed = signed!!,
            validFrom = validFrom!!,
            validUntil = validUntil!!,
            expectedUpdate = expectedUpdate
        )
    }
}
