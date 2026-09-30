/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.model

import com.sphereon.catalog.eu.model.AttributeReference
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.VersionStatus
import kotlinx.serialization.Serializable

/**
 * The fields of a Catalogue of Schemes (CoS) entry that a TS 11 SchemaMeta does not carry. A TS 11 catalog entry and a CoS scheme
 * version describe the same attestation type, so these fields are authored on the TS 11 record and kept next to it, ready for a later
 * publish that emits the CoS XML. Every field is optional while the record is a draft; the publish step enforces the CoS conformance rules.
 *
 * Level of surety, binding type and trusted authorities stay TS 11 only and are not part of this type.
 *
 * The scheme version is [SchemaMeta.version], the scheme document is [SchemaMeta.rulebookURI], and the vct or docType the CoS format
 * binding names is taken from the record's attestation type key.
 */
@Serializable
data class CosSchemeFields(
    /** EAASchemeName. */
    val schemeName: String? = null,
    /** EAASchemeIdentifier. */
    val schemeIdentifier: String? = null,
    val owner: SchemeOwner? = null,
    /** EAASchemeRegistrationIdentifier, issued by the Commission. */
    val registrationIdentifier: String? = null,
    /** VersionedEAASchemeStatusInformation of the version this record represents. */
    val versionStatus: VersionStatus? = null,
    val eaaType: CosEaaTypeFields? = null,
)

/** The CoS EAAType of the record's scheme version. */
@Serializable
data class CosEaaTypeFields(
    /** EAATypeIdentifier. */
    val identifier: String? = null,
    val name: InternationalNames? = null,
    /** EAATypeSchemeDefinition. */
    val schemeDefinition: InternationalNames? = null,
    /** References into a Catalogue of Attributes (EU or authored). */
    val attributeReferences: List<AttributeReference> = emptyList(),
    /** EAATypeDataModelReference. */
    val dataModelReference: String? = null,
    /** EAATypeTrustModelTypes: QEAA, PubEAA and NonQEAANonPubEAA URIs. */
    val trustModelTypes: List<String> = emptyList(),
)
