/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError

/** Permission ids checked by the catalog write commands, on both the attestation and the attribute catalog APIs. */
object CatalogPermissionIds {
    /** Create, update, delete, link and import on catalogs, schemas, rulebooks and authored attribute catalogues, and validate. */
    const val MANAGE: String = "catalog.manage"

    /** Publish and disable TS 11 catalogs, and publish attribute catalogues. */
    const val PUBLISH: String = "catalog.publish"
}

/**
 * Permission boundary of the catalog write commands. A host binds an implementation backed by its policy engine;
 * without one the default refuses every write. Reads are never routed through it.
 */
fun interface CatalogAuthorization {
    /**
     * @param permission one of [CatalogPermissionIds]
     * @param resourceId the catalog or catalogue the call targets, when the call names one
     */
    suspend fun authorize(
        execution: SessionExecution,
        permission: String,
        resourceId: String?,
    ): IdkResult<Unit, IdkError>
}

/** Explicit fail-closed default for callers that have not supplied an authorization binding. */
object DenyAllCatalogAuthorization : CatalogAuthorization {
    override suspend fun authorize(
        execution: SessionExecution,
        permission: String,
        resourceId: String?,
    ): IdkResult<Unit, IdkError> = Err(IdkError.FORBIDDEN_ERROR(message = "Catalog authorization is not configured"))
}
