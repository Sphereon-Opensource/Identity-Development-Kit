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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@Serializable(with = ValidityInfoSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidityInfo", exact = true)
data class ValidityInfo(
    val signed: TDate,
    val validFrom: TDate,
    val validUntil: TDate,
    val expectedUpdate: TDate? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as ValidityInfo

        if (signed != other.signed) {
            return false
        }
        if (validFrom != other.validFrom) {
            return false
        }
        if (validUntil != other.validUntil) {
            return false
        }
        if (expectedUpdate != other.expectedUpdate) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = signed.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + validUntil.hashCode()
        result = 31 * result + (expectedUpdate?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "ValidityInfo(signed=$signed, validFrom=$validFrom, validUntil=$validUntil, expectedUpdate=$expectedUpdate)"

    companion object {
        @JsStatic
        @JvmStatic
        val SIGNED = StringLabel("signed")

        @JsStatic
        @JvmStatic
        val VALID_FROM = StringLabel("validFrom")

        @JsStatic
        @JvmStatic
        val VALID_UNTIL = StringLabel("validUntil")

        @JsStatic
        @JvmStatic
        val EXPECTED_UPDATE = StringLabel("expectedUpdate")

        @JsStatic
        @JvmStatic
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
            expectedUpdate = expectedUpdate?.toMdocTdateString()?.let { TDate(it) },
        )
    }
}

private const val INDEX_EXPECTED_UPDATE = 3

object ValidityInfoSerializer : KSerializer<ValidityInfo> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.sphereon.mdoc.data.mso.ValidityInfo") {
            element(ValidityInfo.SIGNED.value, TDate.serializer().descriptor)
            element(ValidityInfo.VALID_FROM.value, TDate.serializer().descriptor)
            element(ValidityInfo.VALID_UNTIL.value, TDate.serializer().descriptor)
            element(ValidityInfo.EXPECTED_UPDATE.value, TDate.serializer().nullable.descriptor, isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: ValidityInfo,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, TDate.serializer(), value.signed)
        composite.encodeSerializableElement(descriptor, 1, TDate.serializer(), value.validFrom)
        composite.encodeSerializableElement(descriptor, 2, TDate.serializer(), value.validUntil)
        val eu = value.expectedUpdate
        if (eu != null) {
            composite.encodeSerializableElement(descriptor, INDEX_EXPECTED_UPDATE, TDate.serializer(), eu)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ValidityInfo {
        val dec = decoder.beginStructure(descriptor)
        var signed: TDate? = null
        var validFrom: TDate? = null
        var validUntil: TDate? = null
        var expectedUpdate: TDate? = null
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break@loop
                0 -> signed = dec.decodeSerializableElement(descriptor, 0, TDate.serializer())
                1 -> validFrom = dec.decodeSerializableElement(descriptor, 1, TDate.serializer())
                2 -> validUntil = dec.decodeSerializableElement(descriptor, 2, TDate.serializer())
                INDEX_EXPECTED_UPDATE -> expectedUpdate = dec.decodeSerializableElement(descriptor, INDEX_EXPECTED_UPDATE, TDate.serializer())
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
            expectedUpdate = expectedUpdate,
        )
    }
}
