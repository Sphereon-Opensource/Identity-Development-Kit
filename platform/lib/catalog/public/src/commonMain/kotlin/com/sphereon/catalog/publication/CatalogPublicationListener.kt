/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.publication

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * What a TS 11 catalog looks like at the moment it is published: the catalog, its listed schema records with their hosted
 * URIs, and the public URL under which the catalog is distributed (`{origin}/public/catalogs/{slug}`).
 */
class CatalogPublication(
    val catalog: AttestationCatalog,
    val records: List<AttestationSchemaRecord>,
    val publicUrl: String,
)

/**
 * Extension point for other representations of a published TS 11 catalog. The enterprise layer uses it to serve the same
 * catalog as signed CoS XML. A failure of [published] stops the publication, so a catalog is never PUBLISHED without its
 * additional representations.
 */
interface CatalogPublicationListener {
    suspend fun published(publication: CatalogPublication): IdkResult<Unit, IdkError>

    suspend fun disabled(catalog: AttestationCatalog): IdkResult<Unit, IdkError>
}
