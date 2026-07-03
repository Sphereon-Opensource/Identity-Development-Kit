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

package com.sphereon.openid.oid4vci.issuer.impl.config

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
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.issuer.config.MutableOid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that the OID4VCI issuer config provider reads from a per-INSTANCE config namespace
 * selected at request time, falling back to the singular namespace when no instance is resolved.
 *
 * Contract under test (the runtime half of the per-issuer story — VDX writes the plural prefix,
 * this provider reads it):
 *  1. With the instance-id holder set to `acme`, [RegistryBackedOid4vciIssuerConfigProvider] reads
 *     issuer-level keys under `oid4vci.issuers.acme.*` and per-credential keys under
 *     `oid4vci.issuers.acme.credentials.[<id>].*`.
 *  2. With the holder empty (no resolver populated it), the SAME provider reads the singular
 *     `oid4vci.issuer.*` namespace — back-compat with the pure-IDK config-only deploy.
 *  3. The singular [ConfigDrivenOid4vciIssuerConfigProvider] is unconditionally pinned to
 *     `oid4vci.issuer.*` regardless of any holder.
 *
 * Uses a real in-memory [PrincipalConfigService] (no mocking framework), mirroring the OAuth2 AS
 * config-binder test pattern.
 */
class InstanceNamespaceOid4vciIssuerConfigProviderTest {
    @Test
    fun registryProviderReadsInstanceNamespaceWhenHolderSet() {
        val properties =
            mapOf<String, Any>(
                // Singular namespace (must NOT be read when an instance is selected)
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "Singular",
                // Per-instance namespace for "acme"
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.authorizationServers" to "https://as.acme.example.com",
                "oid4vci.issuers.acme.credentialConfigurationIds" to "AcmeDegree",
                "oid4vci.issuers.acme.credentials.[AcmeDegree].format" to "jwt_vc_json",
                "oid4vci.issuers.acme.credentials.[AcmeDegree].scope" to "acme-degree",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        assertEquals(listOf("https://as.acme.example.com"), provider.authorizationServers)
        // Per-credential subtree must also be instance-relative.
        val configs = provider.credentialConfigurations
        assertEquals(setOf("AcmeDegree"), configs.keys)
        assertEquals("acme-degree", configs["AcmeDegree"]?.scope)
    }

    @Test
    fun registryProviderReadsInstanceSignedMetadataSettings() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.signed-metadata.enabled" to "false",
                "oid4vci.issuer.signingKeyAlias" to "singular-signing",
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.signed-metadata.enabled" to "true",
                "oid4vci.issuers.acme.signingKeyAlias" to "acme-metadata-signing",
                "oid4vci.issuers.acme.signingKmsProviderId" to "software",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertNotNull(provider.signingKey, "signed metadata should be enabled from the active issuer instance")
    }

    @Test
    fun responseEncryptionDisabledModeSuppressesMetadataEvenWithAlgorithms() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.encryption.response.mode" to "disabled",
                "oid4vci.issuers.acme.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.acme.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.acme.encryption.response.encValuesSupported" to "A256GCM",
                "oid4vci.issuers.acme.encryption.response.zipValuesSupported" to "DEF",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertNull(provider.credentialResponseEncryption)
    }

    @Test
    fun responseEncryptionSupportedModePublishesMetadataAsNotRequired() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.encryption.response.mode" to "supported",
                "oid4vci.issuers.acme.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.acme.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.acme.encryption.response.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        val encryption = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(false, encryption.encryptionRequired)
        assertEquals(listOf("ECDH-ES"), encryption.algValuesSupported)
        assertEquals(listOf("A256GCM"), encryption.encValuesSupported)
    }

    @Test
    fun responseEncryptionLegacyRequiredBooleanStillPublishesRequiredMetadata() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.acme.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.acme.encryption.response.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        val encryption = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(true, encryption.encryptionRequired)
    }

    @Test
    fun requestEncryptionDisabledModeSuppressesMetadataEvenWithKeyAndAlgorithms() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.encryption.request.mode" to "disabled",
                "oid4vci.issuers.acme.encryption.request.encryptionRequired" to "true",
                "oid4vci.issuers.acme.encryption.request.decryptionKeyAlias" to "acme-request-decryption",
                "oid4vci.issuers.acme.encryption.request.encValuesSupported" to "A256GCM",
                "oid4vci.issuers.acme.encryption.request.zipValuesSupported" to "DEF",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertNull(provider.credentialRequestEncryption)
    }

    @Test
    fun requestEncryptionSupportedModePublishesMetadataAsNotRequired() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.encryption.request.mode" to "supported",
                "oid4vci.issuers.acme.encryption.request.encryptionRequired" to "true",
                "oid4vci.issuers.acme.encryption.request.decryptionKeyAlias" to "acme-request-decryption",
                "oid4vci.issuers.acme.encryption.request.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        val encryption = assertNotNull(provider.credentialRequestEncryption)
        assertEquals(false, encryption.encryptionRequired)
        assertEquals(listOf("A256GCM"), encryption.encValuesSupported)
    }

    @Test
    fun registryProviderFallsBackToSingularCredentialMechanicsWhenInstanceOmitsThem() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.authorizationServers" to "https://as.singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "EuPid,Mdl",
                "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
                "oid4vci.issuer.credentials.[EuPid].vct" to "https://singular.example.com/public/schema/vct/EuPid",
                "oid4vci.issuer.credentials.[EuPid].scope" to "eu-pid",
                "oid4vci.issuer.credentials.[EuPid].signingKeyMode" to "did:jwk",
                "oid4vci.issuer.credentials.[EuPid].statusListId" to "eupid-revocation",
                "oid4vci.issuer.credentials.[Mdl].format" to "mso_mdoc",
                "oid4vci.issuer.credentials.[Mdl].doctype" to "org.iso.18013.5.1.mDL",
                "oid4vci.issuer.credentials.[Mdl].signingKeyMode" to "did:jwk",
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.signingKeyAlias" to "issuer-signing-acme",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        assertEquals(listOf("https://as.singular.example.com"), provider.authorizationServers)

        val configs = provider.credentialConfigurations
        assertEquals(setOf("EuPid", "Mdl"), configs.keys)
        assertEquals("eu-pid", configs["EuPid"]?.scope)
        assertEquals("https://singular.example.com/public/schema/vct/EuPid", configs["EuPid"]?.vct)
        assertEquals("mso_mdoc", configs["Mdl"]?.format)

        val signingConfig = provider.credentialSigningConfigs["EuPid"]
        assertEquals("issuer-signing-acme", signingConfig?.signingKeyAlias)
        assertEquals(SigningKeyMode.Did("jwk"), signingConfig?.signingKeyMode)
    }

    @Test
    fun registryProviderFallsBackToSingularNamespaceWhenHolderEmpty() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.authorizationServers" to "https://as.singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "Singular",
                "oid4vci.issuer.credentials.[Singular].format" to "jwt_vc_json",
                "oid4vci.issuer.credentials.[Singular].scope" to "singular-scope",
                // Instance config that must be ignored when the holder is empty.
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)
        // Holder intentionally left empty (mirrors a request with no instance resolver).
        assertNull(holder.currentInstanceId())

        assertEquals("https://singular.example.com", provider.issuerIdentifier)
        assertEquals(listOf("https://as.singular.example.com"), provider.authorizationServers)
        val configs = provider.credentialConfigurations
        assertEquals(setOf("Singular"), configs.keys)
        assertEquals("singular-scope", configs["Singular"]?.scope)
    }

    @Test
    fun registryProviderSwitchesNamespaceWhenHolderChangesMidSession() {
        // The namespace supplier is evaluated per read (not cached at construction), so a holder
        // set after construction is honoured — the session-scoped lifecycle requires this.
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)

        assertEquals("https://singular.example.com", provider.issuerIdentifier)
        holder.setCurrentInstanceId("acme")
        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        holder.clearCurrentInstanceId()
        assertEquals("https://singular.example.com", provider.issuerIdentifier)
    }

    @Test
    fun singularProviderAlwaysReadsSingularNamespace() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
            )
        val configService = TestPrincipalConfigService(properties)
        val provider = ConfigDrivenOid4vciIssuerConfigProvider(TestSessionExecution(configService))

        assertEquals("https://singular.example.com", provider.issuerIdentifier)
    }

    @Test
    fun credentialSigningConfigFallsBackToIssuerSigningAlias() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.signingKeyAlias" to "issuer-signing-tenant-default",
                "oid4vci.issuer.credentialConfigurationIds" to "EuPid",
                "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
                "oid4vci.issuer.credentials.[EuPid].scope" to "eu-pid",
            )
        val provider = ConfigDrivenOid4vciIssuerConfigProvider(TestSessionExecution(TestPrincipalConfigService(properties)))

        assertEquals(
            "issuer-signing-tenant-default",
            provider.credentialSigningConfigs["EuPid"]?.signingKeyAlias,
        )
    }

    @Test
    fun registryProviderPublishesEncryptionMetadataFromInstanceDefaults() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.encryption.response.mode" to "supported",
                "oid4vci.issuers.acme.encryption.response.algValuesSupported" to "ECDH-ES,ECDH-ES+A128KW,ECDH-ES+A256KW",
                "oid4vci.issuers.acme.encryption.response.encValuesSupported" to "A256GCM,A128GCM",
                "oid4vci.issuers.acme.encryption.response.zipValuesSupported" to "DEF",
                "oid4vci.issuers.acme.encryption.request.mode" to "supported",
                "oid4vci.issuers.acme.encryption.request.decryptionKeyAlias" to "issuer-request-decryption-acme",
                "oid4vci.issuers.acme.encryption.request.decryptionKmsProviderId" to "software",
                "oid4vci.issuers.acme.encryption.request.encValuesSupported" to "A256GCM,A128GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        val response = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(listOf("ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A256KW"), response.algValuesSupported)
        assertEquals(listOf("A256GCM", "A128GCM"), response.encValuesSupported)
        assertEquals(listOf("DEF"), response.zipValuesSupported)
        assertEquals(false, response.encryptionRequired)

        val request = assertNotNull(provider.credentialRequestEncryption)
        assertEquals(listOf("A256GCM", "A128GCM"), request.encValuesSupported)
        assertEquals(false, request.encryptionRequired)
        val decryptor = assertNotNull(provider.credentialRequestDecryptionKey) as ManagedOptsKeyInfo
        assertEquals("issuer-request-decryption-acme", decryptor.identifier.alias)
        assertEquals("software", decryptor.identifier.providerId)
    }

    @Test
    fun registryProviderPublishesPreferredKeyStorageStatusPeriodFromInstanceDefaults() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.preferredKeyStorageStatusPeriodSeconds" to "120",
                "oid4vci.issuers.acme.preferredKeyStorageStatusPeriodSeconds" to "900",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("acme")

        assertEquals(900, provider.preferredKeyStorageStatusPeriodSeconds)
    }

    @Test
    fun instanceNamespaceConstantMatchesVdxWriterPrefix() {
        // VDX's CreateOid4vciIssuerCommandImpl writes "oid4vci.issuers.<partyId>.<key>"; the reader
        // must use the identical root so the persisted prefix is readable at runtime.
        assertEquals(
            "oid4vci.issuers",
            com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE,
        )
    }

    @Test
    fun vctTypeMetadataIsAlsoInstanceRelative() =
        runTest {
            val properties =
                mapOf<String, Any>(
                    "oid4vci.issuers.acme.credentialConfigurationIds" to "AcmePid",
                    "oid4vci.issuers.acme.credentials.[AcmePid].format" to "dc+sd-jwt",
                    "oid4vci.issuers.acme.credentials.[AcmePid].vct" to "https://acme.example.com/vct/Pid",
                    "oid4vci.issuers.acme.credentials.[AcmePid].display.[en-US].name" to "Acme PID",
                )
            val (provider, holder) = newRegistryProvider(properties)
            holder.setCurrentInstanceId("acme")

            val vcts = provider.listVcts()
            assertTrue("Pid" in vcts, "expected the instance-scoped VCT to be discovered, got $vcts")
            val resolved = provider.resolve("Pid")
            assertNotNull(resolved, "VCT type metadata should resolve from the instance namespace")
        }

    private fun newRegistryProvider(properties: Map<String, Any>,): Pair<RegistryBackedOid4vciIssuerConfigProvider, MutableOid4vciIssuerInstanceIdProvider> {
        val configService = TestPrincipalConfigService(properties)
        val execution = TestSessionExecution(configService)
        val holder = DefaultOid4vciIssuerInstanceIdProvider()
        val provider = RegistryBackedOid4vciIssuerConfigProvider(execution = execution, instanceIdProvider = holder)
        return provider to holder
    }
}

/**
 * Real in-memory [PrincipalConfigService] for these tests. Coerces string values to Int/Long/Boolean
 * on demand (matching real property sources) and implements prefix scans for the bracket-quoted
 * sub-property discovery the provider performs. Mirrors the OAuth2 AS config-binder test fake.
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

    override fun getConfigLocation(): Path = error("not used")

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
            else -> error("only PRINCIPAL config is exercised by the issuer config provider")
        }
}

internal object NoOpSessionLogService : SessionLogService {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val id: String = "test-issuer-config-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("logManager not used in this test")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("toAsync not used in this test")
}
