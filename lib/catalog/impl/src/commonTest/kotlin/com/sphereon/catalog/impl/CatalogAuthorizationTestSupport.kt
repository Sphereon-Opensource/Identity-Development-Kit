/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.authorization.CatalogAuthorization
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError

/** Grants every catalog permission, for tests that exercise catalog behavior rather than the permission boundary. */
internal object AllowAllCatalogAuthorization : CatalogAuthorization {
    override suspend fun authorize(
        execution: SessionExecution,
        permission: String,
        resourceId: String?,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}
