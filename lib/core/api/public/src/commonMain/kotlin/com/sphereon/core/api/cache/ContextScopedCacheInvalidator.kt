/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.api.cache

import com.sphereon.core.api.conf.ConfigSnapshotCache
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.context.ContextScopedResourceInvalidator
import com.sphereon.core.api.log.AppLogManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

interface ContextScopedCacheInvalidator : ContextScopedResourceInvalidator

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ContextScopedCacheInvalidator>())
@ContributesIntoSet(AppScope::class, binding = binding<ContextScopedResourceInvalidator>())
class DefaultContextScopedCacheInvalidator(
    private val cacheManager: CacheManager,
    private val configSnapshotCache: ConfigSnapshotCache,
    private val syncConfigSnapshotCache: SyncConfigSnapshotCache,
    appLogManager: AppLogManager,
) : ContextScopedCacheInvalidator {
    private val log = appLogManager.withTag("ContextScopedCacheInvalidator")

    override suspend fun invalidateTenantContext(
        tenantId: String,
        reason: String,
    ) {
        invalidate(
            scope = "TENANT",
            tenantId = tenantId,
            principalId = null,
            reason = reason,
        ) {
            runInvalidationPart("cache-manager") { cacheManager.invalidateTenant(tenantId) }
            runInvalidationPart("config-snapshot-cache") { configSnapshotCache.invalidateTenant(tenantId) }
            runInvalidationPart("sync-config-snapshot-cache") { syncConfigSnapshotCache.invalidateTenant(tenantId) }
        }
    }

    override suspend fun invalidatePrincipalContext(
        tenantId: String,
        principalId: String,
        reason: String,
    ) {
        invalidate(
            scope = "PRINCIPAL",
            tenantId = tenantId,
            principalId = principalId,
            reason = reason,
        ) {
            runInvalidationPart("cache-manager") { cacheManager.invalidatePrincipal(tenantId, principalId) }
            runInvalidationPart("config-snapshot-cache") { configSnapshotCache.invalidatePrincipal(tenantId, principalId) }
            runInvalidationPart("sync-config-snapshot-cache") { syncConfigSnapshotCache.invalidatePrincipal(tenantId, principalId) }
        }
    }

    private suspend fun invalidate(
        scope: String,
        tenantId: String,
        principalId: String?,
        reason: String,
        block: suspend InvalidationRun.() -> Unit,
    ) {
        val beforeConfigSnapshotSize = configSnapshotCache.getStats().size
        val beforeSyncConfigSnapshotSize = syncConfigSnapshotCache.getStats().size
        val beforeNamespaces = cacheManager.getNamespaces().size
        val run = InvalidationRun()

        run.block()

        val afterConfigSnapshotSize = configSnapshotCache.getStats().size
        val afterSyncConfigSnapshotSize = syncConfigSnapshotCache.getStats().size
        val principalPart = principalId?.let { " principal=${it.sanitizeLogToken()}" } ?: ""
        val common =
            "scope=$scope tenant=${tenantId.sanitizeLogToken()}$principalPart reason=${reason.sanitizeLogToken()} " +
                "cache.namespaces=$beforeNamespaces " +
                "configSnapshot.size.before=$beforeConfigSnapshotSize configSnapshot.size.after=$afterConfigSnapshotSize " +
                "syncConfigSnapshot.size.before=$beforeSyncConfigSnapshotSize syncConfigSnapshot.size.after=$afterSyncConfigSnapshotSize"

        if (run.failures.isEmpty()) {
            log.info("VDX_CONTEXT_CACHE_INVALIDATED $common")
        } else {
            log.warn("VDX_CONTEXT_CACHE_INVALIDATION_FAILED $common failures=${run.failures.joinToString("|")}")
        }
        log.debug(
            "VDX_CONTEXT_CACHE_RETAINED scope=$scope tenant=${tenantId.sanitizeLogToken()}$principalPart " +
                "reason=${reason.sanitizeLogToken()} cache.namespaces.retained=${cacheManager.getNamespaces().size} " +
                "configSnapshot.entries.evicted=${(beforeConfigSnapshotSize - afterConfigSnapshotSize).coerceAtLeast(0)} " +
                "configSnapshot.entries.retained=$afterConfigSnapshotSize " +
                "syncConfigSnapshot.entries.evicted=${(beforeSyncConfigSnapshotSize - afterSyncConfigSnapshotSize).coerceAtLeast(0)} " +
                "syncConfigSnapshot.entries.retained=$afterSyncConfigSnapshotSize",
        )
    }

    private class InvalidationRun {
        val failures = mutableListOf<String>()

        suspend fun runInvalidationPart(
            part: String,
            block: suspend () -> Unit,
        ) {
            runCatching { block() }
                .onFailure { error ->
                    failures += "${part.sanitizeLogToken()}:${(error.message ?: error::class.simpleName ?: "unknown").sanitizeLogToken()}"
                }
        }
    }
}

private fun String.sanitizeLogToken(): String =
    trim()
        .ifBlank { "<blank>" }
        .replace(Regex("[^A-Za-z0-9._:@-]"), "_")
        .take(160)
