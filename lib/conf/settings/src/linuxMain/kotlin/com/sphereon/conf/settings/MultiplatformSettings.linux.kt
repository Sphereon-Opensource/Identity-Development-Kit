package com.sphereon.conf.settings

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass

/**
 * There is no support for Linux, Linux does not have a special mechanism besides plain env, use that for Linux
 */
actual class MultiplatformSettings actual constructor(app: App, configLevel: ConfigLevel, userContext: UserContext?) {

    actual val isPlatformSupported: Boolean = false

    actual inline fun <reified T : Any> get(key: String, defaultValue: T?): T? {
        throw NotImplementedError("MultiplatformSettings is not supported on Linux")
    }

    actual fun getKeys(): Set<String> {
        throw NotImplementedError("MultiplatformSettings is not supported on Linux")
    }

    actual fun <T : Any> set(key: String, targetType: KClass<T>, value: T?) {
        throw NotImplementedError("MultiplatformSettings is not supported on Linux")
    }

    actual fun getAsString(key: String): String? {
        throw NotImplementedError("MultiplatformSettings is not supported on Linux")
    }

    actual fun remove(key: String) {
    }
}