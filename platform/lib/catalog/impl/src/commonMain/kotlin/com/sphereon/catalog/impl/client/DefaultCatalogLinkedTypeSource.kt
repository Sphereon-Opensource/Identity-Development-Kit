/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.LinkedTypeSnapshot
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultCatalogLinkedTypeSource : CatalogLinkedTypeSource {
    override suspend fun resolve(
        designId: String?,
        vctId: String?
    ): IdkResult<LinkedTypeSnapshot?, IdkError> {
        val key = vctId?.trim()?.takeIf { it.isNotEmpty() } ?: designId?.trim()?.takeIf { it.isNotEmpty() }
        if (key == null) return Ok(null)
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Linked type source cannot resolve '$key'; bind a VCT/design-backed CatalogLinkedTypeSource",
            ),
        )
    }
}
