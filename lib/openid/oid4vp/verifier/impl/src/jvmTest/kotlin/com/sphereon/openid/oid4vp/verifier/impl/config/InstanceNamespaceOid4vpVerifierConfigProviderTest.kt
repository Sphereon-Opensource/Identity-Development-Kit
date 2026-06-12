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

package com.sphereon.openid.oid4vp.verifier.impl.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.impl.TestExecutionContext
import dev.zacsweers.metro.ContributesTo
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that the OID4VP verifier request-object signing config provider reads from a per-INSTANCE
 * config namespace selected at request time, falling back to the singular namespace when no instance
 * is resolved.
 *
 * Contract under test (the runtime half of the per-verifier story — VDX writes the plural prefix
 * `oid4vp.verifiers.<id>.*`, this provider reads it):
 *  1. With the instance-id holder set to `acme`, [RegistryBackedOid4vpVerifierConfigProvider] reads
 *     request-object signing keys under `oid4vp.verifiers.acme.request-object.signing.*`.
 *  2. With the holder empty (no resolver populated it), the SAME provider reads the singular
 *     `oid4vp.verifier.request-object.signing.*` namespace — back-compat with the pure-IDK
 *     config-only deploy.
 *  3. The namespace supplier is evaluated per read, so a holder set/changed mid-session is honoured.
 *  4. The singular [ConfigDrivenRequestObjectSigningConfig] is unconditionally pinned to the singular
 *     namespace regardless of any holder.
 *
 * The crypto/DID collaborators are real instances pulled from the verifier test session graph; the
 * config-reading getters under test never invoke them. Config itself is supplied via a real
 * in-memory [PrincipalConfigService] (no mocking framework), mirroring the OID4VCI issuer
 * instance-namespace test.
 */
class InstanceNamespaceOid4vpVerifierConfigProviderTest {
    @Test
    fun registryProviderReadsInstanceNamespaceWhenHolderSet() {
        val properties =
            mapOf<String, Any>(
                // Singular namespace (must NOT be read when an instance is selected)
                "oid4vp.verifier.request-object.signing.enabled" to "false",
                "oid4vp.verifier.request-object.signing.audience" to "https://singular.example.com",
                // Per-instance namespace for "acme"
                "oid4vp.verifiers.acme.request-object.signing.enabled" to "true",
                "oid4vp.verifiers.acme.request-object.signing.audience" to "https://acme.example.com",
                "oid4vp.verifiers.acme.request-object.signing.expiration.seconds" to "120",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertTrue(provider.enabled, "instance signing.enabled should be read from oid4vp.verifiers.acme.*")
        assertEquals("https://acme.example.com", provider.audience)
        assertEquals(120L, provider.expirationSeconds)
    }

    @Test
    fun registryProviderFallsBackToSingularNamespaceWhenHolderEmpty() {
        val properties =
            mapOf<String, Any>(
                "oid4vp.verifier.request-object.signing.enabled" to "true",
                "oid4vp.verifier.request-object.signing.audience" to "https://singular.example.com",
                "oid4vp.verifier.request-object.signing.expiration.seconds" to "300",
                // Instance config that must be ignored when the holder is empty.
                "oid4vp.verifiers.acme.request-object.signing.audience" to "https://acme.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)
        // Holder intentionally left empty (mirrors a request with no instance resolver).
        assertNull(holder.currentInstanceId())

        assertTrue(provider.enabled)
        assertEquals("https://singular.example.com", provider.audience)
        assertEquals(300L, provider.expirationSeconds)
    }

    @Test
    fun registryProviderSwitchesNamespaceWhenHolderChangesMidSession() {
        // The namespace supplier is evaluated per read (not cached at construction), so a holder
        // set after construction is honoured — the session-scoped lifecycle requires this.
        val properties =
            mapOf<String, Any>(
                "oid4vp.verifier.request-object.signing.audience" to "https://singular.example.com",
                "oid4vp.verifiers.acme.request-object.signing.audience" to "https://acme.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)

        assertEquals("https://singular.example.com", provider.audience)
        holder.setCurrentInstanceId("acme")
        assertEquals("https://acme.example.com", provider.audience)
        holder.clearCurrentInstanceId()
        assertEquals("https://singular.example.com", provider.audience)
    }

    @Test
    fun singularProviderAlwaysReadsSingularNamespace() {
        val properties =
            mapOf<String, Any>(
                "oid4vp.verifier.request-object.signing.audience" to "https://singular.example.com",
                "oid4vp.verifiers.acme.request-object.signing.audience" to "https://acme.example.com",
            )
        val collaborators = realCollaborators()
        val provider =
            ConfigDrivenRequestObjectSigningConfig(
                execution = TestSessionExecution(TestPrincipalConfigService(properties)),
                managedIdentifierService = collaborators.managedIdentifierService,
                kms = collaborators.kms,
                didProviderRegistry = collaborators.didProviderRegistry,
            )

        assertEquals("https://singular.example.com", provider.audience)
    }

    @Test
    fun blankInstanceIdFallsBackToSingularNamespace() {
        // A holder set to a blank id must not produce the malformed root `oid4vp.verifiers.` —
        // it falls back to the singular namespace exactly like an empty holder.
        val properties =
            mapOf<String, Any>(
                "oid4vp.verifier.request-object.signing.audience" to "https://singular.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("   ")

        assertEquals("https://singular.example.com", provider.audience)
    }

    @Test
    fun disabledWhenNeitherNamespaceConfiguresSigning() {
        val (provider, holder) = newRegistryProvider(emptyMap())
        holder.setCurrentInstanceId("acme")
        assertFalse(provider.enabled, "signing defaults to disabled when no config present")
        assertEquals("", provider.audience)
    }

    @Test
    fun instanceNamespaceConstantMatchesVdxWriterPrefix() {
        // VDX's CreateOid4vpVerifierCommandImpl writes "oid4vp.verifiers.<partyId>.<key>"; the reader
        // must use the identical root so the persisted prefix is readable at runtime.
        assertEquals(
            "oid4vp.verifiers",
            com.sphereon.openid.oid4vp.verifier.config.INSTANCES_NAMESPACE,
        )
    }

    private fun newRegistryProvider(properties: Map<String, Any>,): Pair<RegistryBackedOid4vpVerifierConfigProvider, MutableOid4vpVerifierInstanceIdProvider> {
        val collaborators = realCollaborators()
        val execution = TestSessionExecution(TestPrincipalConfigService(properties))
        val holder = DefaultOid4vpVerifierInstanceIdProvider()
        val provider =
            RegistryBackedOid4vpVerifierConfigProvider(
                execution = execution,
                managedIdentifierService = collaborators.managedIdentifierService,
                kms = collaborators.kms,
                didProviderRegistry = collaborators.didProviderRegistry,
                instanceIdProvider = holder,
            )
        return provider to holder
    }

    private data class Collaborators(
        val managedIdentifierService: ManagedIdentifierService,
        val kms: KeyManagerService,
        val didProviderRegistry: DidProviderRegistry,
    )

    /**
     * Pull REAL session-scoped crypto/DID collaborators from the verifier test app graph. The
     * config-reading getters under test never invoke them, but using real instances avoids
     * hand-rolling fakes for the 7-interface [KeyManagerService] surface.
     */
    private fun realCollaborators(): Collaborators {
        val session = TestExecutionContext.createSession()
        val accessor = session.graph as VerifierCryptoCollaboratorsAccessor
        return Collaborators(
            managedIdentifierService = accessor.managedIdentifierService,
            kms = accessor.keyManagerService,
            didProviderRegistry = accessor.didProviderRegistry,
        )
    }
}

/**
 * Session-graph accessor exposing the real crypto/DID collaborators the verifier config provider
 * depends on. Contributed to the test app graph so [InstanceNamespaceOid4vpVerifierConfigProviderTest]
 * can hand real instances to the provider under test (its config-reading getters never invoke them).
 */
@ContributesTo(SessionScope::class)
interface VerifierCryptoCollaboratorsAccessor {
    val managedIdentifierService: ManagedIdentifierService
    val keyManagerService: KeyManagerService
    val didProviderRegistry: DidProviderRegistry
}

/**
 * Real in-memory [PrincipalConfigService] for these tests. Coerces string values to Long/Boolean on
 * demand (matching real property sources). Mirrors the OID4VCI issuer instance-namespace test fake.
 */
internal class TestPrincipalConfigService(
    private val properties: Map<String, Any>,
) : PrincipalConfigService {
    override val parent: TenantConfigService
        get() = error("parent not used in this test")

    override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

    override fun addPropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun removePropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "test-app"

    override fun getConfigLocation(): kotlinx.io.files.Path = error("not used")

    override fun getPropertySources(includeParents: Boolean): PropertySources = error("not used")

    @Suppress("DEPRECATION")
    override fun getNamespace(): String = ""

    override fun containsProperty(key: String): Boolean = properties.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        val raw = properties[key] ?: return defaultValue
        if (targetType.isInstance(raw)) {
            return raw as T
        }
        val coerced: Any? =
            when (targetType) {
                Int::class -> {
                    (raw as? String)?.trim()?.toIntOrNull()
                }

                Long::class -> {
                    (raw as? String)?.trim()?.toLongOrNull()
                }

                Boolean::class -> {
                    (raw as? String)?.trim()?.lowercase()?.let { s ->
                        when (s) {
                            "true", "1", "yes", "on" -> true
                            "false", "0", "no", "off" -> false
                            else -> null
                        }
                    }
                }

                String::class -> {
                    raw.toString()
                }

                else -> {
                    null
                }
            }
        return coerced as T?
    }

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = properties[key]?.toString() ?: defaultValue

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = getProperty(key, targetType, defaultValue) ?: error("Missing required property $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = getPropertyAsString(key, defaultValue) ?: error("Missing required property $key")

    override fun getAllProperties(): Map<String, Any> = properties.toMap()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties.mapValues { it.value.toString() }

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> {
        val matched = mutableMapOf<String, Any>()
        for (prefix in prefixes) {
            for ((key, value) in properties) {
                val matches = key.startsWith("$prefix.") || key == prefix
                if (!matches) continue
                val outKey = if (stripPrefix) key.removePrefix("$prefix.") else key
                matched[outKey] = value
            }
        }
        return matched
    }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = getSubProperties(prefixes, stripPrefix).mapValues { it.value.toString() }
}

/** Minimal [SessionExecution] surface: only [conf] is exercised by the provider under test. */
internal class TestSessionExecution(
    principal: PrincipalConfigService,
) : SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = error("sessionContextManager not used in this test")
    override val log: SessionLogService = NoOpSessionLogService
    override val conf: ContextConfig = TestContextConfig(principal)
}

internal class TestContextConfig(
    override val principal: PrincipalConfigService,
) : ContextConfig {
    override val app: AppConfigService get() = error("app config not used in this test")
    override val tenant: TenantConfigService get() = error("tenant config not used in this test")

    override fun conf(level: ConfigLevel): ConfigService =
        when (level) {
            ConfigLevel.PRINCIPAL -> principal
            else -> error("only PRINCIPAL config is exercised by the verifier config provider")
        }
}

internal object NoOpSessionLogService : SessionLogService {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val id: String = "test-verifier-config-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("logManager not used in this test")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("toAsync not used in this test")
}
