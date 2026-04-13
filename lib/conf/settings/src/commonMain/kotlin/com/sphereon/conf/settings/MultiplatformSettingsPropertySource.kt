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

import com.sphereon.core.api.conf.AbstractPropertySource
import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.di.Order
import com.sphereon.di.app.App
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsPropertySource", exact = true)
interface MultiplatformSettingsPropertySource : PropertySource<MultiplatformSettings> {
    fun <T : Any> setProperty(
        key: String,
        targetType: KClass<T>,
        value: T?,
    )
}

inline fun <reified T : Any> MultiplatformSettingsPropertySource.setProperty(
    key: String,
    value: T,
) = setProperty<T>(key, T::class, value)

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsAppPropertySource", exact = true)
interface MultiplatformSettingsAppPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(AppScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Graph", exact = true)
    interface Graph {
        val appConfigSettings: MultiplatformSettingsAppPropertySource
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsTenantPropertySource", exact = true)
interface MultiplatformSettingsTenantPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Graph", exact = true)
    interface Graph {
        val tenantConfigSettings: MultiplatformSettingsTenantPropertySource
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsPrincipalPropertySource", exact = true)
interface MultiplatformSettingsPrincipalPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Graph", exact = true)
    interface Graph {
        val principalConfigSettings: MultiplatformSettingsPrincipalPropertySource
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MultiplatformSettingsPropertySource>())
@ContributesBinding(AppScope::class, binding = binding<MultiplatformSettingsAppPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsAppPropertySourceImpl", exact = true)
class MultiplatformSettingsAppPropertySourceImpl(
    app: App,
    configEnvironment: AppConfigEnvironment,
) : AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.APP, userContext = null),
    MultiplatformSettingsAppPropertySource {
    init {
        configEnvironment.getPropertySources(includeParents = false).add(this)
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<MultiplatformSettingsPropertySource>())
@ContributesBinding(UserScope::class, binding = binding<MultiplatformSettingsTenantPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsTenantPropertySourceImpl", exact = true)
class MultiplatformSettingsTenantPropertySourceImpl(
    app: App,
    configEnvironment: TenantConfigEnvironment,
    userContextInstance: UserContextInstance,
) : AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.TENANT, userContext = userContextInstance.context),
    MultiplatformSettingsTenantPropertySource {
    init {
        configEnvironment.getPropertySources(includeParents = false).add(this)
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<MultiplatformSettingsPropertySource>())
@ContributesBinding(UserScope::class, binding = binding<MultiplatformSettingsPrincipalPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsPrincipalPropertySourceImpl", exact = true)
class MultiplatformSettingsPrincipalPropertySourceImpl(
    app: App,
    configEnvironment: PrincipalConfigEnvironment,
    userContextInstance: UserContextInstance,
) : AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.PRINCIPAL, userContext = userContextInstance.context),
    MultiplatformSettingsPrincipalPropertySource {
    init {
        configEnvironment.getPropertySources(includeParents = false).add(this)
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractMultiplatformSettingsPropertySourceImpl", exact = true)
abstract class AbstractMultiplatformSettingsPropertySourceImpl(
    private val app: App,
    private val configLevel: ConfigLevel,
    private val userContext: UserContext? = null,
) : AbstractPropertySource<MultiplatformSettings>(
        name = "settings.${configLevel.name.lowercase()}",
        source = MultiplatformSettings(app, configLevel, userContext),
        order = Order.LOW.orderValue,
    ),
    MultiplatformSettingsPropertySource {
    override fun <T : Any> setProperty(
        key: String,
        targetType: KClass<T>,
        value: T?,
    ) {
        val source = getSource()
        validateSource(source)
        val keyNormalized = keyNormalizer.normalize(key)

        return source.set(keyNormalized, targetType, value)
    }

    override fun hasProperty(name: String): Boolean {
        val source = getSource()
        validateSource(source)
        val keyNormalized = keyNormalizer.normalize(name)
        return source.getKeys().contains(keyNormalized)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        name: String,
        targetType: KClass<T>,
    ): T? {
        val source = getSource()
        validateSource(source)

        val keyNormalized = keyNormalizer.normalize(name)
        return when (targetType) {
            String::class -> source.get<String>(keyNormalized)
            Int::class -> source.get<Int>(keyNormalized)
            Long::class -> source.get<Long>(keyNormalized)
            Float::class -> source.get<Float>(keyNormalized)
            Double::class -> source.get<Double>(keyNormalized)
            Boolean::class -> source.get<Boolean>(keyNormalized)
            else -> throw UnsupportedOperationException("Type $targetType of key $name is not supported by MultiplatformSettings")
        } as T?
    }

    override fun getPropertyAsString(name: String): String? {
        val source = getSource()
        validateSource(source)

        val keyNormalized = keyNormalizer.normalize(name)
        return source.getAsString(keyNormalized)
    }

    override fun removeProperty(name: String) {
        val source = getSource()
        validateSource(source)

        val keyNormalized = keyNormalizer.normalize(name)
        source.remove(keyNormalized)
    }

    override fun getAllPropertyNames(): Set<String> = getSource().getKeys()

    private fun validateSource(source: MultiplatformSettings) {
        if (!source.isPlatformSupported) {
            throw UnsupportedOperationException("MultiplatformSettings is not supported on this platform")
        }
    }

    override val isPlatformSupported: Boolean = getSource().isPlatformSupported
}
