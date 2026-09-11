/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class CatalogTypeView
    @JvmOverloads
    constructor(
        val schema: SchemaMeta,
        val catalogId: String,
        val catalogSlug: String,
        val typeKey: AttestationTypeKey,
        val title: String,
        val description: String? = null,
        val claims: List<CatalogClaimView> = emptyList(),
        val card: CatalogCardFace? = null,
        val issuerBindings: List<CatalogIssuerBindingView> = emptyList(),
        val formatSummaries: List<CatalogFormatSummary> = emptyList(),
        val rulebookMediaType: String? = null,
        val listing: CatalogListingWindow = CatalogListingWindow.ALWAYS,
    )

@JsExportCompat
@Serializable
data class CatalogClaimView
    @JvmOverloads
    constructor(
        val path: String,
        val label: String,
        val mandatory: Boolean = false,
        val description: String? = null,
        val example: String? = null,
    )

@JsExportCompat
@Serializable
data class CatalogFormatSummary(
    val formatIdentifier: String,
    val mediaType: String,
)

@JsExportCompat
@Serializable
data class CatalogCardFace
    @JvmOverloads
    constructor(
        val displayName: String,
        val issuerName: String? = null,
        val description: String? = null,
        val backgroundColor: String? = null,
        val textColor: String? = null,
        val logoUrl: String? = null,
        val logoAlt: String? = null,
        val backgroundUrl: String? = null,
        val seed: String,
        val source: CatalogCardSource,
    )

@JsExportCompat
@Serializable
enum class CatalogCardSource {
    VCT_METADATA,
    LINKED_DESIGN,
    ISSUER_CONFIG,
}

@JsExportCompat
@Serializable
data class CatalogIssuerBindingView
    @JvmOverloads
    constructor(
        val instanceId: String,
        val instanceDisplayName: String,
        val credentialConfigurationId: String? = null,
        val designId: String? = null,
    )
