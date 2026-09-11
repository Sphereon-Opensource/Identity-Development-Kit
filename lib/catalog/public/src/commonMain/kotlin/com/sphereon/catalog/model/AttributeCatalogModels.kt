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
 * Thin TS 11 catalog-of-attributes datamodel. Not persisted or served in v1.
 */
@JsExportCompat
@Serializable
data class SchemaDistribution
    @JvmOverloads
    constructor(
        val accessURL: String,
        val mediaType: String,
    )

@JsExportCompat
@Serializable
data class DataService
    @JvmOverloads
    constructor(
        val country: String,
        val nationalSubID: String? = null,
        val endpointDescription: String,
        val endpointURL: String,
    )

@JsExportCompat
@Serializable
data class Attribute
    @JvmOverloads
    constructor(
        val name: List<String>,
        val identifier: String,
        val description: List<String>,
        val semanticDataSpecification: String? = null,
        val distributions: List<SchemaDistribution>,
        val nameSpace: String? = null,
        val contactInfo: List<String>,
        val legalBasis: List<String> = emptyList(),
        val authenticSources: List<DataService>,
    )
