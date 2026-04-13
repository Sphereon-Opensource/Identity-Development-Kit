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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsPropertySource", exact = true)
interface MultiplatformSettingsPropertySource : PropertySource<MultiplatformSettings> {
    fun <T : Any> setProperty(key: String, targetType: KClass<T>, value: T?)
}

inline fun <reified T : Any> MultiplatformSettingsPropertySource.setProperty(key: String, value: T) = setProperty<T>(key, T::class, value)

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsAppPropertySource", exact = true)
interface MultiplatformSettingsAppPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(AppScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val appConfigSettings: MultiplatformSettingsAppPropertySource
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsTenantPropertySource", exact = true)
interface MultiplatformSettingsTenantPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val tenantConfigSettings: MultiplatformSettingsTenantPropertySource
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MultiplatformSettingsPrincipalPropertySource", exact = true)
interface MultiplatformSettingsPrincipalPropertySource : MultiplatformSettingsPropertySource {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
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
    configEnvironment: AppConfigEnvironment
) : MultiplatformSettingsAppPropertySource, AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.APP, userContext = null) {
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
) : MultiplatformSettingsTenantPropertySource,
    AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.TENANT, userContext = userContextInstance.context) {
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
    userContextInstance: UserContextInstance
) : MultiplatformSettingsPrincipalPropertySource,
    AbstractMultiplatformSettingsPropertySourceImpl(app = app, configLevel = ConfigLevel.PRINCIPAL, userContext = userContextInstance.context) {
    init {
        configEnvironment.getPropertySources(includeParents = false).add(this)
    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractMultiplatformSettingsPropertySourceImpl", exact = true)
abstract class AbstractMultiplatformSettingsPropertySourceImpl(
    private val app: App,
    private val configLevel: ConfigLevel,
    private val userContext: UserContext? = null
) : AbstractPropertySource<MultiplatformSettings>(
    name = "config-settings-${configLevel.name}",
    source = MultiplatformSettings(app, configLevel, userContext),
    order = Order.LOW.orderValue
), MultiplatformSettingsPropertySource {

    override fun <T : Any> setProperty(key: String, targetType: KClass<T>, value: T?) {
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
    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
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

            else -> throw IllegalArgumentException("Type $targetType of key $name not supported")
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


    override fun getAllPropertyNames(): Set<String> {
        return getSource().getKeys()
    }

    private fun validateSource(source: MultiplatformSettings) {
        if (!source.isPlatformSupported) {
            throw NotImplementedError("multiplatform-settings is not supported on this platform")
        }
    }

    override val isPlatformSupported: Boolean = getSource().isPlatformSupported
}
