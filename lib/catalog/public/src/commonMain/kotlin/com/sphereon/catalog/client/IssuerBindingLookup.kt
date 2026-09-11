/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.client

import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.CatalogCardFace
import com.sphereon.catalog.model.CatalogIssuerBindingView
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class IssuerBindingQuery
    @JvmOverloads
    constructor(
        val catalogId: String,
        val schemaId: String? = null,
        val typeKey: AttestationTypeKey,
        val linkedDesignId: String? = null,
    )

@JsExportCompat
@Serializable
data class IssuerBindingSnapshot
    @JvmOverloads
    constructor(
        val bindings: List<CatalogIssuerBindingView> = emptyList(),
        val card: CatalogCardFace? = null,
    )

/** Optional card and issuer-binding lookup so wallets without issuer services still get VCT cards. */
interface IssuerBindingLookup {
    suspend fun lookup(query: IssuerBindingQuery): IssuerBindingSnapshot
}
