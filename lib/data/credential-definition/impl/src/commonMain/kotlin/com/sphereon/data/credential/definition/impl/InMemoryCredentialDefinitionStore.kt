@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.credential.definition.CredentialDefinition
import com.sphereon.data.credential.definition.persistence.CredentialDefinitionStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory tenant-scoped [CredentialDefinitionStore]. Definitions are keyed by `(tenantId, id)`;
 * reads and writes of one tenant cannot reach another tenant's definitions. Higher layers overlay a
 * durable store via Metro `replaces`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CredentialDefinitionStore>())
class InMemoryCredentialDefinitionStore : CredentialDefinitionStore {
    private val mutex = Mutex()
    private val store: MutableMap<String, MutableMap<Uuid, CredentialDefinition>> = mutableMapOf()

    override suspend fun save(definition: CredentialDefinition,): IdkResult<CredentialDefinition, IdkError> =
        mutex.withLock {
            val tenantTable = store.getOrPut(definition.tenantId) { mutableMapOf() }
            tenantTable[definition.id] = definition
            Ok(definition)
        }

    override suspend fun get(
        tenantId: String,
        id: Uuid,
    ): IdkResult<CredentialDefinition?, IdkError> =
        mutex.withLock {
            Ok(store[tenantId]?.get(id))
        }

    override suspend fun list(tenantId: String,): IdkResult<List<CredentialDefinition>, IdkError> =
        mutex.withLock {
            Ok(store[tenantId]?.values?.toList() ?: emptyList())
        }

    override suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            val tenantTable = store[tenantId] ?: return@withLock Ok(false)
            val removed = tenantTable.remove(id) != null
            Ok(removed)
        }
}
