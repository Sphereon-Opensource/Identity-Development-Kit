/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.core.store

import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [ThemeStore].
 * Useful for testing and as a default when no persistence is configured.
 * Data is scoped per session and not persisted across restarts.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeStore>())
class InMemoryThemeStore : ThemeStore {
    private val mutex = Mutex()

    // Outer key: tenant, inner key: themeId
    private val store = mutableMapOf<String, MutableMap<String, ThemeDefinition>>()

    private fun tenantStore(tenant: String): MutableMap<String, ThemeDefinition> = store.getOrPut(tenant) { mutableMapOf() }

    override suspend fun getDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition? = mutex.withLock { store[tenant]?.get(themeId) }

    override suspend fun listDefinitions(
        tenant: String,
        scope: ThemeScope?,
    ): List<ThemeDefinition> {
        return mutex.withLock {
            val defs = store[tenant]?.values ?: return@withLock emptyList()
            if (scope != null) defs.filter { it.scope == scope } else defs.toList()
        }
    }

    override suspend fun saveDefinition(
        tenant: String,
        definition: ThemeDefinition,
    ): ThemeDefinition {
        mutex.withLock {
            tenantStore(tenant)[definition.id] = definition
        }
        return definition
    }

    override suspend fun deleteDefinition(
        tenant: String,
        themeId: String,
    ): Boolean = mutex.withLock { store[tenant]?.remove(themeId) != null }

    override suspend fun getDefinitionsByScope(
        tenant: String,
        scope: ThemeScope,
        variant: ThemeVariant?,
        appId: String?,
    ): List<ThemeDefinition> {
        return mutex.withLock {
            val defs = store[tenant]?.values ?: return@withLock emptyList()
            defs
                .filter { it.scope == scope }
                .filter { variant == null || it.variant == variant }
                .filter { it.appId == appId }
        }
    }

    override suspend fun countDefinitions(tenant: String): Int = mutex.withLock { store[tenant]?.size ?: 0 }
}
