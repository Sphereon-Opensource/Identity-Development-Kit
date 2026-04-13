/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.credential.claims.mapper.impl.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.store.QueryConfigurationStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [com.sphereon.credential.claims.mapper.api.store.QueryConfigurationStore].
 *
 * This implementation stores configurations in memory using coroutine mutex
 * for thread safety. It supports both regular ID lookups and query ID lookups.
 *
 * Suitable for development, testing, and single-instance deployments.
 * For production multi-instance deployments, use a database-backed implementation.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<QueryConfigurationStore>())
class InMemoryClaimMappingConfigurationStore : QueryConfigurationStore {
    private val mutex = Mutex()
    private val configurationsById = linkedMapOf<String, ClaimMappingConfiguration>()
    private val configurationsByQueryId = linkedMapOf<String, ClaimMappingConfiguration>()

    override suspend fun save(config: ClaimMappingConfiguration): IdkResult<ClaimMappingConfiguration, IdkError> =
        mutex.withLock {
            // Remove old mapping by query ID if config already exists with different query ID
            val existing = configurationsById[config.id]
            if (existing != null && existing.queryId != null && existing.queryId != config.queryId) {
                configurationsByQueryId.remove(existing.queryId)
            }

            configurationsById[config.id] = config
            // Only index by DCQL query ID if one is provided
            val queryId = config.queryId
            if (queryId != null) {
                configurationsByQueryId[queryId] = config
            }
            Ok(config).asResult()
        }

    override suspend fun findById(id: String): IdkResult<ClaimMappingConfiguration?, IdkError> =
        mutex.withLock {
            Ok(configurationsById[id]).asResult()
        }

    override suspend fun findByQueryId(queryId: String): IdkResult<ClaimMappingConfiguration?, IdkError> =
        mutex.withLock {
            Ok(configurationsByQueryId[queryId]).asResult()
        }

    override suspend fun findAll(): IdkResult<List<ClaimMappingConfiguration>, IdkError> =
        mutex.withLock {
            Ok(configurationsById.values.sortedBy { it.id }).asResult()
        }

    override suspend fun delete(id: String): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            val config = configurationsById.remove(id)
            if (config != null) {
                if (config.queryId != null) {
                    configurationsByQueryId.remove(config.queryId)
                }
                Ok(true).asResult()
            } else {
                Ok(false).asResult()
            }
        }

    override suspend fun exists(id: String): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            Ok(configurationsById.containsKey(id)).asResult()
        }

    override suspend fun deleteAll(): IdkResult<Int, IdkError> =
        mutex.withLock {
            val count = configurationsById.size
            configurationsById.clear()
            configurationsByQueryId.clear()
            Ok(count).asResult()
        }
}
