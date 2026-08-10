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

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import java.util.prefs.Preferences
import kotlin.reflect.KClass

actual class MultiplatformSettings actual constructor(
    app: App,
    configLevel: ConfigLevel,
    userContext: UserContext?,
) {
    private val namespace = generateNameSpace(app, configLevel, userContext)

    @PublishedApi
    internal var settings: Settings

    init {
        val factory: Settings.Factory = PreferencesSettings.Factory(Preferences.userRoot())
        this.settings = factory.create(namespace)
    }

    /**
     * Checks whether MultiplatformSettings is supported on the current platform
     */
    actual val isPlatformSupported: Boolean = true
    actual val mutationRevision: Long
        get() = settings.getLongOrNull(SETTINGS_NAMESPACE_REVISION_KEY) ?: 0L

    actual internal fun getStoredTypeTag(key: String): String? {
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        return settings.getStringOrNull(settingsTypeStorageKey(normKey))
    }

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
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        return if (settings.hasKey(normKey)) {
            settings.get<T>(normKey)
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
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        val value =
            settings.getStringOrNull(normKey) ?: settings.getIntOrNull(normKey)
                ?: settings.getLongOrNull(normKey) ?: settings.getDoubleOrNull(normKey) ?: settings.getFloatOrNull(normKey)
                ?: settings.getBooleanOrNull(normKey) ?: return null
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
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        if (value == null) {
            settings.remove(normKey)
            settings.remove(settingsTypeStorageKey(normKey))
            advanceNamespaceRevision()
            return
        }

        when (value) {
            is Int -> settings.putInt(normKey, value)
            is Long -> settings.putLong(normKey, value)
            is String -> settings.putString(normKey, value)
            is Float -> settings.putFloat(normKey, value)
            is Double -> settings.putDouble(normKey, value)
            is Boolean -> settings.putBoolean(normKey, value)
            else -> throw UnsupportedOperationException("Unsupported settings type for key '$normKey': ${value::class}. Supported types: Int, Long, String, Float, Double, Boolean")
        }
        settings.putString(settingsTypeStorageKey(normKey), storedSettingsTypeTag(value))
        advanceNamespaceRevision()
    }

    /**
     * Removes the value stored at the specified key.
     *
     * @param key the name of the property to remove.
     */
    actual fun remove(key: String) {
        val normKey = propKeyNormalizer.normalize(key)
        requirePublicSettingsKey(normKey)
        settings.remove(normKey)
        settings.remove(settingsTypeStorageKey(normKey))
        advanceNamespaceRevision()
    }

    /**
     * Retrieves all property names as a set.
     *
     * @return a set containing all property names.
     */
    actual fun getKeys(): Set<String> =
        settings.keys
            .asSequence()
            .filterNot(::isInternalSettingsStorageKey)
            .map { propKeyNormalizer.normalize(it) }
            .toSet()

    private fun advanceNamespaceRevision() {
        settings.putLong(SETTINGS_NAMESPACE_REVISION_KEY, mutationRevision + 1L)
    }
}
