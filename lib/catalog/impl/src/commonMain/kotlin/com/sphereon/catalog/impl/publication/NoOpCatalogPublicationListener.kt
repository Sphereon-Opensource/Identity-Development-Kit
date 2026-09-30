/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.publication

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.publication.CatalogPublication
import com.sphereon.catalog.publication.CatalogPublicationListener
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** A published TS 11 catalog has no other representation unless a layer above contributes a listener. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class NoOpCatalogPublicationListener : CatalogPublicationListener {
    override suspend fun published(publication: CatalogPublication): IdkResult<Unit, IdkError> = Ok(Unit)

    override suspend fun disabled(catalog: AttestationCatalog): IdkResult<Unit, IdkError> = Ok(Unit)
}
