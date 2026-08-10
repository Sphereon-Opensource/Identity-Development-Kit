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
import com.sphereon.core.api.conf.RefreshablePropertySource
import com.sphereon.core.api.conf.ScopedPropertySource
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.core.api.conf.validateConfigurationValueForRead
import com.sphereon.core.api.conf.validateEnvironmentReferencesForWrite
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
import org.kotlincrypto.core.digest.Digest
import org.kotlincrypto.hash.sha2.SHA256
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
    override val configLevel: ConfigLevel,
    private val userContext: UserContext? = null,
) : AbstractPropertySource<MultiplatformSettings>(
        name = "settings.${configLevel.name.lowercase()}",
        source = MultiplatformSettings(app, configLevel, userContext),
        order = Order.LOW.orderValue,
    ),
    MultiplatformSettingsPropertySource,
    ScopedPropertySource<MultiplatformSettings>,
    RefreshablePropertySource {
    private val contentRevisionRef = kotlinx.atomicfu.atomic(0L)
    private val contentDescriptorRef =
        kotlinx.atomicfu.atomic<SettingsNamespaceContentDescriptor?>(null)

    override val contentRevision: Long
        get() = contentRevisionRef.value

    override fun refreshIfNeeded() {
        val source = getSource()
        validateSource(source)
        val descriptor =
            SettingsNamespaceContentDescriptor(
                mutationRevision = source.mutationRevision,
                entries =
                    source
                        .getKeys()
                        .sorted()
                        .map { key ->
                            val storedType = source.getStoredTypeTag(key)
                            val value = source.getPropertyValue(key, Any::class)
                            validateConfigurationValueForRead(value, configLevel)
                            SettingsEntryContentDescriptor(
                                key = key,
                                storedType = storedType,
                                sensitiveContentFingerprint =
                                    digestSettingsValue(
                                        storedType = storedType,
                                        value = value,
                                    ),
                            )
                        },
            )
        val previous = contentDescriptorRef.value
        if (previous != descriptor && contentDescriptorRef.compareAndSet(previous, descriptor)) {
            contentRevisionRef.incrementAndGet()
        }
    }

    override fun <T : Any> setProperty(
        key: String,
        targetType: KClass<T>,
        value: T?,
    ) {
        val source = getSource()
        validateSource(source)
        validateEnvironmentReferencesForWrite(value, configLevel)
        val keyNormalized = keyNormalizer.normalize(key)

        source.set(keyNormalized, targetType, value)
        refreshIfNeeded()
    }

    override fun hasProperty(name: String): Boolean {
        val source = getSource()
        validateSource(source)
        val keyNormalized = keyNormalizer.normalize(name)
        val present = source.getKeys().contains(keyNormalized)
        if (present) {
            validateCurrentValue(source, keyNormalized)
        }
        return present
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        name: String,
        targetType: KClass<T>,
    ): T? {
        val source = getSource()
        validateSource(source)

        val keyNormalized = keyNormalizer.normalize(name)
        validateCurrentValue(source, keyNormalized)
        return when (targetType) {
            Any::class -> source.getAny(keyNormalized)
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
        validateCurrentValue(source, keyNormalized)
        return source.getAsString(keyNormalized)
    }

    override fun removeProperty(name: String) {
        val source = getSource()
        validateSource(source)

        val keyNormalized = keyNormalizer.normalize(name)
        source.remove(keyNormalized)
        refreshIfNeeded()
    }

    override fun getAllPropertyNames(): Set<String> {
        val source = getSource()
        validateSource(source)
        val keys = source.getKeys()
        if (configLevel != ConfigLevel.APP) {
            keys.forEach { validateCurrentValue(source, it) }
        }
        return keys
    }

    private fun validateSource(source: MultiplatformSettings) {
        if (!source.isPlatformSupported) {
            throw UnsupportedOperationException("MultiplatformSettings is not supported on this platform")
        }
    }

    private fun validateCurrentValue(
        source: MultiplatformSettings,
        key: String,
    ) {
        validateConfigurationValueForRead(source.getAsString(key), configLevel)
    }

    override val isPlatformSupported: Boolean = getSource().isPlatformSupported
}

private fun MultiplatformSettings.getAny(key: String): Any? =
    when (getStoredTypeTag(key)) {
        "STRING" -> get<String>(key)
        "BOOLEAN" -> get<Boolean>(key)
        "INT" -> get<Int>(key)
        "LONG" -> get<Long>(key)
        "FLOAT" -> get<Float>(key)
        "DOUBLE" -> get<Double>(key)
        // Legacy entries predate persisted type tags. Treat their string rendering as
        // the only authoritative representation rather than guessing a narrower type.
        else -> getAsString(key)
    }

private fun MultiplatformSettings.getPropertyValue(
    key: String,
    targetType: KClass<*>,
): Any? =
    when (targetType) {
        Any::class -> getAny(key)
        String::class -> get<String>(key)
        Boolean::class -> get<Boolean>(key)
        Int::class -> get<Int>(key)
        Long::class -> get<Long>(key)
        Float::class -> get<Float>(key)
        Double::class -> get<Double>(key)
        else -> null
    }

/**
 * Exact namespace descriptor. The content fingerprint is verifier material for low-entropy
 * values, so it remains private/in-memory and is never persisted, serialized, logged, or audited.
 */
private data class SettingsNamespaceContentDescriptor(
    val mutationRevision: Long,
    val entries: List<SettingsEntryContentDescriptor>,
) {
    override fun toString(): String = "SettingsNamespaceContentDescriptor(<redacted>)"
}

private data class SettingsEntryContentDescriptor(
    val key: String,
    val storedType: String?,
    val sensitiveContentFingerprint: SensitiveSettingsContentFingerprint,
) {
    override fun toString(): String = "SettingsEntryContentDescriptor(key=$key, storedType=$storedType, fingerprint=<redacted>)"
}

/**
 * Deterministic verifier material. Equality supports freshness checks; string rendering is
 * deliberately redacted so diagnostics cannot expose it.
 */
internal class SensitiveSettingsContentFingerprint(
    private val value: String,
) {
    override fun equals(other: Any?): Boolean =
        other is SensitiveSettingsContentFingerprint && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "<sensitive-content-fingerprint:redacted>"
}

private fun digestSettingsValue(
    storedType: String?,
    value: Any?,
): SensitiveSettingsContentFingerprint {
    val digest = SHA256()
    digest.updateLengthPrefixed((storedType ?: "UNTAGGED").encodeToByteArray())
    digest.updateLengthPrefixed((value?.toString() ?: "NULL").encodeToByteArray())
    return SensitiveSettingsContentFingerprint(digest.digest().toLowerHex())
}

private fun Digest.updateLengthPrefixed(bytes: ByteArray) {
    val length = bytes.size
    update(
        byteArrayOf(
            (length ushr 24).toByte(),
            (length ushr 16).toByte(),
            (length ushr 8).toByte(),
            length.toByte(),
        ),
    )
    update(bytes)
}

private fun ByteArray.toLowerHex(): String =
    buildString(size * 2) {
        this@toLowerHex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

private const val HEX_DIGITS = "0123456789abcdef"
