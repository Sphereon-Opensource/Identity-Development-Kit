@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.persistence

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.credential.definition.CredentialDefinition
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persistence seam for [CredentialDefinition] entities. Tenant-scoped: every method takes `tenantId`
 * explicitly so a single store serves every tenant and one tenant's reads/writes cannot reach
 * another tenant's definitions.
 *
 * The IDK default is an in-memory implementation; durable overlays may replace the binding in higher
 * layers.
 */
interface CredentialDefinitionStore {
    /** Insert or replace the definition for its `(tenantId, id)` key, returning the stored value. */
    suspend fun save(definition: CredentialDefinition): IdkResult<CredentialDefinition, IdkError>

    /** The definition for the given id within the tenant, or null when none exists. */
    suspend fun get(
        tenantId: String,
        id: Uuid
    ): IdkResult<CredentialDefinition?, IdkError>

    /** All definitions in the tenant. */
    suspend fun list(tenantId: String): IdkResult<List<CredentialDefinition>, IdkError>

    /** Remove a definition. Returns false when no definition existed for the key. */
    suspend fun delete(
        tenantId: String,
        id: Uuid
    ): IdkResult<Boolean, IdkError>
}
