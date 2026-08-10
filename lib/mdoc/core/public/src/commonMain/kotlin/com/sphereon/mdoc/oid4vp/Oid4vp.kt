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

package com.sphereon.mdoc.oid4vp

import com.sphereon.cbor.json.HasToJsonString
import com.sphereon.cbor.json.toJsonDTO
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.mdoc.data.AbstractDataElementDef
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.json.oid4vpJsonSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPPresentationDefinition", exact = true)
sealed interface IOid4VPPresentationDefinition {
    val id: String

    @SerialName("input_descriptors")
    val input_descriptors: Array<out IOid4VPInputDescriptor>
}

@Serializable
@JsExportCompat
data class Oid4VPPresentationDefinition(
    @SerialName("id")
    override val id: String,
    @SerialName("input_descriptors")
    override val input_descriptors: Array<Oid4VPInputDescriptor>,
) : IOid4VPPresentationDefinition,
    HasToJsonString {
    fun toDocRequest(): DocRequest {
        val itemsBuilder = DeviceItemsRequest.Builder()
        val docRequestBuilder = DocRequest.Builder(deviceItemsRequestBuilder = itemsBuilder)
        input_descriptors.forEach { it.toDeviceItemsRequest(itemsBuilder) }
        return docRequestBuilder.build()
    }

    fun toJsonObject(): JsonObject = oid4vpJsonSerializer.parseToJsonElement(oid4vpJsonSerializer.encodeToString(this)).jsonObject

    fun toDTO() = toJsonDTO<IOid4VPPresentationDefinition>(this)

    fun toSerializedJson() = oid4vpJsonSerializer.encodeToString(this)

    override fun toJsonString() = toSerializedJson()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Oid4VPPresentationDefinition) {
            return false
        }

        if (id != other.id) {
            return false
        }
        if (!input_descriptors.contentEquals(other.input_descriptors)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + input_descriptors.contentHashCode()
        return result
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromDTO(presentationDefinition: IOid4VPPresentationDefinition) =
            with(presentationDefinition) {
                Oid4VPPresentationDefinition(
                    id,
                    input_descriptors = input_descriptors.map { Oid4VPInputDescriptor.fromDTO(it) }.toTypedArray(),
                )
            }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPInputDescriptor", exact = true)
sealed interface IOid4VPInputDescriptor {
    val id: DocType
    val format: IOid4VPFormat
    val constraints: IOid4VPConstraints
}

@Serializable
@JsExportCompat
data class Oid4VPInputDescriptor(
    @SerialName("id")
    override val id: DocType,
    @SerialName("format")
    override val format: Oid4VPFormat,
    @SerialName("constraints")
    override val constraints: Oid4VPConstraints,
) : IOid4VPInputDescriptor {
    fun toDeviceItemsRequest(builder: DeviceItemsRequest.Builder) {
        builder.withDocType(id) // For ISO 18015-7 the input descriptor id is the doc type
        constraints.fields.forEach {
            it.path.forEach { path ->
                run {
                    val (nameSpace, identifier) = assertedPathEntry(path)
                    builder.add(nameSpace, identifier, IntentToRetain(it.intent_to_retain))
                }
            }
        }
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromDTO(inputDescriptor: IOid4VPInputDescriptor): Oid4VPInputDescriptor =
            with(inputDescriptor) {
                Oid4VPInputDescriptor(
                    id = id,
                    format = Oid4VPFormat.fromDTO(format),
                    constraints = Oid4VPConstraints.fromDTO(constraints),
                )
            }

        @JsStatic
        @JvmStatic
        fun fromJsonObject(jsonObject: JsonObject): Oid4VPInputDescriptor =
            with(jsonObject) {
                Oid4VPInputDescriptor(
                    id = DocType(get("id")!!.jsonPrimitive.content),
                    format = Json.decodeFromJsonElement(Oid4VPFormat.serializer(), get("format")!!.jsonObject),
                    constraints = Json.decodeFromJsonElement(Oid4VPConstraints.serializer(), get("constraints")!!.jsonObject),
                )
            }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPFormat", exact = true)
sealed interface IOid4VPFormat {
    @SerialName("mso_mdoc")
    val mso_mdoc: IOid4VPSupportedAlgorithm?

    @SerialName("dc+sd-jwt")
    val dc_sd_jwt: IOid4VPSupportedAlgorithm?
}

@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPFormat", exact = true)
data class Oid4VPFormat(
    @SerialName("mso_mdoc") override val mso_mdoc: Oid4VPSupportedAlgorithm? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("dc+sd-jwt") override val dc_sd_jwt: Oid4VPSupportedAlgorithm? = null,
) : IOid4VPFormat {
    init {
        require(mso_mdoc == null || dc_sd_jwt == null) { "requires that either mso_mdoc or dc+sd-jwt is present, but both are present" }
        require(mso_mdoc != null || dc_sd_jwt != null) { "requires that either mso_mdoc or dc+sd-jwt is present, but both are absent" }
        require(mso_mdoc == null || mso_mdoc.algorithmObjects.isNotEmpty()) { "ISO 18015-7 requires that mso_mdoc contains at least one algorithm" }
        require(dc_sd_jwt == null || dc_sd_jwt.algorithmObjects.isNotEmpty()) { "requires that dc+sd-jwt contains at least one algorithm" }
    }

    fun validateAlgorithms(): Boolean {
        if (mso_mdoc != null) {
            return mso_mdoc.algorithmObjects.isNotEmpty()
        } else if (dc_sd_jwt != null) {
            return dc_sd_jwt.algorithmObjects.isNotEmpty()
        }
        return false
    }

    fun hasFormat(format: Oid4VPFormatIdentifier) = Json.encodeToString(this).contains(format.value)

    companion object {
        @JsStatic
        @JvmStatic
        fun fromDTO(dto: IOid4VPFormat) =
            with(dto) {
                Oid4VPFormat(
                    mso_mdoc = mso_mdoc?.let { Oid4VPSupportedAlgorithm.fromDTO(it) },
                    dc_sd_jwt = dc_sd_jwt?.let { Oid4VPSupportedAlgorithm.fromDTO(it) },
                )
            }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPSupportedAlgorithm", exact = true)
sealed interface IOid4VPSupportedAlgorithm {
    val alg: Array<String>
}

@Serializable
@JsExportCompat
data class Oid4VPSupportedAlgorithm(
    override val alg: Array<String>,
) : IOid4VPSupportedAlgorithm {
    @Transient
    val algorithmObjects = alg.map { a -> CoseAlgorithm.fromName(a) }.toTypedArray()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Oid4VPSupportedAlgorithm) {
            return false
        }

        if (!alg.contentEquals(other.alg)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = alg.contentHashCode()

    companion object {
        @JsStatic
        @JvmStatic
        fun fromDTO(dto: IOid4VPSupportedAlgorithm) = with(dto) { Oid4VPSupportedAlgorithm(alg = alg) }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPConstraints", exact = true)
sealed interface IOid4VPConstraints {
    @SerialName("limit_disclosure")
    val limit_disclosure: String

    @SerialName("fields")
    val fields: Array<out IOid4VPConstraintField>
}

@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPConstraints", exact = true)
data class Oid4VPConstraints(
    @SerialName("fields")
    override val fields: Array<Oid4VPConstraintField>,
    @SerialName("limit_disclosure")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val limit_disclosure: String = "required",
) : IOid4VPConstraints {
    init {
        require(limit_disclosure == "required") { "Limit disclosure must have the value 'required' according to ISO 18013-7" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Oid4VPConstraints) {
            return false
        }

        if (limit_disclosure != other.limit_disclosure) {
            return false
        }
        if (!fields.contentEquals(other.fields)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = limit_disclosure.hashCode()
        result = 31 * result + fields.contentHashCode()
        return result
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromDTO(constraints: IOid4VPConstraints) =
            with(constraints) {
                Oid4VPConstraints(
                    fields = fields.map { Oid4VPConstraintField.fromDTO(it) }.toTypedArray(),
                    limit_disclosure = "required",
                )
            }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPConstraintField", exact = true)
sealed interface IOid4VPConstraintField {
    @SerialName("path")
    val path: Array<String>

    @SerialName("intent_to_retain")
    val intent_to_retain: Boolean
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPConstraintField", exact = true)
data class Oid4VPConstraintField(
    @SerialName("path")
    override val path: Array<String> = arrayOf(),
    @SerialName("intent_to_retain")
    override val intent_to_retain: Boolean,
) : IOid4VPConstraintField {
    init {
        this.assertValidPath()
    }

    private fun assertValidPath() {
        check(this.path.isNotEmpty()) { "OID4VP constraint field path cannot be empty" }
        path.forEach { assertValidPathEntry(it) }
    }

    private fun assertValidPathEntry(pathEntry: String) {
        // We do nothing with the result, as it will throw an exception anyway if invalid
        assertedPathEntry(pathEntry)
    }

    companion object {
        @JsStatic
        @JsName("fromElementIdentifiers")
        @JvmStatic
        fun fromElementIdentifiers(
            nameSpace: String,
            elementIdentifiers: Array<String>,
            intentToRetain: Boolean,
        ): Oid4VPConstraintField = Oid4VPConstraintField(intent_to_retain = intentToRetain, path = elementIdentifiers.map { "$['$nameSpace']['$it']" }.toTypedArray())

        @JsStatic
        @JsName("fromIssuerSignedItemCbor")
        @JvmStatic
        fun fromIssuerSignedItem(
            nameSpace: String,
            issuerSignedItem: IssuerSignedItem<*>,
            intentToRetain: Boolean,
        ): Oid4VPConstraintField =
            Oid4VPConstraintField(
                intent_to_retain = intentToRetain,
                path = arrayOf("$['$nameSpace']['${issuerSignedItem.elementIdentifier}']"),
            )

        @JsStatic
        @JsName("fromDTO")
        @JvmStatic
        fun fromDTO(dto: IOid4VPConstraintField) = with(dto) { Oid4VPConstraintField(intent_to_retain = intent_to_retain, path = path) }

        @JsStatic
        @JsName("fromDataElementDef")
        @JvmStatic
        fun fromDataElementDef(
            dataElementDef: AbstractDataElementDef,
            intentToRetain: Boolean,
        ): Oid4VPConstraintField =
            Oid4VPConstraintField(
                intent_to_retain = intentToRetain,
                path = arrayOf("$['${dataElementDef.nameSpace}']['${dataElementDef.identifier}']"),
            )
    }
}
/*

@JsExportCompat
@Serializable(with = Oid4VPLimitDisclosureSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPLimitDisclosure", exact = true)
enum class Oid4VPLimitDisclosure(val value: String) {
    REQUIRED("required");

    object Static {
        fun fromValue(value: String) = Oid4VPLimitDisclosure.entries.find { value == it.value }
    }
}

internal object Oid4VPLimitDisclosureSerializer : KSerializer<Oid4VPLimitDisclosure> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Oid4VPLimitDisclosure", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Oid4VPLimitDisclosure) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Oid4VPLimitDisclosure {
        val value = decoder.decodeString()
        return Oid4VPLimitDisclosure.fromValue(value) ?: throw IllegalArgumentException("Invalid value for limit disclosure ${value}")
    }
}
*/

@JsExportCompat
@Serializable(with = Oid4VPFormatsSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPFormatIdentifier", exact = true)
enum class Oid4VPFormatIdentifier(
    val value: String,
) {
    @JsName("MSO_MDOC")
    @SerialName("mso_mdoc")
    MSO_MDOC("mso_mdoc"),

    @JsName("SD_JWT_VC")
    @SerialName("dc+sd-jwt")
    SD_JWT_VC("dc+sd-jwt"),
    ;

    companion object {
        @JsStatic
        @JvmStatic
        fun fromValue(value: String) = entries.find { value == it.value }
    }
}

object Oid4VPFormatsSerializer : KSerializer<Oid4VPFormatIdentifier> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Oid4VPFormats", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Oid4VPFormatIdentifier,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Oid4VPFormatIdentifier {
        val value = decoder.decodeString()
        return requireNotNull(Oid4VPFormatIdentifier.fromValue(value)) { "Invalid value for format $value" }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4VPPresentationSubmission", exact = true)
sealed interface IOid4VPPresentationSubmission {
    @SerialName("definition_id")
    val definition_id: String
    val id: String

    @SerialName("descriptor_map")
    val descriptor_map: Array<out IOid4vpSubmissionDescriptor>
}

@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4VPPresentationSubmission", exact = true)
data class Oid4VPPresentationSubmission(
    @SerialName("definition_id")
    override val definition_id: String,
    @SerialName("id")
    override val id: String,
    @SerialName("descriptor_map")
    override val descriptor_map: Array<Oid4vpSubmissionDescriptor>,
) : IOid4VPPresentationSubmission {
    fun assertValid(pd: IOid4VPPresentationDefinition) {
        val definition = Oid4VPPresentationDefinition.fromDTO(pd)
        require(definition.id == definition_id) {
            "Definition id ${definition.id} is different from definition_id in presentation submission: $definition_id"
        }
        definition.input_descriptors.forEach { inputDescriptor ->
            require(descriptor_map.find { mapItem -> mapItem.id === inputDescriptor.id.toString() } !== null) {
                "Presentation definition input descriptor id ${inputDescriptor.id} was not present in presentation submission"
            }
        }
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromPresentationDefinition(
            pd: IOid4VPPresentationDefinition,
            id: String = Uuid.v4String(),
        ): Oid4VPPresentationSubmission =
            Oid4VPPresentationSubmission(
                definition_id = pd.id,
                id = id,
                descriptor_map = pd.input_descriptors.map { Oid4vpSubmissionDescriptor.fromInputDescriptor(it) }.toTypedArray(),
            )

        @JsStatic
        @JvmStatic
        fun fromDTO(dto: IOid4VPPresentationSubmission) =
            with(dto) {
                Oid4VPPresentationSubmission(
                    definition_id = definition_id,
                    id = id,
                    descriptor_map = descriptor_map.map { Oid4vpSubmissionDescriptor.fromDTO(it) }.toTypedArray(),
                )
            }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IOid4vpSubmissionDescriptor", exact = true)
sealed interface IOid4vpSubmissionDescriptor {
    val id: String
    val format: String
    val path: String
}

@Serializable
@JsExportCompat
data class Oid4vpSubmissionDescriptor(
    @SerialName("id")
    override val id: String,
    @SerialName("format")
    override val format: String,
    @SerialName("path")
    override val path: String,
) : IOid4vpSubmissionDescriptor {
    companion object {
        @JsStatic
        @JvmStatic
        fun fromInputDescriptor(descriptor: IOid4VPInputDescriptor): Oid4vpSubmissionDescriptor =
            with(descriptor) {
                val formatId =
                    if (format.dc_sd_jwt?.alg?.isNotEmpty() == true) {
                        Oid4VPFormatIdentifier.SD_JWT_VC
                    } else {
                        Oid4VPFormatIdentifier.MSO_MDOC
                    }
                val path =
                    if (formatId == Oid4VPFormatIdentifier.MSO_MDOC) {
                        "$"
                    } else {
                        descriptor.constraints.fields[0].path[0]
                    } // fixme
                Oid4vpSubmissionDescriptor(
                    id = id.toString(),
                    format = formatId.value,
                    path = path,
                )
            }

        @JsStatic
        @JvmStatic
        fun fromDTO(dto: IOid4vpSubmissionDescriptor) = with(dto) { Oid4vpSubmissionDescriptor(id = id, format = format, path = path) }
    }
}
