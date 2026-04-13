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

package com.sphereon.identity.reconciliation.impl.job

import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Periodic cleanup of expired reconciliation sessions.
 *
 * Runs on a fixed interval, finds sessions past their expiry time per tenant,
 * and marks them as [ReconciliationSessionStatus.EXPIRED].
 *
 * @param sessionStore The session store to query and update
 * @param tenantIdProvider Provides the list of active tenant IDs to clean up.
 *        The service layer supplies this (e.g., from tenant registry or config).
 *
 * Lifecycle is managed by the service layer (e.g., started during app initialization).
 */
class ReconciliationSessionCleanupJob(
    private val sessionStore: ReconciliationSessionStore,
    private val tenantIdProvider: suspend () -> List<String>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(intervalMinutes: Long = 5) {
        scope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000)
                try {
                    val tenantIds = tenantIdProvider()
                    val now = Clock.System.now()
                    for (tenantId in tenantIds) {
                        val expired = sessionStore.findExpired(tenantId, now)
                        for (session in expired) {
                            sessionStore.update(session.copy(status = ReconciliationSessionStatus.EXPIRED))
                        }
                    }
                } catch (_: Exception) {
                    // Ignored: session cleanup is best-effort
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
