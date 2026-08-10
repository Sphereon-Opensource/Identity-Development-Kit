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

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract for the issuer's metadata-signing and request-decryption keys once a deployment manages
 * key material centrally.
 *
 * 1. Neither key resolves when the server-side binding cannot be honoured; the issuer refuses rather
 *    than falling back to a configured alias or minting anything.
 * 2. An alias and a provider id planted in configuration are ignored while the seam is bound, for
 *    both keys.
 * 3. Absent, detached, cross-tenant, inactive, and unmapped bindings refuse identically.
 * 4. Credentials fall back to the server-resolved issuer key rather than to the planted alias, and a
 *    per-credential or credential-default alias planted in configuration is ignored outright.
 * 5. The published JWKS names only server-resolved keys; a configuration whose key does not resolve
 *    contributes nothing and never its own caller-visible id.
 */
class IssuerKeyNameSeamTest {
    @Test
    fun bothKeysRefuseWhenNoBindingResolves() =
        runTest {
            val provider = seamProvider(ShapedIssuerKeyNameResolver(IssuerBindingShape.ABSENT))

            assertNull(provider.metadataSigningKeyName(), "an unusable binding must not yield a signing key name")
            assertNull(provider.signingKey(), "signed_metadata must be refused, not signed under a configured alias")
            assertNull(provider.credentialRequestDecryptionKey(), "an encrypted credential request must be refused")
        }

    @Test
    fun plantedSigningAliasAndProviderAreIgnoredWhileTheSeamIsBound() =
        runTest {
            val provider = seamProvider(FixedIssuerKeyNameResolver())

            assertEquals(SERVER_SIGNING_KEY, provider.metadataSigningKeyName())
            assertNotEquals(PLANTED_SIGNING_ALIAS, provider.metadataSigningKeyName())

            val opts = provider.signingKey() as ManagedOptsKeyInfo
            assertEquals(SERVER_SIGNING_KEY, opts.identifier.alias)
            assertNull(opts.identifier.providerId, "no provider may be selected from configuration")
        }

    @Test
    fun plantedDecryptionAliasAndProviderAreIgnoredWhileTheSeamIsBound() =
        runTest {
            val provider = seamProvider(FixedIssuerKeyNameResolver())

            val opts = provider.credentialRequestDecryptionKey() as ManagedOptsKeyInfo
            assertEquals(SERVER_DECRYPTION_KEY, opts.identifier.alias)
            assertNotEquals(PLANTED_DECRYPTION_ALIAS, opts.identifier.alias)
            assertNull(opts.identifier.providerId, "no provider may be selected from configuration")
        }

    @Test
    fun everyUnusableBindingShapeRefusesIdentically() =
        runTest {
            val outcomes =
                IssuerBindingShape.entries.map { shape ->
                    val provider = seamProvider(ShapedIssuerKeyNameResolver(shape))
                    Triple(
                        provider.metadataSigningKeyName(),
                        provider.signingKey(),
                        provider.credentialRequestDecryptionKey(),
                    )
                }

            assertEquals(1, outcomes.toSet().size, "unusable bindings must refuse identically, got $outcomes")
            assertTrue(outcomes.all { (name, signing, decryption) -> name == null && signing == null && decryption == null })
        }

    @Test
    fun credentialsFallBackToTheServerResolvedIssuerKey() =
        runTest {
            val signingConfigs = seamProvider(FixedIssuerKeyNameResolver()).credentialSigningConfigs()

            assertEquals(SERVER_SIGNING_KEY, signingConfigs["EuPid"]?.signingKeyAlias)
            assertNotEquals(PLANTED_SIGNING_ALIAS, signingConfigs["EuPid"]?.signingKeyAlias)
        }

    @Test
    fun plantedPerCredentialAndDefaultAliasesAreIgnoredWhileTheSeamIsBound() =
        runTest {
            val signingConfigs = seamProvider(FixedIssuerKeyNameResolver()).credentialSigningConfigs()

            assertEquals(SERVER_SIGNING_KEY, signingConfigs["EuPid"]?.signingKeyAlias)
            assertNotEquals(PLANTED_CREDENTIAL_ALIAS, signingConfigs["EuPid"]?.signingKeyAlias)
            assertNotEquals(PLANTED_CREDENTIAL_DEFAULT_ALIAS, signingConfigs["EuPid"]?.signingKeyAlias)
        }

    @Test
    fun credentialsRefuseWhenTheSeamRefuses() =
        runTest {
            val signingConfigs = seamProvider(ShapedIssuerKeyNameResolver(IssuerBindingShape.ABSENT)).credentialSigningConfigs()

            assertNull(
                signingConfigs["EuPid"]?.signingKeyAlias,
                "a refused binding must not leave a per-credential alias standing in for the server key",
            )
        }

    @Test
    fun publishedJwksNamesOnlyServerResolvedKeys() =
        runTest {
            val names = seamProvider(FixedIssuerKeyNameResolver()).signingKeyNames()

            assertEquals(setOf(SERVER_SIGNING_KEY), names)
        }

    @Test
    fun publishedJwksNamesNothingWhenNoKeyResolves() =
        runTest {
            val outcomes =
                IssuerBindingShape.entries.map { shape ->
                    seamProvider(ShapedIssuerKeyNameResolver(shape)).signingKeyNames()
                }

            assertEquals(1, outcomes.toSet().size, "unusable bindings must refuse identically, got $outcomes")
            assertTrue(
                outcomes.all { it.isEmpty() },
                "a configuration with no resolvable key must contribute no JWKS entry, not its own id",
            )
            assertTrue(
                outcomes.none { names -> names.any { it == CREDENTIAL_CONFIG_ID || it == INSTANCE_ID } },
                "no published key name may be derived from a caller-visible identifier",
            )
        }

    private fun seamProvider(resolver: IssuerKeyNameResolver): RegistryBackedOid4vciIssuerConfigProvider {
        val holder = DefaultOid4vciIssuerInstanceIdProvider()
        holder.setCurrentInstanceId(INSTANCE_ID)
        return RegistryBackedOid4vciIssuerConfigProvider(
            execution = TenantScopedIssuerTestSessionExecution(TestPrincipalConfigService(PLANTED_PROPERTIES), TENANT_ID),
            instanceIdProvider = holder,
            keyNameResolver = { resolver },
        )
    }

    private companion object {
        const val TENANT_ID = "tenant-acme"
        const val INSTANCE_ID = "acme"
        const val CREDENTIAL_CONFIG_ID = "EuPid"
        const val PLANTED_SIGNING_ALIAS = "planted-signing-alias"
        const val PLANTED_DECRYPTION_ALIAS = "planted-decryption-alias"
        const val PLANTED_CREDENTIAL_ALIAS = "planted-credential-alias"
        const val PLANTED_CREDENTIAL_DEFAULT_ALIAS = "planted-credential-default-alias"
        const val SERVER_SIGNING_KEY = "issuer-signing-acme"
        const val SERVER_DECRYPTION_KEY = "issuer-request-decryption-acme"

        val PLANTED_PROPERTIES =
            mapOf<String, Any>(
                "oid4vci.issuers.acme.identifier" to "https://acme.example.com",
                "oid4vci.issuers.acme.signed-metadata.enabled" to "true",
                "oid4vci.issuers.acme.signingKeyAlias" to PLANTED_SIGNING_ALIAS,
                "oid4vci.issuers.acme.signingKmsProviderId" to "planted-provider",
                "oid4vci.issuers.acme.encryption.request.mode" to "supported",
                "oid4vci.issuers.acme.encryption.request.decryptionKeyAlias" to PLANTED_DECRYPTION_ALIAS,
                "oid4vci.issuers.acme.encryption.request.decryptionKmsProviderId" to "planted-provider",
                "oid4vci.issuers.acme.encryption.request.encValuesSupported" to "A256GCM",
                "oid4vci.issuers.acme.credentialConfigurationIds" to "EuPid",
                "oid4vci.issuers.acme.credentials.[EuPid].format" to "dc+sd-jwt",
                "oid4vci.issuers.acme.credentials.[EuPid].signingKeyAlias" to PLANTED_CREDENTIAL_ALIAS,
                "oid4vci.issuers.acme.credentialDefaults.signingKeyAlias" to PLANTED_CREDENTIAL_DEFAULT_ALIAS,
            )
    }
}

/** The reasons a server-side binding cannot be honoured. All of them must look the same downstream. */
private enum class IssuerBindingShape {
    ABSENT,
    DETACHED,
    CROSS_TENANT,
    INACTIVE,
    UNMAPPED,
}

/**
 * A deployment whose binding cannot be honoured. The seam offers exactly one way to express that,
 * which is what keeps the five shapes indistinguishable to the protocol.
 */
private class ShapedIssuerKeyNameResolver(
    private val shape: IssuerBindingShape,
) : IssuerKeyNameResolver {
    override suspend fun resolveMetadataSigningKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String? = refuse()

    override suspend fun resolveRequestDecryptionKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String? = refuse()

    private fun refuse(): String? =
        when (shape) {
            IssuerBindingShape.ABSENT,
            IssuerBindingShape.DETACHED,
            IssuerBindingShape.CROSS_TENANT,
            IssuerBindingShape.INACTIVE,
            IssuerBindingShape.UNMAPPED,
            -> null
        }
}

/** A deployment with usable bindings: both key names are server-derived from the fixed roles. */
private class FixedIssuerKeyNameResolver : IssuerKeyNameResolver {
    override suspend fun resolveMetadataSigningKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String = "issuer-signing-$issuerInstanceId"

    override suspend fun resolveRequestDecryptionKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String = "issuer-request-decryption-$issuerInstanceId"
}

/** [SessionExecution] carrying a tenant, which the key-name seam is keyed on. */
private class TenantScopedIssuerTestSessionExecution(
    principal: PrincipalConfigService,
    override val tenantId: String,
) : SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = error("sessionContextManager not used in this test")
    override val log: SessionLogService = NoOpSessionLogService
    override val conf: ContextConfig = TestContextConfig(principal)
}
