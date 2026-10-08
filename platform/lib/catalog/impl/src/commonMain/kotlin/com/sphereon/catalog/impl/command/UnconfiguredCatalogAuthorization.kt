/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.command

import com.sphereon.catalog.authorization.CatalogAuthorization
import com.sphereon.catalog.authorization.DenyAllCatalogAuthorization
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Explicit default authorization binding; a host policy replaces this at composition time. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CatalogAuthorization>())
class UnconfiguredCatalogAuthorization : CatalogAuthorization {
    override suspend fun authorize(
        execution: SessionExecution,
        permission: String,
        resourceId: String?,
    ): IdkResult<Unit, IdkError> = DenyAllCatalogAuthorization.authorize(execution, permission, resourceId)
}
