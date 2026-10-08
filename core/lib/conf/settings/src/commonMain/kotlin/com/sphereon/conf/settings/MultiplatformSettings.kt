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

package com.sphereon.conf.settings

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass

expect class MultiplatformSettings(
    app: App,
    configLevel: ConfigLevel,
    userContext: UserContext?,
) {
    /**
     * Checks whether MultiplatformSettings is supported on the current platform
     */
    val isPlatformSupported: Boolean

    /** Monotonic mutation revision for cache invalidation, including stored type changes. */
    val mutationRevision: Long

    /** Exact persisted runtime type descriptor for a user key, shared across namespace instances. */
    internal fun getStoredTypeTag(key: String): String?

    /**
     * Retrieves the value of a property corresponding to the specified name with type safety.
     *
     * @param key the name of the property to retrieve.
     * @return the value of the property converted to the specified type T if found,
     *         or null if the property does not exist.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    inline fun <reified T : Any> get(
        key: String,
        defaultValue: T? = null,
    ): T?

    /**
     * Stores a typed value at the specified property name, or removes the property if value is null.
     *
     * @param key the name of the property to set.
     * @param targetType the KClass of the type to store.
     * @param value the value to store, or null to remove the property.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    fun <T : Any> set(
        key: String,
        targetType: KClass<T>,
        value: T?,
    )

    /**
     * Removes the value stored at the specified key.
     *
     * @param key the name of the property to remove.
     */
    fun remove(key: String)

    /**
     * Retrieves the value associated with the specified key as a String.
     *
     * @param key the name of the property to retrieve.
     * @return the value as a String if the key exists, or null if not found.
     */
    fun getAsString(key: String): String?

    /**
     * Retrieves all environment variables as a map.
     *
     * @return a map containing all environment variables, with variable names as keys and their values as values.
     */
    fun getKeys(): Set<String>
}

inline fun <reified T : Any> MultiplatformSettings.set(
    key: String,
    value: T?,
) = set(key, T::class, value)

internal fun <T : Any> setTypedValue(
    key: String,
    value: T?,
    setter: (String, Any) -> Unit,
    remover: (String) -> Unit,
) {
    if (value == null) {
        remover(key)
        return
    }

    when (value) {
        is Int, is Long, is String, is Float, is Double, is Boolean -> setter(key, value)
        else -> throw UnsupportedOperationException("Unsupported settings type: ${value::class}. Supported types: Int, Long, String, Float, Double, Boolean")
    }
}

internal fun generateNameSpace(
    app: App,
    configLevel: ConfigLevel,
    context: UserContext?,
): String =
    when (configLevel) {
        ConfigLevel.APP -> {
            propKeyNormalizer.normalize("${app.appId}.${app.profile}")
        }

        ConfigLevel.TENANT -> {
            propKeyNormalizer.normalize("${app.appId}.${app.profile}.${context!!.tenant.tenantId.replace("<", "").replace(">", "")}")
        }

        ConfigLevel.PRINCIPAL -> {
            propKeyNormalizer.normalize(
                "${app.appId}.${app.profile}.${context!!.tenant.tenantId.replace("<", "").replace(">", "")}.${
                    context.principal.toString().replace("<", "").replace(">", "")
                }",
            )
        }
    }

/**
 * Stored in the same backing namespace as the user settings so independent
 * MultiplatformSettings instances observe type-only mutations as well as value changes.
 * It is deliberately excluded from the public key set.
 */
internal const val SETTINGS_NAMESPACE_REVISION_KEY = "__sphereon_internal_namespace_revision"
internal const val SETTINGS_TYPE_KEY_PREFIX = "__sphereon_internal_type."

@PublishedApi
internal fun requirePublicSettingsKey(key: String) {
    require(!isInternalSettingsStorageKey(key)) {
        "The requested settings key is reserved for internal cache-coherency metadata"
    }
}

internal fun isInternalSettingsStorageKey(key: String): Boolean {
    val normalized = propKeyNormalizer.normalize(key)
    return key == SETTINGS_NAMESPACE_REVISION_KEY ||
        key.startsWith(SETTINGS_TYPE_KEY_PREFIX) ||
        normalized == propKeyNormalizer.normalize(SETTINGS_NAMESPACE_REVISION_KEY) ||
        normalized.startsWith(propKeyNormalizer.normalize(SETTINGS_TYPE_KEY_PREFIX))
}

internal fun settingsTypeStorageKey(normalizedUserKey: String): String = "$SETTINGS_TYPE_KEY_PREFIX$normalizedUserKey"

/**
 * Projects a shared backing-store key set into one namespace's public keys.
 * Exact internal storage keys are removed before namespace stripping or normalization.
 */
internal fun projectNamespacedSettingsUserKeys(
    storageKeys: Set<String>,
    namespaceStoragePrefix: String,
    exactRevisionStorageKey: String,
    exactTypeStoragePrefix: String,
): Set<String> =
    storageKeys
        .asSequence()
        .filterNot { storageKey ->
            storageKey == exactRevisionStorageKey ||
                storageKey.startsWith(exactTypeStoragePrefix)
        }.filter { it.startsWith(namespaceStoragePrefix) }
        .map { it.removePrefix(namespaceStoragePrefix) }
        .filterNot(::isInternalSettingsStorageKey)
        .map(propKeyNormalizer::normalize)
        .toSet()

internal fun storedSettingsTypeTag(value: Any): String =
    when (value) {
        is String -> "STRING"
        is Boolean -> "BOOLEAN"
        is Int -> "INT"
        is Long -> "LONG"
        is Float -> "FLOAT"
        is Double -> "DOUBLE"
        else -> throw UnsupportedOperationException("Unsupported settings type: ${value::class}")
    }

val propKeyNormalizer = PropertyKeyNormalizerImpl.Default
