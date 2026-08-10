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

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass

actual class MultiplatformSettings actual constructor(
    app: App,
    configLevel: ConfigLevel,
    userContext: UserContext?,
) {
    @PublishedApi
    internal val namespace = generateNameSpace(app, configLevel, userContext)

    @PublishedApi
    internal var settings: Settings = StorageSettings()

    fun keyWithNS(key: String) = propKeyNormalizer.normalize("$namespace.$key")
    private val namespaceRevisionKey: String
        get() = keyWithNS(SETTINGS_NAMESPACE_REVISION_KEY)
    private val namespaceStoragePrefix: String
        get() = propKeyNormalizer.normalize("$namespace.").trimEnd('.') + "."
    private val namespacedTypeStoragePrefix: String
        get() = keyWithNS(SETTINGS_TYPE_KEY_PREFIX).trimEnd('.') + "."

    private fun namespacedTypeStorageKey(normalizedUserKey: String): String = keyWithNS(settingsTypeStorageKey(normalizedUserKey))

    /**
     * Retrieves the value of a property corresponding to the specified name with type safety.
     *
     * @param key the name of the property to retrieve.
     * @return the value of the property converted to the specified type T if found,
     *         or null if the property does not exist.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    actual inline fun <reified T : Any> get(
        key: String,
        defaultValue: T?,
    ): T? {
        requirePublicSettingsKey(propKeyNormalizer.normalize(key))
        val keyWithNS = keyWithNS(key)
        return if (settings.hasKey(keyWithNS)) {
            when (T::class) {
                Int::class -> settings.getIntOrNull(keyWithNS) as T?
                Long::class -> settings.getLongOrNull(keyWithNS) as T?
                String::class -> settings.getStringOrNull(keyWithNS) as T?
                Float::class -> settings.getFloatOrNull(keyWithNS) as T?
                Double::class -> settings.getDoubleOrNull(keyWithNS) as T?
                Boolean::class -> settings.getBooleanOrNull(keyWithNS) as T?
                else -> throw UnsupportedOperationException("Unsupported settings type: ${T::class} for key '$keyWithNS'. Supported types: Int, Long, String, Float, Double, Boolean")
            }
        } else {
            defaultValue
        }
    }

    /**
     * Retrieves the value associated with the specified key as a String.
     *
     * @param key the name of the property to retrieve.
     * @return the value as a String if the key exists, or null if not found.
     */
    actual fun getAsString(key: String): String? {
        requirePublicSettingsKey(propKeyNormalizer.normalize(key))
        val keyWithNS = keyWithNS(key)

        val value =
            settings.getStringOrNull(keyWithNS) ?: settings.getIntOrNull(keyWithNS)
                ?: settings.getLongOrNull(keyWithNS) ?: settings.getDoubleOrNull(keyWithNS) ?: settings.getFloatOrNull(keyWithNS)
                ?: settings.getBooleanOrNull(keyWithNS) ?: return null
        return "$value"
    }

    /**
     * Stores a typed value at the specified property name, or removes the property if value is null.
     *
     * @param key the name of the property to set.
     * @param value the value to store, or null to remove the property.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    actual fun <T : Any> set(
        key: String,
        targetType: KClass<T>,
        value: T?,
    ) {
        requirePublicSettingsKey(propKeyNormalizer.normalize(key))
        val keyWithNS = keyWithNS(key)
        if (value == null) {
            settings.remove(keyWithNS)
            settings.remove(namespacedTypeStorageKey(propKeyNormalizer.normalize(key)))
            advanceNamespaceRevision()
            return
        }

        when (value) {
            is Int -> settings.putInt(keyWithNS, value)
            is Long -> settings.putLong(keyWithNS, value)
            is String -> settings.putString(keyWithNS, value)
            is Float -> settings.putFloat(keyWithNS, value)
            is Double -> settings.putDouble(keyWithNS, value)
            is Boolean -> settings.putBoolean(keyWithNS, value)
            else -> throw UnsupportedOperationException("Unsupported settings type: ${value::class}. Supported types: Int, Long, String, Float, Double, Boolean")
        }
        settings.putString(
            namespacedTypeStorageKey(propKeyNormalizer.normalize(key)),
            storedSettingsTypeTag(value),
        )
        advanceNamespaceRevision()
    }

    /**
     * Removes the value stored at the specified key.
     *
     * @param key the name of the property to remove.
     */
    actual fun remove(key: String) {
        requirePublicSettingsKey(propKeyNormalizer.normalize(key))
        val keyWithNS = keyWithNS(key)
        settings.remove(keyWithNS)
        settings.remove(namespacedTypeStorageKey(propKeyNormalizer.normalize(key)))
        advanceNamespaceRevision()
    }

    /**
     * Retrieves all property names as a set.
     *
     * @return a set containing all property names.
     */
    actual fun getKeys(): Set<String> =
        projectNamespacedSettingsUserKeys(
            storageKeys = settings.keys,
            namespaceStoragePrefix = namespaceStoragePrefix,
            exactRevisionStorageKey = namespaceRevisionKey,
            exactTypeStoragePrefix = namespacedTypeStoragePrefix,
        )

    /**
     * Checks whether MultiplatformSettings is supported on the current platform
     */
    actual val isPlatformSupported: Boolean = true
    actual val mutationRevision: Long
        get() = settings.getLongOrNull(namespaceRevisionKey) ?: 0L

    actual internal fun getStoredTypeTag(key: String): String? {
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        return settings.getStringOrNull(namespacedTypeStorageKey(normKey))
    }

    private fun advanceNamespaceRevision() {
        settings.putLong(namespaceRevisionKey, mutationRevision + 1L)
    }
}
