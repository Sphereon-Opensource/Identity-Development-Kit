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
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import kotlin.reflect.KClass

/**
 * There is no support for Linux, Linux does not have a special mechanism besides plain env, use that for Linux
 */
actual class MultiplatformSettings actual constructor(
    app: App,
    configLevel: ConfigLevel,
    userContext: UserContext?,
) {
    actual val isPlatformSupported: Boolean = false
    actual val mutationRevision: Long = 0L

    actual internal fun getStoredTypeTag(key: String): String? =
        throw UnsupportedOperationException("MultiplatformSettings is not supported on Linux")

    actual inline fun <reified T : Any> get(
        key: String,
        defaultValue: T?,
    ): T? = throw UnsupportedOperationException("MultiplatformSettings is not supported on Linux")

    actual fun getKeys(): Set<String> = throw UnsupportedOperationException("MultiplatformSettings is not supported on Linux")

    actual fun <T : Any> set(
        key: String,
        targetType: KClass<T>,
        value: T?,
    ): Unit = throw UnsupportedOperationException("MultiplatformSettings is not supported on Linux")

    actual fun getAsString(key: String): String? = throw UnsupportedOperationException("MultiplatformSettings is not supported on Linux")

    actual fun remove(key: String) {
    }
}
