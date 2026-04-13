package com.sphereon.conf.theme.core.store

import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * In-memory implementation of [ThemeStore].
 * Useful for testing and as a default when no persistence is configured.
 * Data is scoped per session and not persisted across restarts.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeStore>())
class InMemoryThemeStore : ThemeStore {

    // Outer key: tenant, inner key: themeId
    private val store = mutableMapOf<String, MutableMap<String, ThemeDefinition>>()

    private fun tenantStore(tenant: String): MutableMap<String, ThemeDefinition> =
        store.getOrPut(tenant) { mutableMapOf() }

    override suspend fun getDefinition(tenant: String, themeId: String): ThemeDefinition? =
        store[tenant]?.get(themeId)

    override suspend fun listDefinitions(tenant: String, scope: ThemeScope?): List<ThemeDefinition> {
        val defs = store[tenant]?.values ?: return emptyList()
        return if (scope != null) defs.filter { it.scope == scope } else defs.toList()
    }

    override suspend fun saveDefinition(tenant: String, definition: ThemeDefinition): ThemeDefinition {
        tenantStore(tenant)[definition.id] = definition
        return definition
    }

    override suspend fun deleteDefinition(tenant: String, themeId: String): Boolean =
        store[tenant]?.remove(themeId) != null

    override suspend fun getDefinitionsByScope(
        tenant: String,
        scope: ThemeScope,
        variant: ThemeVariant?,
        appId: String?
    ): List<ThemeDefinition> {
        val defs = store[tenant]?.values ?: return emptyList()
        return defs
            .filter { it.scope == scope }
            .filter { variant == null || it.variant == variant }
            .filter { it.appId == appId }
    }

    override suspend fun countDefinitions(tenant: String): Int =
        store[tenant]?.size ?: 0
}
