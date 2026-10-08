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

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.startup.Initializer
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.toBlockingSettings
import com.russhwolf.settings.datastore.DataStoreSettings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.reflect.KClass

private var appContext: Context? = null
private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

/**
 * Initializer graph to pass a context to the no-arg `Settings()` call on Android. Pass a `Context` to  `create()`
 * if you're doing custom initialization logic or need to call `Settings()` from tests.
 *
 * TODO KIWA-38 This should come from the app graph for Android in the future.
 * TODO For Android the AppGraph and injected app should already have the context and coroutineScope, so we simply can access it here
 *
 */
class SettingsInitializer : Initializer<Context> {
    /**
     * Stores the `applicationContext` from the provided `context` so that it can be used by the no-arg `Settings()`
     * function.
     */
    override fun create(context: Context): Context = context.applicationContext.also { appContext = it }

    /**
     * Returns an empty list. This initializer depends only on the provided context.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
actual class MultiplatformSettings actual constructor(
    app: App,
    configLevel: ConfigLevel,
    userContext: UserContext?,
) {
    private val namespace = generateNameSpace(app, configLevel, userContext)

    @PublishedApi
    internal var settings: Settings

    init {
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = {
                    val fileName = "$namespace.preferences_pb"
                    appContext?.let { File(it.filesDir, fileName) }
                        ?: throw IllegalStateException("App context not initialized")
                },
                scope = scope,
            )
        this.settings = DataStoreSettings(dataStore).toBlockingSettings() // DataStoreSettings replaces SharedPreferences and will also work on Android native if we would enable it
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
        setTypedValue(normKey, value, settings::set, settings::remove)
        if (value == null) {
            settings.remove(settingsTypeStorageKey(normKey))
        } else {
            settings.putString(settingsTypeStorageKey(normKey), storedSettingsTypeTag(value))
        }
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
