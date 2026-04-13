/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.config

import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustConfigProviderTest {

    @Test
    fun defaultConfigWhenNoPropertiesSet() {
        val provider = createProvider(emptyMap())
        val config = provider.getTrustConfig()

        assertTrue(config.validation.enabled)
        assertTrue(config.validation.defaultCheckRevocation)
        assertFalse(config.anchors.x509.enabled)
        assertFalse(config.anchors.etsi.enabled)
        assertFalse(config.anchors.did.enabled)
        assertFalse(config.anchors.oidfed.enabled)
        assertTrue(config.revocation.enabled)
        assertEquals(60L, config.cache.trustListTtlMinutes)
    }

    @Test
    fun readsDidConfig() {
        val provider = createProvider(mapOf(
            "trust.anchors.did.enabled" to true,
            "trust.anchors.did.allowed-methods.0" to "web",
            "trust.anchors.did.allowed-methods.1" to "key",
            "trust.anchors.did.allowed-methods.2" to "jwk",
            "trust.anchors.did.trusted-dids.0" to "did:web:example.com",
            "trust.anchors.did.trusted-dids.1" to "did:key:z6MkTest"
        ))
        val config = provider.getTrustConfig()

        assertTrue(config.anchors.did.enabled)
        assertEquals(listOf("web", "key", "jwk"), config.anchors.did.allowedMethods)
        assertEquals(listOf("did:web:example.com", "did:key:z6MkTest"), config.anchors.did.trustedDids)
    }

    @Test
    fun readsX509Config() {
        val provider = createProvider(mapOf(
            "trust.anchors.x509.enabled" to true,
            "trust.anchors.x509.ca-bundle-paths.0" to "/etc/ssl/certs/ca-certificates.crt",
            "trust.anchors.x509.ca-bundle-urls.0" to "https://example.com/ca.pem",
            "trust.anchors.x509.trusted-fingerprints.0" to "sha256:AABB",
            "trust.anchors.x509.max-failed-sources" to 2
        ))
        val config = provider.getTrustConfig()

        assertTrue(config.anchors.x509.enabled)
        assertEquals(listOf("/etc/ssl/certs/ca-certificates.crt"), config.anchors.x509.caBundlePaths)
        assertEquals(listOf("https://example.com/ca.pem"), config.anchors.x509.caBundleUrls)
        assertEquals(listOf("sha256:AABB"), config.anchors.x509.trustedFingerprints)
        assertEquals(2, config.anchors.x509.maxFailedSources)
    }

    @Test
    fun readsEtsiConfig() {
        val provider = createProvider(mapOf(
            "trust.anchors.etsi.enabled" to true,
            "trust.anchors.etsi.lotl-url" to "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
            "trust.anchors.etsi.verify-signatures" to false,
            "trust.anchors.etsi.territories.0" to "NL",
            "trust.anchors.etsi.territories.1" to "DE"
        ))
        val config = provider.getTrustConfig()

        assertTrue(config.anchors.etsi.enabled)
        assertEquals("https://ec.europa.eu/tools/lotl/eu-lotl.xml", config.anchors.etsi.lotlUrl)
        assertFalse(config.anchors.etsi.verifySignatures)
        assertEquals(listOf("NL", "DE"), config.anchors.etsi.territories)
    }

    @Test
    fun readsOidfConfig() {
        val provider = createProvider(mapOf(
            "trust.anchors.oidfed.enabled" to true,
            "trust.anchors.oidfed.trust-anchors.0" to "https://trust-anchor.example.com",
            "trust.anchors.oidfed.max-chain-depth" to 3,
            "trust.anchors.oidfed.required-trust-marks.0" to "https://mark.example.com/m1"
        ))
        val config = provider.getTrustConfig()

        assertTrue(config.anchors.oidfed.enabled)
        assertEquals(listOf("https://trust-anchor.example.com"), config.anchors.oidfed.trustAnchors)
        assertEquals(3, config.anchors.oidfed.maxChainDepth)
        assertEquals(listOf("https://mark.example.com/m1"), config.anchors.oidfed.requiredTrustMarks)
    }

    @Test
    fun readsRevocationConfig() {
        val provider = createProvider(mapOf(
            "trust.revocation.enabled" to false,
            "trust.revocation.check-ocsp" to false,
            "trust.revocation.check-crl" to true,
            "trust.revocation.prefer-ocsp" to false,
            "trust.revocation.timeout-ms" to 5000L
        ))
        val config = provider.getTrustConfig()

        assertFalse(config.revocation.enabled)
        assertFalse(config.revocation.checkOcsp)
        assertTrue(config.revocation.checkCrl)
        assertFalse(config.revocation.preferOcsp)
        assertEquals(5000L, config.revocation.timeoutMs)
    }

    @Test
    fun readsCacheConfig() {
        val provider = createProvider(mapOf(
            "trust.cache.trust-list-ttl-minutes" to 120L,
            "trust.cache.revocation-ttl-minutes" to 30L,
            "trust.cache.oidfed-entity-ttl-minutes" to 45L
        ))
        val config = provider.getTrustConfig()

        assertEquals(120L, config.cache.trustListTtlMinutes)
        assertEquals(30L, config.cache.revocationTtlMinutes)
        assertEquals(45L, config.cache.oidfedEntityTtlMinutes)
    }

    @Test
    fun cachesConfigAcrossCalls() {
        val props = mutableMapOf<String, Any>("trust.anchors.did.enabled" to true)
        val provider = createProvider(props)

        val config1 = provider.getTrustConfig()
        assertTrue(config1.anchors.did.enabled)

        // Mutating the map shouldn't affect cached result
        props["trust.anchors.did.enabled"] = false
        val config2 = provider.getTrustConfig()
        assertTrue(config2.anchors.did.enabled) // still true from cache
    }

    @Test
    fun emptyListWhenNoIndexedProperties() {
        val provider = createProvider(mapOf(
            "trust.anchors.did.enabled" to true
        ))
        val config = provider.getTrustConfig()

        assertTrue(config.anchors.did.allowedMethods.isEmpty())
        assertTrue(config.anchors.did.trustedDids.isEmpty())
    }

    // -- Test infrastructure --

    private fun createProvider(properties: Map<String, Any>): DefaultTrustConfigProvider {
        val appConfig = MapBackedAppConfigService(properties)
        val execution = TestSessionExecution(
            createAnonymousSessionContext("config-test"),
            appConfig
        )
        return DefaultTrustConfigProvider(execution)
    }

    private class MapBackedAppConfigService(
        private val properties: Map<String, Any>
    ) : AppConfigService {
        override val level: ConfigLevel get() = ConfigLevel.APP
        override val parent: ConfigService? get() = null
        override val configLevel: ConfigLevel get() = ConfigLevel.APP

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T?): T? {
            val value = properties[key] ?: return defaultValue
            return try {
                when (targetType) {
                    Boolean::class -> (value as? Boolean ?: value.toString().toBooleanStrictOrNull()) as? T ?: defaultValue
                    Int::class -> (value as? Int ?: value.toString().toIntOrNull()) as? T ?: defaultValue
                    Long::class -> (value as? Long ?: value.toString().toLongOrNull()) as? T ?: defaultValue
                    String::class -> value.toString() as T
                    else -> defaultValue
                }
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun getPropertyAsString(key: String, defaultValue: String?): String? {
            return properties[key]?.toString() ?: defaultValue
        }

        override fun containsProperty(key: String): Boolean = key in properties
        override fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T?): T =
            getProperty(key, targetType, defaultValue) ?: error("Property $key not found")
        override fun getAllProperties(): Map<String, Any> = properties
        override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> = emptyMap()
        override fun getNamespace(): String = "test"
        override fun addPropertySource(source: PropertySource<*>): ConfigService = this
        override fun removePropertySource(source: PropertySource<*>): ConfigService = this
        override fun getActiveProfile(): String = "test"
        override fun getAppName(): String = "trust-test"
        override fun getConfigLocation(): kotlinx.io.files.Path = kotlinx.io.files.Path(".")
        override fun getPropertySources(includeParents: Boolean): com.sphereon.core.api.conf.PropertySources =
            com.sphereon.core.api.conf.DefaultPropertySources()
        override fun getRequiredPropertyAsString(key: String, defaultValue: String?): String =
            getPropertyAsString(key, defaultValue) ?: error("Property $key not found")
        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> =
            properties.mapValues { it.value.toString() }
        override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> =
            emptyMap()
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
        private val appConfig: AppConfigService
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager get() = throw NotImplementedError()
        override val log: SessionLogService = TestLogService(sessionContext)
        override val conf: ContextConfig = object : ContextConfig {
            override val app: AppConfigService get() = appConfig
            override val tenant: TenantConfigService get() = throw NotImplementedError()
            override val principal: PrincipalConfigService get() = throw NotImplementedError()
            override fun conf(level: ConfigLevel): ConfigService = if (level == ConfigLevel.APP) appConfig else throw NotImplementedError()
        }
    }

    private class TestLogService(
        override val sessionContext: SessionContext = NoOpSessionContext
    ) : SessionLogService {
        override val logManager: SessionLogManager = TestLogManager(sessionContext)
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-log"
        override val isEnabled: Boolean = false
        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this
        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)
        override fun toAsync(): AsyncLogService = TestAsyncLogService(sessionContext)
    }

    private class TestLogManager(private val ctx: SessionContext) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this
        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default
        override fun withTagAsync(tag: String, config: LoggerConfig?): AsyncLogService = TestAsyncLogService(ctx)
        override fun withTag(tag: String, config: LoggerConfig?): SessionLogService = TestLogService(ctx)
    }

    private class TestAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-async-log"
        override val isEnabled: Boolean = false
        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this
        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)
        override fun toSync(): SessionLogService = TestLogService(sessionContext)
    }
}
