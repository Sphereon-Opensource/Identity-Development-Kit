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
 */

package com.sphereon.identity.idv.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.identity.idv.command.ResolveIdvUseCaseArgs
import com.sphereon.identity.idv.model.IdvError
import com.sphereon.identity.idv.model.IdvExecution
import com.sphereon.identity.idv.model.IdvExecutionId
import com.sphereon.identity.idv.model.IdvMethodDefinition
import com.sphereon.identity.idv.model.IdvMethodId
import com.sphereon.identity.idv.model.IdvUseCaseDefinition
import com.sphereon.identity.idv.model.IdvUseCaseId
import kotlin.time.Instant

@JsExportCompat
interface IdvMethodDefinitionStore {
    suspend fun findById(
        id: IdvMethodId,
        tenantId: String? = null,
    ): IdvMethodDefinition?

    suspend fun list(
        tenantId: String? = null,
        enabledOnly: Boolean = false,
    ): List<IdvMethodDefinition>

    suspend fun create(definition: IdvMethodDefinition): IdvMethodDefinition

    suspend fun update(definition: IdvMethodDefinition): IdvMethodDefinition

    suspend fun delete(
        id: IdvMethodId,
        tenantId: String? = null,
    ): Boolean
}

@JsExportCompat
interface IdvUseCaseDefinitionStore {
    suspend fun findById(
        id: IdvUseCaseId,
        tenantId: String,
    ): IdvUseCaseDefinition?

    suspend fun list(
        tenantId: String,
        enabledOnly: Boolean = false,
    ): List<IdvUseCaseDefinition>

    suspend fun create(definition: IdvUseCaseDefinition): IdvUseCaseDefinition

    suspend fun update(definition: IdvUseCaseDefinition): IdvUseCaseDefinition

    suspend fun delete(
        id: IdvUseCaseId,
        tenantId: String,
    ): Boolean

    suspend fun resolve(args: ResolveIdvUseCaseArgs): IdvUseCaseDefinition?
}

@JsExportCompat
interface IdvExecutionStore {
    suspend fun findById(
        executionId: IdvExecutionId,
        tenantId: String,
    ): IdvExecution?

    /**
     * Persists an execution together with the immutable use-case snapshot it was compiled from.
     * The snapshot is execution state, not a catalog entry: ad-hoc graphs must remain reloadable
     * without becoming tenant-visible definitions, and catalog edits must not alter an active run.
     */
    suspend fun create(
        execution: IdvExecution,
        useCaseSnapshot: IdvUseCaseDefinition? = null,
    ): IdvExecution

    suspend fun findUseCaseSnapshot(
        executionId: IdvExecutionId,
        tenantId: String,
    ): IdvUseCaseDefinition?

    suspend fun update(
        execution: IdvExecution,
        expectedVersion: Long? = null,
    ): IdkResult<IdvExecution, IdvError>

    suspend fun delete(
        executionId: IdvExecutionId,
        tenantId: String,
    ): Boolean

    suspend fun findExpired(
        tenantId: String,
        cutoff: Instant,
    ): List<IdvExecution>
}
