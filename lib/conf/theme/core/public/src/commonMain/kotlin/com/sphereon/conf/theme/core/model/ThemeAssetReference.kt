/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Storage-neutral reference to an asset rendered by a theme.
 *
 * Persistence locators and blob-store descriptors belong to theme/asset service adapters and must
 * never cross into theme contracts or reusable UI graphs.
 */
@JsExportCompat
@Serializable
data class ThemeAssetReference
    @JvmOverloads
    constructor(
        val uri: String,
        val integrity: String? = null,
        val altText: String? = null,
        val contentType: String? = null,
    )
