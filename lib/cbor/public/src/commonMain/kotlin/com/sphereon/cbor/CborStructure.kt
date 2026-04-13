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

package com.sphereon.cbor

import kotlinx.serialization.Serializable
import com.sphereon.core.api.Base64Serializer
import com.sphereon.core.compat.JsExportCompat
abstract class CborJsonViewStructure<CborViewType, JsonViewType, CborTime: CborItem<*>>(cddl: CDDLType, override val original: ByteArray? = null): CborStructure<CborViewType, CborTime>(cddl, original) {
    abstract fun toJson(): JsonViewType
}

@JsExportCompat
@Serializable(with = CborStructureSerializer::class)
abstract class CborStructure<out CborViewType, CborType: CborItem<*>>(
    cddl: CDDLType,
    override val original: ByteArray? = null
) : CborBaseItem(cddl), HasToCborWithOriginal<CborType> {

    abstract fun cborBuilder(): CborBuilder<CborViewType>
    override fun encodeCbor(): ByteArray {
        // If the data came in from remote let's make sure we return the original
        if (original != null) {
            return original!!
        }
        return cborBuilder().encodedBuild()
    }

    fun isUserConstructed(): Boolean = original == null
    fun isDecoded(): Boolean = original != null
    fun canBeModified(): Boolean = original == null


    @Suppress("UNCHECKED_CAST")
    override fun toCborStructure(): CborType {
        return cborBuilder().build() as CborType
    }
}




internal object CborStructureSerializer : kotlinx.serialization.KSerializer<CborStructure<*, *>> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.buildClassSerialDescriptor("com.sphereon.cbor.CborStructure") {
            element("cddl", CDDLSerializer.descriptor)
            element("original", Base64Serializer.descriptor, isOptional = true)
        }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: CborStructure<*, *>) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, CDDLSerializer, value.cddl as CDDL)
        val orig = value.original
        if (orig != null) {
            composite.encodeSerializableElement(descriptor, 1, com.sphereon.core.api.Base64Serializer, orig)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): CborStructure<*, *> {
        throw kotlinx.serialization.SerializationException("CborStructure is abstract and cannot be deserialized directly. Use a concrete subtype.")
    }
}
