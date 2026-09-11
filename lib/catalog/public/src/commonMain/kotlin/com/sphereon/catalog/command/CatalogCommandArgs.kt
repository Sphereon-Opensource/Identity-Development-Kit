/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.command

import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.CatalogListingWindow
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class CatalogIdArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
    )

@JsExportCompat
@Serializable
data class ListCatalogsArgs
    @JvmOverloads
    constructor(
        val status: AttestationCatalogStatus? = null,
        val verificationEnabled: Boolean? = null,
    )

@JsExportCompat
@Serializable
data class CreateCatalogArgs
    @JvmOverloads
    constructor(
        val slug: String,
        val displayName: String,
        val description: String? = null,
        val verificationEnabled: Boolean = false,
    )

@JsExportCompat
@Serializable
data class UpdateCatalogArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val displayName: String,
        val description: String? = null,
        val verificationEnabled: Boolean,
        val slug: String? = null,
    )

@JsExportCompat
@Serializable
data class ListSchemasArgs
    @JvmOverloads
    constructor(
        val catalogId: String? = null,
        val slug: String? = null,
        val id: String? = null,
        val supportedFormats: List<String> = emptyList(),
        val attestationLoS: String? = null,
        val bindingType: String? = null,
        val trustedAuthoritiesFrameworkType: String? = null,
        val trustedAuthoritiesValue: String? = null,
        val schemaUri: String? = null,
        val rulebookUri: String? = null,
        val publishedOnly: Boolean = false,
        val listedOnly: Boolean = false,
        val limit: Int = 20,
        val offset: Int = 0,
        val linkedDesignId: String? = null,
        val linkedVctId: String? = null,
    )

@JsExportCompat
@Serializable
data class SchemaIdArgs
    @JvmOverloads
    constructor(
        val catalogId: String? = null,
        val slug: String? = null,
        val schemaId: String,
        val publishedOnly: Boolean = false,
        val locale: String? = null,
    )

@JsExportCompat
@Serializable
data class CreateSchemaArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val schema: SchemaMeta,
        val documents: List<AttestationSchemaDocument> = emptyList(),
        val listing: CatalogListingWindow? = null,
    )

@JsExportCompat
@Serializable
data class UpdateSchemaArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val schemaId: String,
        val schema: SchemaMeta,
        val documents: List<AttestationSchemaDocument>? = null,
        val listing: CatalogListingWindow? = null,
    )

@JsExportCompat
@Serializable
data class LinkSchemaArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val designId: String? = null,
        val vctId: String? = null,
        val vct: String? = null,
        val doctype: String? = null,
        val version: String,
        val rulebookURI: String? = null,
        val attestationLoS: String,
        val bindingType: String,
        val supportedFormats: List<String>,
        val schemaURIs: List<SchemaUriRef> = emptyList(),
        val trustedAuthorities: List<TrustAuthority> = emptyList(),
        val documents: List<AttestationSchemaDocument> = emptyList(),
        val listing: CatalogListingWindow? = null,
    )

@JsExportCompat
@Serializable
data class SchemaFormatArgs
    @JvmOverloads
    constructor(
        val catalogId: String? = null,
        val slug: String? = null,
        val schemaId: String,
        val formatIdentifier: String,
        val publishedOnly: Boolean = false,
    )

@JsExportCompat
@Serializable
data class SchemaRulebookArgs
    @JvmOverloads
    constructor(
        val catalogId: String? = null,
        val slug: String? = null,
        val schemaId: String,
        val publishedOnly: Boolean = false,
    )

@JsExportCompat
@Serializable
data class ImportRemoteCatalogArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val baseUrl: String,
    )

@JsExportCompat
@Serializable
data class ImportRulebooksArgs
    @JvmOverloads
    constructor(
        val catalogId: String,
        val files: Map<String, String>,
        val defaultAttestationLoS: String? = null,
        val defaultBindingType: String? = null,
        val sourceUrl: String? = null,
    )

@JsExportCompat
@Serializable
data class ResolveAttestationTypeArgs
    @JvmOverloads
    constructor(
        val type: AttestationTypeKey,
        val catalogIds: List<String> = emptyList(),
        val includeDisabled: Boolean = false,
        val admin: Boolean = false,
    )

@JsExportCompat
@Serializable
data class EvaluateCatalogVerificationArgs
    @JvmOverloads
    constructor(
        val type: AttestationTypeKey,
        val mode: CatalogVerificationMode,
        val catalogIds: List<String> = emptyList(),
    )
