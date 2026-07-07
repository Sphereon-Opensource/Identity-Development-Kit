/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.software.registry.impl

import com.sphereon.core.api.conf.AbstractConfigService
import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.software.registry.model.SoftwareCapabilityType
import com.sphereon.software.registry.model.SoftwareLifecycleStatus
import com.sphereon.software.registry.model.SoftwareManagementMode
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Minimal real [ConfigEnvironment] backed by a [MapPropertySource], mirroring the
 * `TestConfigEnvironment` used inside lib-core-api-public's own tests. This exercises the REAL
 * production property-source / sub-property scan path (not a mock) so the registry's enumeration
 * idiom is verified end to end.
 */
private class MapConfigEnvironment(
    override val level: ConfigLevel = ConfigLevel.PRINCIPAL,
    override val parent: ConfigEnvironment? = null,
    private val propertySources: PropertySources,
) : ConfigEnvironment {
    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "test-app"

    override fun getConfigLocation(): Path = Path("/test/config")

    override fun getPropertySources(includeParents: Boolean): PropertySources = propertySources

    override fun getNamespace(): String = "test.namespace"

    override fun containsProperty(key: String): Boolean = propertySources.contains(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        for (source in propertySources) {
            val value = source.getProperty(key, targetType)
            if (value != null) return value
        }
        return defaultValue
    }

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T =
        getProperty(key, targetType, defaultValue)
            ?: throw IllegalStateException("Required property '$key' not found")

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = getProperty(key, String::class, defaultValue)

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = getRequiredProperty(key, String::class, defaultValue)

    override fun getAllProperties(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (source in propertySources) {
            for (name in source.getAllPropertyNames()) {
                source.getProperty(name, Any::class)?.let { result[name] = it }
            }
        }
        return result
    }

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = getAllProperties().mapValues { it.value.toString() }

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((key, value) in getAllProperties()) {
            for (prefix in prefixes) {
                if (key.startsWith("$prefix.")) {
                    val newKey = if (stripPrefix) key.removePrefix("$prefix.") else key
                    result[newKey] = value
                }
            }
        }
        return result
    }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = getSubProperties(prefixes, stripPrefix).mapValues { it.value.toString() }
}

/** Real [PrincipalConfigService] over [MapConfigEnvironment] — a thin AbstractConfigService subclass. */
private class MapPrincipalConfigService(
    environment: ConfigEnvironment,
) : AbstractConfigService(environment),
    PrincipalConfigService {
    override val level: ConfigLevel = ConfigLevel.PRINCIPAL

    // Not exercised by the registry; the parent chain is irrelevant to config-namespace derivation.
    @Deprecated("Test-only stub; parent chain is unused by the registry.")
    override val parent: TenantConfigService
        get() = throw UnsupportedOperationException("parent not used in this test")
}

private fun registryWith(props: Map<String, Any>): InMemorySoftwareInstanceRegistry {
    val sources = DefaultPropertySources().apply { add(MapPropertySource("test", props)) }
    val env = MapConfigEnvironment(propertySources = sources)
    return InMemorySoftwareInstanceRegistry(MapPrincipalConfigService(env))
}

class InMemorySoftwareInstanceRegistryTest {
    private val tenant = "tenant-a"

    // Keys are written in the already-normalized form the production property sources store them in
    // (dot-delimited, lowercase; camelCase `displayName` becomes `display.name`). MapPropertySource
    // keys its backing map verbatim, so pre-normalizing here mirrors real config loading.
    private val props =
        mapOf(
            // Two issuers; acme has an explicit displayName, beta has only nested config (id fallback).
            "oid4vci.issuers.acme.display.name" to "Acme",
            "oid4vci.issuers.beta.identifier" to "https://beta.example.com",
            "oid4vci.issuers.beta.credential.configuration.ids" to "UniversityDegree",
            // A verifier using the `name` fallback key.
            "oid4vp.verifiers.gamma.name" to "Gamma Verifier",
            // An authorization server (no display key -> id fallback).
            "oauth2.servers.default.issuer" to "https://as.example.com",
        )

    @Test
    fun listsIssuersDiscoveredFromConfigNamespace() =
        runTest {
            val registry = registryWith(props)

            val issuers = registry.list(tenant, SoftwareCapabilityType.OID4VCI_ISSUER)

            assertEquals(setOf("acme", "beta"), issuers.map { it.instanceId }.toSet())

            val acme = issuers.first { it.instanceId == "acme" }
            assertEquals(tenant, acme.tenantId)
            assertEquals(SoftwareCapabilityType.OID4VCI_ISSUER, acme.capabilityType)
            // displayName ('display.name' after normalization) is read from config.
            assertEquals("Acme", acme.displayName)
            assertEquals(SoftwareLifecycleStatus.ACTIVE, acme.lifecycleStatus)
            assertEquals(SoftwareManagementMode.MANAGED, acme.managementMode)
            assertNull(acme.runtimeMode)
            assertEquals("oid4vci.issuers.acme", acme.configKeyPrefix)
            assertTrue(acme.endpoints.isEmpty())

            val beta = issuers.first { it.instanceId == "beta" }
            // No displayName/name configured -> falls back to the id.
            assertEquals("beta", beta.displayName)
            assertEquals("oid4vci.issuers.beta", beta.configKeyPrefix)
        }

    @Test
    fun listsVerifierUsingNameFallbackKey() =
        runTest {
            val registry = registryWith(props)

            val verifiers = registry.list(tenant, SoftwareCapabilityType.OID4VP_VERIFIER)

            assertEquals(1, verifiers.size)
            val gamma = verifiers.single()
            assertEquals("gamma", gamma.instanceId)
            assertEquals(SoftwareCapabilityType.OID4VP_VERIFIER, gamma.capabilityType)
            assertEquals("Gamma Verifier", gamma.displayName)
            assertEquals("oid4vp.verifiers.gamma", gamma.configKeyPrefix)
        }

    @Test
    fun listsAuthorizationServer() =
        runTest {
            val registry = registryWith(props)

            val servers = registry.list(tenant, SoftwareCapabilityType.OAUTH2_AUTHORIZATION_SERVER)

            assertEquals(listOf("default"), servers.map { it.instanceId })
            assertEquals("default", servers.single().displayName)
            assertEquals(SoftwareCapabilityType.OAUTH2_AUTHORIZATION_SERVER, servers.single().capabilityType)
        }

    @Test
    fun listIsEmptyWhenNamespaceUnconfigured() =
        runTest {
            val registry = registryWith(emptyMap())

            assertTrue(registry.list(tenant, SoftwareCapabilityType.OID4VCI_ISSUER).isEmpty())
        }

    @Test
    fun getResolvesAnInstanceAcrossNamespaces() =
        runTest {
            val registry = registryWith(props)

            val beta = registry.get(tenant, "beta")
            assertEquals("beta", beta?.instanceId)
            assertEquals(SoftwareCapabilityType.OID4VCI_ISSUER, beta?.capabilityType)

            val gamma = registry.get(tenant, "gamma")
            assertEquals(SoftwareCapabilityType.OID4VP_VERIFIER, gamma?.capabilityType)

            val default = registry.get(tenant, "default")
            assertEquals(SoftwareCapabilityType.OAUTH2_AUTHORIZATION_SERVER, default?.capabilityType)
        }

    @Test
    fun getReturnsNullForUnknownInstance() =
        runTest {
            val registry = registryWith(props)

            assertNull(registry.get(tenant, "does-not-exist"))
        }

    @Test
    fun getReturnsMatchingConfigKeyPrefix() =
        runTest {
            val registry = registryWith(props)

            assertEquals("oid4vci.issuers.acme", registry.get(tenant, "acme")?.configKeyPrefix)
            assertEquals("oid4vp.verifiers.gamma", registry.get(tenant, "gamma")?.configKeyPrefix)
            assertEquals("oauth2.servers.default", registry.get(tenant, "default")?.configKeyPrefix)
            assertNull(registry.get(tenant, "does-not-exist"))
        }
}
