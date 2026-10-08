/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.publication

import com.sphereon.catalog.publication.CatalogSlugGuard
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** TS 11 catalog slugs only need to be unique among TS 11 catalogs unless a layer above contributes a guard. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class NoOpCatalogSlugGuard : CatalogSlugGuard {
    override suspend fun requireAvailable(
        tenantId: String,
        slug: String,
        catalogId: String,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}
