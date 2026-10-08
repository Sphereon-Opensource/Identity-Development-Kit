package com.sphereon.data.credential.definition

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A dot-separated path identifying a claim within a [CredentialDefinition]. Supports nested claims
 * such as `address.postal_code` or `name.given`, mirroring how DCQL/SD-JWT address nested claims:
 * the path itself encodes the structure, so a [CredentialClaim] does not need a child tree.
 *
 * This is the IDK (open-core) structure-aware path used by the role-neutral free-form credential
 * definition. It is deliberately self-contained in the IDK layer; the EDK profile-bound resolver
 * maps its catalog/profile attribute paths onto this type when producing effective free-form claims.
 *
 * Serialized as a bare JSON string primitive so it can be used as a map key.
 *
 * @property value the raw dot-separated path string.
 */
@Serializable(with = ClaimPathSerializer::class)
data class ClaimPath(
    val value: String
) {
    /** The individual path segments split by `.`. */
    val segments: List<String> get() = value.split(".")

    /** The final segment of the path, representing the immediate claim name. */
    val leaf: String get() = segments.last()

    /** True when this path is a single, non-nested segment (no `.` separator). */
    val isSingleSegment: Boolean get() = !value.contains('.')

    /**
     * True when this is a well-formed path: non-blank overall and every segment is itself non-blank
     * (so neither a leading/trailing `.` nor an empty inner segment such as `a..b` is allowed).
     */
    val isWellFormed: Boolean get() = value.isNotBlank() && segments.all { it.isNotBlank() }
}

object ClaimPathSerializer : KSerializer<ClaimPath> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.data.credential.definition.ClaimPath", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ClaimPath
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): ClaimPath = ClaimPath(decoder.decodeString())
}
