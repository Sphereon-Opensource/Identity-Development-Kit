/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustContext

/**
 * Resolves trust anchors for a given trust context.
 */
@JsExportCompat
interface TrustAnchorResolver {
    val anchorType: TrustAnchorType

    suspend fun resolve(context: TrustContext): IdkResult<List<TrustAnchor>, IdkError>

    suspend fun supports(context: TrustContext): Boolean

    suspend fun refresh(): IdkResult<Boolean, IdkError>
}
