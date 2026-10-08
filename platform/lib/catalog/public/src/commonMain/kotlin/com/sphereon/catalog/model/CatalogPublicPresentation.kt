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

/**
 * VDX public page metadata for a published catalog. Not a TS 11 SchemaMeta field.
 */
@JsExportCompat
@Serializable
data class CatalogPublicPresentation
    @JvmOverloads
    constructor(
        val slug: String,
        val displayName: String,
        val description: String? = null,
        val themeApplicationId: String,
    )
