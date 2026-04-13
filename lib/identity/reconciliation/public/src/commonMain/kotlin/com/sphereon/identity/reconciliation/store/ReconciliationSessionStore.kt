/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.store

import com.sphereon.identity.reconciliation.model.ReconciliationSession
import kotlinx.datetime.Instant

interface ReconciliationSessionStore {
    suspend fun findById(tenantId: String, sessionId: String): ReconciliationSession?
    suspend fun findByState(tenantId: String, state: String): ReconciliationSession?
    suspend fun create(session: ReconciliationSession): ReconciliationSession
    suspend fun update(session: ReconciliationSession): ReconciliationSession
    suspend fun delete(tenantId: String, sessionId: String): Boolean
    suspend fun findExpired(tenantId: String, cutoff: Instant): List<ReconciliationSession>
}
