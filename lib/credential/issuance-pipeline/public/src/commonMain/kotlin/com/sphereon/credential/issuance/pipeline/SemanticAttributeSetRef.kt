/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * A reference into the OCA semantic attribute model — the bound semantic attribute set (an OCA
 * bundle / vocabulary subset) a credential draws from.
 *
 * Phase 1 ships the reference type only. Resolving a ref against the OCA bundle service — to
 * derive selective-disclosure flags, mandatory-ness, data types and display — is the EDK
 * engine's job in a later phase. The credential-design service consumes this ref; the
 * [CredentialClaimsBinding] never restates SD-ness or data types.
 */
@JsExportCompat
@Serializable
data class SemanticAttributeSetRef(
    /** OCA bundle / vocabulary identifier. */
    val bundleId: String,
    /** Optional pinned bundle version; null = resolve the current version. */
    val version: String? = null,
)

/** A reference to a single attribute within a [SemanticAttributeSetRef]. */
@JsExportCompat
@Serializable
data class SemanticAttributeRef(
    val setRef: SemanticAttributeSetRef,
    /** The attribute's name within the semantic set. */
    val attributeName: String,
)
