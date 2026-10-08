/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.store.credential.design.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Reference to the semantic attribute set a credential design or credential-type binding draws
 * from. This is a design/catalog reference, not an issuance pipeline contract.
 */
@JsExportCompat
@Serializable
data class SemanticAttributeSetRef(
    val bundleId: String,
    val version: String? = null,
)

@JsExportCompat
@Serializable
data class SemanticAttributeRef(
    val setRef: SemanticAttributeSetRef,
    val attributeName: String,
)
