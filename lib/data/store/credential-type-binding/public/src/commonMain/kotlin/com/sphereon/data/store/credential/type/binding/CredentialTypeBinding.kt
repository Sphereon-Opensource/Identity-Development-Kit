package com.sphereon.data.store.credential.type.binding

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.credential.design.model.SemanticAttributeSetRef
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A role-independent registry record binding a semantic attribute set to its credential
 * wire-format identity (`format` + `vct` or `doctype`). One [SemanticAttributeSetRef] can map
 * to multiple credential types; each binding denormalizes the format-specific type
 * identifier so verifiers (which exist without an issuer) can resolve credential-type
 * identity directly, without going through issuance metadata.
 *
 * @property id stable identifier for this binding.
 * @property semanticAttributeSetRef the semantic attribute set this credential type carries.
 * @property credentialConfigurationId optional OID4VCI credential configuration id this
 *  binding maps to; null when the tenant does not issue this credential type.
 * @property format the credential wire format (`dc+sd-jwt`, `mso_mdoc`, `jwt_vc_json`, ...).
 * @property vct SD-JWT VC type identifier; required when [format] is `dc+sd-jwt`.
 * @property doctype mdoc document type; required when [format] is `mso_mdoc`.
 * @property claimMappingConfigId optional reference to a claim-mapping configuration; null
 *  means claim names map 1:1 to the semantic attribute paths.
 * @property attributeProfileId optional attribute-profile this binding maps to, set alongside
 *  [semanticAttributeSetRef] to bridge the legacy OCA-set identity to the enterprise
 *  attribute-profile model. Null on legacy bindings keyed only by [semanticAttributeSetRef].
 * @property attributeProfileVersion optional pinned profile snapshot version for
 *  [attributeProfileId]; null resolves the current version.
 */
@JsExportCompat
@Serializable
data class CredentialTypeBinding(
    val id: String,
    val semanticAttributeSetRef: SemanticAttributeSetRef,
    val credentialConfigurationId: String? = null,
    val format: String,
    val vct: String? = null,
    val doctype: String? = null,
    val claimMappingConfigId: String? = null,
    val attributeProfileId: Uuid? = null,
    val attributeProfileVersion: Long? = null,
)
