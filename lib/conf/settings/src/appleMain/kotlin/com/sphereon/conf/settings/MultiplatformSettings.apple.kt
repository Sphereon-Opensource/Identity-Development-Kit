package com.sphereon.conf.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass

@OptIn(ExperimentalSettingsImplementation::class)
actual class MultiplatformSettings actual constructor(app: App, configLevel: ConfigLevel, userContext: UserContext?) {

    private val namespace = generateNameSpace(app, configLevel,userContext)

    @PublishedApi
    internal var settings: Settings

    // TODO / NOTE: KeychainSettings.Factory() is also supported here, but there is no encryption support for other platforms
    init {
        val factory: Settings.Factory = NSUserDefaultsSettings.Factory()
        this.settings = factory.create(namespace)
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
    actual inline fun <reified T : Any> get(key: String, defaultValue: T?): T? {
        val normKey = propKeyNormalizer.normalize(key)
        return if (settings.hasKey(normKey)) {
            settings.get<T>(normKey)
        } else defaultValue
    }

    /**
     * Retrieves the value associated with the specified key as a String.
     *
     * @param key the name of the property to retrieve.
     * @return the value as a String if the key exists, or null if not found.
     */
    actual fun getAsString(key: String): String? {
        val normKey = propKeyNormalizer.normalize(key)
        val value = settings.getStringOrNull(normKey) ?: settings.getIntOrNull(normKey)
        ?: settings.getDoubleOrNull(normKey) ?: settings.getFloatOrNull(normKey)
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
    actual fun <T : Any> set(key: String, targetType: KClass<T>, value: T?) {
        val normKey = propKeyNormalizer.normalize(key)
        setTypedValue(normKey, value, settings::set, settings::remove)
    }

    /**
     * Removes the value stored at the specified key.
     *
     * @param key the name of the property to remove.
     */
    actual fun remove(key: String) {
        val normKey = propKeyNormalizer.normalize(key)
        settings.remove(normKey)
    }

    /**
     * Retrieves all property names as a set.
     *
     * @return a set containing all property names.
     */
    actual fun getKeys(): Set<String> {
        return settings.keys.map { propKeyNormalizer.normalize(it) }.toSet()
    }

    /**
     * Checks whether MultiplatformSettings is supported on the current platform
     */
    actual val isPlatformSupported: Boolean = true
}
