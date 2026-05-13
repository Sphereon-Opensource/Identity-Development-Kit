/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.mapping

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * A single source-to-target attribute rename rule.
 *
 * Used by any flow that needs to project an externally-supplied attribute map
 * (CSV row, IdP claims, form payload, …) onto a canonical or target-specific
 * attribute namespace before the value is consumed.
 *
 * @param source The source attribute name in the input map.
 * @param target The target attribute name to write the value under.
 * @param required If true, [applyAttributeMappings] fails when [source] is
 *   absent from the input map. Defaults to false (silent skip).
 */
@JsExportCompat
@Serializable
data class AttributeMapping
    @JvmOverloads
    constructor(
        val source: String,
        val target: String,
        val required: Boolean = false,
    )
