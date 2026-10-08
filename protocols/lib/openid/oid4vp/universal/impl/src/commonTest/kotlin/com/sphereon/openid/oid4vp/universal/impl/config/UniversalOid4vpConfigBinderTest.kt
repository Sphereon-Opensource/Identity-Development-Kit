package com.sphereon.openid.oid4vp.universal.impl.config

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceIdProvider
import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class UniversalOid4vpConfigBinderTest {
    @Test
    fun resolvesPartyIdThroughProjectedConfigBindingAlias() {
        val partyId = "e58b34ca-30b2-4fd0-8e85-cbac4d49f359"
        val binder =
            UniversalOid4vpConfigBinder(
                execution =
                    MapSessionExecution(
                        MapPrincipalConfigService(
                            mapOf(
                                "_derived.software.config-bindings.by-party.$partyId.config-key-prefix" to "oid4vp.verifiers.test",
                                "oid4vp.verifiers.$partyId.universal.external-base-url" to "https://uuid.example.com",
                                "oid4vp.verifiers.test.universal.external-base-url" to "https://test.saas.localtest.me",
                                "oid4vp.verifiers.test.universal.response-uri" to "https://test.saas.localtest.me/oid4vp/auth/response",
                            ),
                        ),
                    ),
                instanceIdProvider = FixedInstanceIdProvider(partyId),
            )

        val config = binder.getConfig()

        assertEquals("https://test.saas.localtest.me", config.externalBaseUrl)
        assertEquals("https://test.saas.localtest.me/oid4vp/auth/response", config.responseUri)
    }
}

private class FixedInstanceIdProvider(
    private val instanceId: String?,
) : Oid4vpVerifierInstanceIdProvider {
    override fun currentInstanceId(): String? = instanceId
}

private class MapSessionExecution(
    principalConfigService: PrincipalConfigService,
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = unsupported()
    override val log
        get() = unsupported()
    override val conf: ContextConfig = MapContextConfig(principalConfigService)
    override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
}

private class MapContextConfig(
    override val principal: PrincipalConfigService,
) : ContextConfig {
    override val app: AppConfigService
        get() = unsupported()
    override val tenant: TenantConfigService
        get() = unsupported()

    override fun conf(level: ConfigLevel): ConfigService =
        when (level) {
            ConfigLevel.PRINCIPAL -> principal
            else -> unsupported()
        }
}

private class MapPrincipalConfigService(
    private val properties: Map<String, String>,
) : PrincipalConfigService {
    override val parent: TenantConfigService
        get() = unsupported()
    override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

    override fun addPropertySource(source: PropertySource<*>): ConfigService = this

    override fun removePropertySource(source: PropertySource<*>): ConfigService = this

    override fun containsProperty(key: String): Boolean = properties.containsKey(key)

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?
    ): String? = properties[key] ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?
    ): T? =
        when (targetType) {
            String::class -> (properties[key] ?: defaultValue) as T?
            else -> defaultValue
        }

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?
    ): T = getProperty(key, targetType, defaultValue) ?: unsupported()

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?
    ): String = getPropertyAsString(key, defaultValue) ?: unsupported()

    override fun getAllProperties(): Map<String, Any> = properties

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean
    ): Map<String, Any> = emptyMap()

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean
    ): Map<String, String> = emptyMap()

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "test"

    override fun getConfigLocation(): Path = Path(".")

    override fun getPropertySources(includeParents: Boolean): PropertySources = unsupported()

    @Deprecated("use bare domain prefixes", level = DeprecationLevel.WARNING)
    override fun getNamespace(): String = "test"
}

private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by this test")
