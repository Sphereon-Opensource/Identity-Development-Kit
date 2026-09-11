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
 * selected at request time. The enterprise registry provider rejects a request without a resolved
 * issuer selector; it never falls back to the singular namespace.
 *
 * Contract under test (the runtime half of the per-issuer story — VDX writes the plural prefix,
 * this provider reads it):
 *  1. With the instance-id holder set to `acme`, [RegistryBackedOid4vciIssuerConfigProvider] reads
 *     issuer-level keys under `oid4vci.issuers.00000000-0000-4000-8000-000000000031.*` and per-credential keys under
 *     `oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[<id>].*`.
 *  2. With the holder empty (no resolver populated it), the registry provider fails closed.
 *  3. The singular [ConfigDrivenOid4vciIssuerConfigProvider] is unconditionally pinned to
 *     `oid4vci.issuer.*` regardless of any holder.
 *
 * Uses a real in-memory [PrincipalConfigService] (no mocking framework), mirroring the OAuth2 AS
 * config-binder test pattern.
 */
class InstanceNamespaceOid4vciIssuerConfigProviderTest {
    @Test
    fun registryProviderResolvesCanonicalPartyIdThroughDurableConfigBindingProjection() = runTest {
        val partyId = "00000000-0000-4000-8000-000000000031"
        val logicalInstanceId = "acme-issuer"
        val properties =
            mapOf<String, Any>(
                "_derived.software.config-bindings.by-party.$partyId.config-key-prefix" to
                    "oid4vci.issuers.$logicalInstanceId",
                "oid4vci.issuers.$logicalInstanceId.identifier" to "https://acme.example.com",
                "oid4vci.issuers.$logicalInstanceId.credentialConfigurationIds" to "AcmeDegree",
                "oid4vci.issuers.$logicalInstanceId.credentials.[AcmeDegree].format" to "dc+sd-jwt",
                "oid4vci.issuers.$logicalInstanceId.credentials.[AcmeDegree].scope" to "acme-degree",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId(partyId)
        provider.prepare()

        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        assertEquals(setOf("AcmeDegree"), provider.credentialConfigurations.keys)
        assertEquals("acme-degree", provider.credentialConfigurations["AcmeDegree"]?.scope)
    }

    @Test
    fun registryProviderReadsInstanceNamespaceWhenHolderSet() = runTest {
        val properties =
            mapOf<String, Any>(
                // Singular namespace (must NOT be read when an instance is selected)
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "Singular",
                // Per-instance namespace for "acme"
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentialConfigurationIds" to "AcmeDegree",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[AcmeDegree].format" to "jwt_vc_json",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[AcmeDegree].scope" to "acme-degree",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")
        provider.prepare()

        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        assertEquals(listOf("https://as.acme.example.com"), provider.authorizationServers)
        // Per-credential subtree must also be instance-relative.
        val configs = provider.credentialConfigurations
        assertEquals(setOf("AcmeDegree"), configs.keys)
        assertEquals("acme-degree", configs["AcmeDegree"]?.scope)
    }

    @Test
    fun registryProviderReadsInstanceSignedMetadataSettings() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.signed-metadata.enabled" to "false",
                "oid4vci.issuer.signingKeyAlias" to "singular-signing",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.signed-metadata.enabled" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.signingKeyAlias" to "acme-metadata-signing",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.signingKmsProviderId" to "software",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        assertNotNull(provider.signingKey(), "signed metadata should be enabled from the active issuer instance")
    }

    @Test
    fun responseEncryptionDisabledModeSuppressesMetadataEvenWithAlgorithms() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.mode" to "disabled",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encValuesSupported" to "A256GCM",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.zipValuesSupported" to "DEF",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        assertNull(provider.credentialResponseEncryption)
    }

    @Test
    fun responseEncryptionSupportedModePublishesMetadataAsNotRequired() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.mode" to "supported",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        val encryption = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(false, encryption.encryptionRequired)
        assertEquals(listOf("ECDH-ES"), encryption.algValuesSupported)
        assertEquals(listOf("A256GCM"), encryption.encValuesSupported)
    }

    @Test
    fun responseEncryptionLegacyRequiredBooleanStillPublishesRequiredMetadata() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encryptionRequired" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.algValuesSupported" to "ECDH-ES",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        val encryption = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(true, encryption.encryptionRequired)
    }

    @Test
    fun requestEncryptionDisabledModeSuppressesMetadataEvenWithKeyAndAlgorithms() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.mode" to "disabled",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.encryptionRequired" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.decryptionKeyAlias" to "acme-request-decryption",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.encValuesSupported" to "A256GCM",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.zipValuesSupported" to "DEF",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        assertNull(provider.credentialRequestEncryption)
    }

    @Test
    fun requestEncryptionSupportedModePublishesMetadataAsNotRequired() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.mode" to "supported",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.encryptionRequired" to "true",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.decryptionKeyAlias" to "acme-request-decryption",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.encValuesSupported" to "A256GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        val encryption = assertNotNull(provider.credentialRequestEncryption)
        assertEquals(false, encryption.encryptionRequired)
        assertEquals(listOf("A256GCM"), encryption.encValuesSupported)
    }

    @Test
    fun registryProviderDoesNotFallBackToSingularCredentialMechanicsWhenInstanceOmitsThem() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.authorizationServers" to "https://as.singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "EuPid,Mdl",
                "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
                "oid4vci.issuer.credentials.[EuPid].vct" to "https://singular.example.com/public/schema/vct/EuPid",
                "oid4vci.issuer.credentials.[EuPid].scope" to "eu-pid",
                "oid4vci.issuer.credentials.[EuPid].signingKeyMode" to "did:jwk",
                "oid4vci.issuer.credentials.[EuPid].validityPeriod" to "P365D",
                "oid4vci.issuer.credentials.[EuPid].status.statusListId" to "eupid-revocation",
                "oid4vci.issuer.credentials.[Mdl].format" to "mso_mdoc",
                "oid4vci.issuer.credentials.[Mdl].doctype" to "org.iso.18013.5.1.mDL",
                "oid4vci.issuer.credentials.[Mdl].signingKeyMode" to "did:jwk",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.signingKeyAlias" to "issuer-signing-acme",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")
        provider.prepare()

        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        assertEquals(listOf("https://as.acme.example.com"), provider.authorizationServers)
        assertEquals(emptySet(), provider.credentialConfigurations.keys)
        assertEquals(emptySet(), provider.credentialSigningConfigs().keys)
    }

    @Test
    fun registryProviderRejectsMissingUuidInstanceSelector() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuer.authorizationServers" to "https://as.singular.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "Singular",
                "oid4vci.issuer.credentials.[Singular].format" to "jwt_vc_json",
                "oid4vci.issuer.credentials.[Singular].scope" to "singular-scope",
                // Instance config that must be ignored when the holder is empty.
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)
        // Holder intentionally left empty (mirrors a request with no instance resolver).
        assertNull(holder.currentInstanceId())

        kotlin.test.assertFailsWith<IllegalArgumentException> { provider.prepare() }
    }

    @Test
    fun registryProviderRequiresSelectorAndSwitchesBetweenResolvedInstanceNamespaces() {
        // The namespace supplier is evaluated per read so each routed request reads only its
        // selected instance. An unresolved request must not regain the retired singular fallback.
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000032.identifier" to "https://globex.example.com",
            )
        val (provider, holder) = newRegistryProvider(properties)

        kotlin.test.assertFailsWith<IllegalArgumentException> { provider.issuerIdentifier }
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")
        assertEquals("https://acme.example.com", provider.issuerIdentifier)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000032")
        assertEquals("https://globex.example.com", provider.issuerIdentifier)
        holder.clearCurrentInstanceId()
        kotlin.test.assertFailsWith<IllegalArgumentException> { provider.issuerIdentifier }
    }

    @Test
    fun singularProviderAlwaysReadsSingularNamespace() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://singular.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
            )
        val configService = TestPrincipalConfigService(properties)
        val provider = ConfigDrivenOid4vciIssuerConfigProvider(TestSessionExecution(configService))

        assertEquals("https://singular.example.com", provider.issuerIdentifier)
    }

    @Test
    fun credentialSigningConfigFallsBackToIssuerSigningAlias() = runTest {
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
            provider.credentialSigningConfigs()["EuPid"]?.signingKeyAlias,
        )
    }

    @Test
    fun credentialSigningConfigReadsExplicitDataIntegrityCryptosuite() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.credentialConfigurationIds" to "DegreeLdpVc",
                "oid4vci.issuer.credentials.[DegreeLdpVc].format" to "ldp_vc",
                "oid4vci.issuer.credentials.[DegreeLdpVc].dataIntegrityCryptosuite" to "eddsa-jcs-2022",
            )
        val provider = ConfigDrivenOid4vciIssuerConfigProvider(TestSessionExecution(TestPrincipalConfigService(properties)))

        assertEquals(
            "eddsa-jcs-2022",
            provider.credentialSigningConfigs()["DegreeLdpVc"]?.dataIntegrityCryptosuite,
        )
    }

    @Test
    fun credentialSigningConfigReadsDesignBoundExpirationAndFlatStatusListAlias() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.identifier" to "https://acme.example.com",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.signingKeyAlias" to "issuer-signing-acme",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentialConfigurationIds" to "EuPid",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[EuPid].format" to "dc+sd-jwt",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[EuPid].vct" to "https://acme.example.com/public/schema/vct/EuPid",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[EuPid].expirationInDays" to "365",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[EuPid].statusListId" to "eupid-revocation",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        assertEquals(365, provider.credentialSigningConfigs()["EuPid"]?.expirationInDays)
        val binding = provider.statusListBindingFor("EuPid")
        assertTrue(binding.isErr, "flat statusListId must be treated as a configured status binding")
        assertTrue(
            binding.error.message.defaultMessage
                .contains("eupid-revocation"),
            "status binding error should include the configured list id",
        )
    }

    @Test
    fun registryProviderPublishesEncryptionMetadataFromInstanceDefaults() = runTest {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.mode" to "supported",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.algValuesSupported" to "ECDH-ES,ECDH-ES+A128KW,ECDH-ES+A256KW",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.encValuesSupported" to "A256GCM,A128GCM",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.response.zipValuesSupported" to "DEF",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.mode" to "supported",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.decryptionKeyAlias" to "issuer-request-decryption-acme",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.decryptionKmsProviderId" to "software",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.encryption.request.encValuesSupported" to "A256GCM,A128GCM",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

        val response = assertNotNull(provider.credentialResponseEncryption)
        assertEquals(listOf("ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A256KW"), response.algValuesSupported)
        assertEquals(listOf("A256GCM", "A128GCM"), response.encValuesSupported)
        assertEquals(listOf("DEF"), response.zipValuesSupported)
        assertEquals(false, response.encryptionRequired)

        val request = assertNotNull(provider.credentialRequestEncryption)
        assertEquals(listOf("A256GCM", "A128GCM"), request.encValuesSupported)
        assertEquals(false, request.encryptionRequired)
        val decryptor = assertNotNull(provider.credentialRequestDecryptionKey()) as ManagedOptsKeyInfo
        assertEquals("issuer-request-decryption-acme", decryptor.identifier.alias)
        assertEquals("software", decryptor.identifier.providerId)
    }

    @Test
    fun registryProviderPublishesPreferredKeyStorageStatusPeriodFromInstanceDefaults() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.preferredKeyStorageStatusPeriodSeconds" to "120",
                "oid4vci.issuers.00000000-0000-4000-8000-000000000031.preferredKeyStorageStatusPeriodSeconds" to "900",
            )
        val (provider, holder) = newRegistryProvider(properties)
        holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

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
                    "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentialConfigurationIds" to "AcmePid",
                    "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[AcmePid].format" to "dc+sd-jwt",
                    "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[AcmePid].vct" to "https://acme.example.com/vct/Pid",
                    "oid4vci.issuers.00000000-0000-4000-8000-000000000031.credentials.[AcmePid].display.[en-US].name" to "Acme PID",
                )
            val (provider, holder) = newRegistryProvider(properties)
            holder.setCurrentInstanceId("00000000-0000-4000-8000-000000000031")

            val vcts = provider.listVcts()
            assertTrue("Pid" in vcts, "expected the instance-scoped VCT to be discovered, got $vcts")
            val resolved = provider.resolve("Pid")
            assertNotNull(resolved, "VCT type metadata should resolve from the instance namespace")
        }

    private fun newRegistryProvider(properties: Map<String, Any>,): Pair<RegistryBackedOid4vciIssuerConfigProvider, MutableOid4vciIssuerInstanceIdProvider> {
        val configService = TestPrincipalConfigService(properties)
        val execution = TestSessionExecution(configService)
        val holder = DefaultOid4vciIssuerInstanceIdProvider()
        val provider = RegistryBackedOid4vciIssuerConfigProvider(
            execution = execution,
            instanceIdProvider = holder,
            authorizationPolicyProvider = TestOid4vciAuthorizationPolicyProvider("https://as.acme.example.com"),
        )
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
