package com.sphereon.conf.settings

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass


expect class MultiplatformSettings(app: App, configLevel: ConfigLevel, userContext: UserContext?) {

    /**
     * Checks whether MultiplatformSettings is supported on the current platform
     */
    val isPlatformSupported: Boolean

    /**
     * Retrieves the value of a property corresponding to the specified name with type safety.
     *
     * @param key the name of the property to retrieve.
     * @return the value of the property converted to the specified type T if found,
     *         or null if the property does not exist.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    inline fun <reified T : Any> get(key: String, defaultValue: T? = null): T?

    /**
     * Stores a typed value at the specified property name, or removes the property if value is null.
     *
     * @param key the name of the property to set.
     * @param targetType the KClass of the type to store.
     * @param value the value to store, or null to remove the property.
     * @throws IllegalArgumentException if the specified type T is not supported.
     *         Supported types are: Int, Long, String, Float, Double, Boolean.
     */
    fun <T : Any> set(key: String, targetType: KClass<T>, value: T?)

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

inline fun <reified T : Any> MultiplatformSettings.set(key: String, value: T?) = set(key, T::class, value)

internal fun <T : Any> setTypedValue(key: String, value: T?, setter: (String, Any) -> Unit, remover: (String) -> Unit) {
    if (value == null) {
        remover(key)
        return
    }

    when (value) {
        is Int, is Long, is String, is Float, is Double, is Boolean -> setter(key, value)
        else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
    }
}

internal fun generateNameSpace(app: App, configLevel: ConfigLevel, context: UserContext?): String {

    return when (configLevel) {
        ConfigLevel.APP -> propKeyNormalizer.normalize("${app.appId}.${app.profile}")
        ConfigLevel.TENANT -> propKeyNormalizer.normalize("${app.appId}.${app.profile}.${context!!.tenant.tenantId.replace("<", "").replace(">", "")}")
        ConfigLevel.PRINCIPAL -> propKeyNormalizer.normalize(
            "${app.appId}.${app.profile}.${context!!.tenant.tenantId.replace("<", "").replace(">", "")}.${
                context.principal.toString().replace("<", "").replace(">", "")
            }"
        )
    }
}

val propKeyNormalizer = PropertyKeyNormalizerImpl()