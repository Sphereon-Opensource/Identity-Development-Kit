/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.publication

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Decides whether a TS 11 catalog may take [slug]. A catalog slug shares one namespace per tenant with every other public
 * catalogue slug (for example the slug of an authored CoA or CoS), so a layer that serves more than TS 11 catalogs contributes a
 * guard that refuses a slug another catalogue holds. [catalogId] is the catalog that wants the slug.
 */
fun interface CatalogSlugGuard {
    suspend fun requireAvailable(
        tenantId: String,
        slug: String,
        catalogId: String,
    ): IdkResult<Unit, IdkError>
}
