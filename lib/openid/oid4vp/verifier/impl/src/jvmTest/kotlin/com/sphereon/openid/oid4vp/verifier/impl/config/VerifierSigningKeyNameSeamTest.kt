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

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.openid.oid4vp.verifier.impl.createOid4vpRpJvmTestAppGraph
import kotlinx.coroutines.test.TestScope
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.impl.TestExecutionContext
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import com.sphereon.openid.oid4vp.verifier.spi.VerifierSigningKeyNameResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract for the verifier's request-object signing key once a deployment manages signing material
 * centrally.
 *
 * 1. A key that does not resolve refuses, and nothing is generated in the KMS as a side effect.
 * 2. A `keyAlias` / `providerId` planted in configuration is ignored while the seam is bound.
 * 3. Absent, detached, cross-tenant, inactive, and unmapped bindings refuse with the same message,
 *    so the refusal is not a discovery oracle.
 * 4. Create-time `client_id` substitution and fetch-time request-object signing resolve to the same
 *    key, which is what makes the published DID verifiable against the JAR signature.
 */
class VerifierSigningKeyNameSeamTest {
    @Test
    fun signingRefusesAndGeneratesNothingWhenNoKeyResolves() =
        runTest {
            val collaborators = realCollaborators()
            val provider = seamProvider(collaborators, ShapedSigningKeyNameResolver(BindingShape.ABSENT))

            val failure = assertFailsWith<IllegalStateException> { provider.resolveSigningKey() }
            assertEquals(AbstractConfigOid4vpVerifierConfigProvider.SIGNING_KEY_UNAVAILABLE, failure.message)
            assertFailsWith<IllegalStateException> { provider.resolveSignerBinding(ClientIdScheme.DECENTRALIZED_IDENTIFIER) }

            // The refusal must not have minted anything under the planted alias, nor under the
            // instance-derived name a generating implementation would have reached for.
            for (candidate in listOf(PLANTED_ALIAS, SERVER_KEY_NAME, INSTANCE_ID)) {
                val lookup = collaborators.kms.getKeyResult(KeyInfo<Nothing>(alias = candidate))
                val present = lookup.isOk && lookup.value.key != null
                assertFalse(present, "refusing to sign must never create key material (found '$candidate' in the KMS)")
            }
        }

    @Test
    fun configuredAliasAndProviderAreIgnoredWhileTheSeamIsBound() =
        runTest {
            val collaborators = realCollaborators()
            provisionKey(collaborators.kms, SERVER_KEY_NAME)
            provisionKey(collaborators.kms, PLANTED_ALIAS)
            val provider = seamProvider(collaborators, FixedSigningKeyNameResolver(SERVER_KEY_NAME))

            val resolved = provider.resolveSigningKey()

            assertEquals(SERVER_KEY_NAME, resolved.alias)
            assertNotEquals(PLANTED_ALIAS, resolved.alias, "a configured alias must not select key material")
        }

    @Test
    fun everyUnusableBindingShapeRefusesIdentically() =
        runTest {
            val collaborators = realCollaborators()
            provisionKey(collaborators.kms, PLANTED_ALIAS)

            val messages =
                BindingShape.entries.map { shape ->
                    val provider = seamProvider(collaborators, ShapedSigningKeyNameResolver(shape))
                    assertFailsWith<IllegalStateException> { provider.resolveSigningKey() }.message
                }

            assertEquals(1, messages.toSet().size, "unusable bindings must refuse identically, got $messages")
            assertEquals(AbstractConfigOid4vpVerifierConfigProvider.SIGNING_KEY_UNAVAILABLE, messages.first())
        }

    @Test
    fun createTimeAndFetchTimeResolveTheSameKey() =
        runTest {
            val collaborators = realCollaborators()
            provisionKey(collaborators.kms, SERVER_KEY_NAME)
            val provider = seamProvider(collaborators, FixedSigningKeyNameResolver(SERVER_KEY_NAME))

            // Create time: the authorization request substitutes the verifier's client_id from the binding.
            val createTime = provider.resolveSignerBinding(ClientIdScheme.DECENTRALIZED_IDENTIFIER) as VerifierSignerBinding.Did
            // Fetch time: the request_uri handler signs the JAR with the key resolved for the same binding.
            val fetchTime = provider.resolveSignerBinding(ClientIdScheme.DECENTRALIZED_IDENTIFIER) as VerifierSignerBinding.Did
            val signingKey = provider.resolveSigningKey()

            assertEquals(createTime.did, fetchTime.did, "the published client_id must not drift between create and fetch")
            assertEquals(createTime.verificationMethodId, fetchTime.verificationMethodId)
            assertTrue(createTime.verificationMethodId.startsWith(createTime.did))
            assertEquals(
                SERVER_KEY_NAME,
                signingKey.alias,
                "the key the JAR is signed with must be the same one the client_id was derived from",
            )
        }

    private fun seamProvider(
        collaborators: Collaborators,
        resolver: VerifierSigningKeyNameResolver,
    ): RegistryBackedOid4vpVerifierConfigProvider {
        val holder = DefaultOid4vpVerifierInstanceIdProvider()
        holder.setCurrentInstanceId(INSTANCE_ID)
        return RegistryBackedOid4vpVerifierConfigProvider(
            execution = TenantScopedTestSessionExecution(TestPrincipalConfigService(PLANTED_ALIAS_PROPERTIES), TENANT_ID),
            managedIdentifierService = collaborators.managedIdentifierService,
            kms = collaborators.kms,
            didProviderRegistry = collaborators.didProviderRegistry,
            instanceIdProvider = holder,
            signingKeyNameResolver = { resolver },
        )
    }

    private suspend fun provisionKey(
        kms: KeyManagerService,
        alias: String,
    ) {
        kms.generateKeyAsync(
            providerId = null,
            alias = alias,
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE,
        )
    }

    private data class Collaborators(
        val managedIdentifierService: ManagedIdentifierService,
        val kms: KeyManagerService,
        val didProviderRegistry: DidProviderRegistry,
    )

    private fun realCollaborators(): Collaborators {
        val app = createOid4vpRpJvmTestAppGraph(TestScope(), "test-verifier-seam", "test", "1.0.0")
        val session = app.userContextManager.getAnonymous().sessionContextManager.createOrGetFromId("seam", principalType = com.sphereon.di.context.PrincipalType.USER)
        val accessor = session.graph as VerifierCryptoCollaboratorsAccessor
        val kms = accessor.keyManagerService
        // The seam only resolves a key name; production never creates one. The fixture therefore
        // provisions its own keys, and that needs a concrete provider selected on the KMS rather
        // than the algorithm-only lookup, which finds nothing here.
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(SoftwareKmsProviderConfig(id = "test-software"), session.sessionExecution)
        kms.registerProvider(provider, makeDefaultKms = true)
        return Collaborators(
            managedIdentifierService = accessor.managedIdentifierService,
            kms = kms,
            didProviderRegistry = accessor.didProviderRegistry,
        )
    }

    private companion object {
        const val TENANT_ID = "tenant-acme"
        const val INSTANCE_ID = "acme"
        const val PLANTED_ALIAS = "planted-by-configuration"
        const val SERVER_KEY_NAME = "oid4vp-verifier-signing-acme"

        val PLANTED_ALIAS_PROPERTIES =
            mapOf<String, Any>(
                "oid4vp.verifiers.acme.request-object.signing.enabled" to "true",
                "oid4vp.verifiers.acme.request-object.signing.keyAlias" to PLANTED_ALIAS,
                "oid4vp.verifiers.acme.request-object.signing.providerId" to "planted-provider",
                "oid4vp.verifiers.acme.request-object.signing.mode" to "did:jwk",
            )
    }
}

/** The reasons a server-side binding cannot be honoured. All of them must look the same downstream. */
private enum class BindingShape {
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
private class ShapedSigningKeyNameResolver(
    private val shape: BindingShape,
) : VerifierSigningKeyNameResolver {
    override suspend fun resolveRequestObjectSigningKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String? =
        when (shape) {
            BindingShape.ABSENT,
            BindingShape.DETACHED,
            BindingShape.CROSS_TENANT,
            BindingShape.INACTIVE,
            BindingShape.UNMAPPED,
            -> null
        }
}

/** A deployment with a usable binding: the key name is server-derived and stable. */
private class FixedSigningKeyNameResolver(
    private val keyName: String,
) : VerifierSigningKeyNameResolver {
    override suspend fun resolveRequestObjectSigningKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String = keyName
}

/** [SessionExecution] carrying a tenant, which the signing-key seam is keyed on. */
private class TenantScopedTestSessionExecution(
    principal: PrincipalConfigService,
    override val tenantId: String,
) : SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = error("sessionContextManager not used in this test")
    override val log: SessionLogService = NoOpSessionLogService
    override val conf: ContextConfig = TestContextConfig(principal)
}
