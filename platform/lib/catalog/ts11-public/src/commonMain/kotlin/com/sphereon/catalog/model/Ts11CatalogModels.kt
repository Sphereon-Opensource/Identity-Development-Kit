/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.catalog.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

private fun requireTs11Text(name: String, value: String, max: Int): String =
    value.trim().also {
        require(it.isNotEmpty()) { "$name must not be blank" }
        require(it.length <= max) { "$name must not exceed $max characters" }
    }

@JsExportCompat
@Serializable
enum class AttestationLevelOfSurety {
    iso_18045_high,
    iso_18045_moderate,
    iso_18045_enhanced_basic,
    iso_18045_basic;

    fun toWire(): String = if (this == iso_18045_enhanced_basic) "iso_18045_enhanced-basic" else name

    companion object {
        fun fromWire(value: String): AttestationLevelOfSurety? = when (value) {
            "iso_18045_high" -> iso_18045_high
            "iso_18045_moderate" -> iso_18045_moderate
            "iso_18045_enhanced-basic", "iso_18045_enhanced_basic" -> iso_18045_enhanced_basic
            "iso_18045_basic" -> iso_18045_basic
            else -> null
        }
    }
}

@JsExportCompat
@Serializable
enum class AttestationBindingType { claim, key, biometric, none }

@JsExportCompat
@Serializable
enum class AttestationFormatIdentifier {
    DC_SD_JWT, MSO_MDOC, JWT_VC_JSON, JWT_VC_JSON_LD, LDP_VC;

    fun toWire(): String = when (this) {
        DC_SD_JWT -> "dc+sd-jwt"
        MSO_MDOC -> "mso_mdoc"
        JWT_VC_JSON -> "jwt_vc_json"
        JWT_VC_JSON_LD -> "jwt_vc_json-ld"
        LDP_VC -> "ldp_vc"
    }

    companion object {
        fun fromWire(value: String): AttestationFormatIdentifier? = entries.firstOrNull { it.toWire() == value }
    }
}

@JsExportCompat
@Serializable
enum class TrustFrameworkType { aki, etsi_tl, openid_federation }

@JsExportCompat
@Serializable
enum class AttestationTypeKeyKind { VCT, DOCTYPE, SCHEMA_URI }

@JsExportCompat
@Serializable
data class AttestationTypeKey @JvmOverloads constructor(
    val kind: AttestationTypeKeyKind,
    val value: String,
) {
    init { requireTs11Text("attestation type value", value, 2048) }
}

@JsExportCompat
@Serializable
data class SchemaUriRef @JvmOverloads constructor(
    val formatIdentifier: String,
    val uri: String,
) {
    init {
        require(AttestationFormatIdentifier.fromWire(formatIdentifier) != null) { "unsupported formatIdentifier" }
        requireTs11Text("schema URI", uri, 4096)
    }
}

@JsExportCompat
@Serializable
data class TrustAuthority @JvmOverloads constructor(
    val frameworkType: TrustFrameworkType,
    val value: String,
    val isLOTE: Boolean? = null,
) {
    init {
        requireTs11Text("trust authority value", value, 4096)
    }
}

@JsExportCompat
@Serializable
class SchemaMeta private constructor(
    val id: String?,
    val version: String,
    val rulebookURI: String,
    @SerialName("trustedAuthorities") private val trustedAuthoritiesSnapshot: List<TrustAuthority> = emptyList(),
    val attestationLoS: String,
    val bindingType: String,
    @SerialName("supportedFormats") private val supportedFormatsSnapshot: List<String>,
    @SerialName("schemaURIs") private val schemaUriSnapshot: List<SchemaUriRef>,
    @Transient private val immutableSnapshot: Boolean = true,
) {
    @JvmOverloads
    @JsName("fromValues")
    constructor(
        id: String? = null,
        version: String,
        rulebookURI: String,
        trustedAuthorities: List<TrustAuthority> = emptyList(),
        attestationLoS: String,
        bindingType: String,
        supportedFormats: List<String>,
        schemaURIs: List<SchemaUriRef>,
    ) : this(
        id?.let { requireTs11Text("schema id", it, 256) },
        requireTs11Text("schema version", version, 128),
        requireTs11Text("rulebook URI", rulebookURI, 4096),
        trustedAuthorities.toList(),
        requireTs11Text("attestation LoS", attestationLoS, 128),
        requireTs11Text("binding type", bindingType, 128),
        supportedFormats.map { requireTs11Text("supported format", it, 128) }.toList(),
        schemaURIs.toList(),
        true,
    )

    val trustedAuthorities: List<TrustAuthority> get() = trustedAuthoritiesSnapshot.toList()
    val supportedFormats: List<String> get() = supportedFormatsSnapshot.toList()
    val schemaURIs: List<SchemaUriRef> get() = schemaUriSnapshot.toList()

    fun deepCopy(): SchemaMeta = SchemaMeta(
        id = id,
        version = version,
        rulebookURI = rulebookURI,
        trustedAuthorities = trustedAuthorities,
        attestationLoS = attestationLoS,
        bindingType = bindingType,
        supportedFormats = supportedFormats,
        schemaURIs = schemaURIs,
    )

    fun copy(
        id: String? = this.id,
        version: String = this.version,
        rulebookURI: String = this.rulebookURI,
        trustedAuthorities: List<TrustAuthority> = this.trustedAuthorities,
        attestationLoS: String = this.attestationLoS,
        bindingType: String = this.bindingType,
        supportedFormats: List<String> = this.supportedFormats,
        schemaURIs: List<SchemaUriRef> = this.schemaURIs,
    ): SchemaMeta = SchemaMeta(
        id = id,
        version = version,
        rulebookURI = rulebookURI,
        trustedAuthorities = trustedAuthorities,
        attestationLoS = attestationLoS,
        bindingType = bindingType,
        supportedFormats = supportedFormats,
        schemaURIs = schemaURIs,
    )

    override fun equals(other: Any?): Boolean = other is SchemaMeta &&
        id == other.id && version == other.version && rulebookURI == other.rulebookURI &&
        trustedAuthoritiesSnapshot == other.trustedAuthoritiesSnapshot && attestationLoS == other.attestationLoS &&
        bindingType == other.bindingType && supportedFormatsSnapshot == other.supportedFormatsSnapshot &&
        schemaUriSnapshot == other.schemaUriSnapshot

    override fun hashCode(): Int = listOf(id, version, rulebookURI, trustedAuthoritiesSnapshot, attestationLoS, bindingType, supportedFormatsSnapshot, schemaUriSnapshot).hashCode()

    override fun toString(): String = "SchemaMeta(id=$id, version=$version, rulebookURI=$rulebookURI)"
}
