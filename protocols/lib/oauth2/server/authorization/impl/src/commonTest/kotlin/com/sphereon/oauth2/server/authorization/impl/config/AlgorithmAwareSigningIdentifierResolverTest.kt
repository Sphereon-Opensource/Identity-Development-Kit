package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.IdkResult
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TenantOverrideSessionExecution
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStoreError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AlgorithmAwareSigningIdentifierResolverTest {
    private val context = OAuth2ServerTestContext("algorithm-aware-signing-resolver", this)

    @Test
    fun resolvesTheActiveKeyMatchingTheRequestedAlgorithm() =
        runTest {
            val store = InMemorySigningKeyStore()
            register(store, "tenant-one", "rsa-kid", SignatureAlgorithm.RSA_SHA256, priority = 100)
            register(store, "tenant-one", "ec-kid", SignatureAlgorithm.ECDSA_SHA384, priority = 90)
            val resolver = resolver(store)

            val captured = CapturedAsServerConfig.select(configuredRoot(), "default")
            val rsa = resolver.selectSigning(captured, AsSigningRequirement.REQUIRED, "RS256").identifier as ManagedOptsKeyInfo
            val ec = resolver.selectSigning(captured, AsSigningRequirement.REQUIRED, "ES384").identifier as ManagedOptsKeyInfo

            assertEquals("rsa-kid", rsa.identifier.kid)
            assertEquals("ec-kid", ec.identifier.kid)
            assertEquals(setOf("RS256", "ES384"), resolver.selectSigning(captured, AsSigningRequirement.OPTIONAL).algorithms)
        }

    @Test
    fun rejectsAnAlgorithmWithoutAnActivePrivateKey() =
        runTest {
            val store = InMemorySigningKeyStore()
            register(store, "tenant-one", "rsa-kid", SignatureAlgorithm.RSA_SHA256, priority = 100)

            val failure = runCatching {
                resolver(store).selectSigning(CapturedAsServerConfig.select(configuredRoot(), "default"), AsSigningRequirement.REQUIRED, "ES256")
            }.exceptionOrNull()

            assertTrue(failure is OAuth2SigningKeyUnavailableException)
        }

    @Test
    fun synthesizedRootRejectsRequiredAlgorithmBeforeReadingRealSigningStore() =
        runTest {
            val realStore = InMemorySigningKeyStore()
            register(realStore, "tenant-one", "real-active", SignatureAlgorithm.RSA_SHA256, priority = 1)
            val counted = CountingSigningKeyStore(realStore)
            val synthesized = OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig()),
                explicitlyConfigured = false,
            )

            val failure = runCatching {
                resolver(counted).selectSigning(CapturedAsServerConfig.select(synthesized, null), AsSigningRequirement.REQUIRED, "RS256")
            }.exceptionOrNull()

            assertIs<IllegalStateException>(failure, "a required signer must reject an unconfigured AS root")
            assertEquals(0, counted.readCalls, "the real key store must not be consulted for a synthesized root")
        }

    @Test
    fun synthesizedRootAdvertisesNoAlgorithmsWithoutReadingRealSigningStore() =
        runTest {
            val realStore = InMemorySigningKeyStore()
            register(realStore, "tenant-one", "real-active", SignatureAlgorithm.RSA_SHA256, priority = 1)
            val counted = CountingSigningKeyStore(realStore)
            val synthesized = OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig()),
                explicitlyConfigured = false,
            )

            assertEquals(
                emptySet(),
                resolver(counted).selectSigning(CapturedAsServerConfig.select(synthesized, null), AsSigningRequirement.OPTIONAL).algorithms,
            )
            assertEquals(0, counted.readCalls, "a synthesized root has no AS algorithms to advertise")
        }

    @Test
    fun synthesizedRootOptionalDefaultSelectionReturnsNoSignerWithoutStoreRead() =
        runTest {
            val realStore = InMemorySigningKeyStore()
            register(realStore, "tenant-one", "real-active", SignatureAlgorithm.RSA_SHA256, priority = 1)
            val counted = CountingSigningKeyStore(realStore)
            val synthesized = OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig()),
                explicitlyConfigured = false,
            )

            assertNull(resolver(counted).selectSigning(CapturedAsServerConfig.select(synthesized, null), AsSigningRequirement.OPTIONAL).identifier)
            assertEquals(0, counted.readCalls)
        }

    @Test
    fun configuredHostedServerWithNoIssuerStillResolvesRequiredAlgorithm() =
        runTest {
            val store = InMemorySigningKeyStore()
            register(store, "tenant-one", "hosted-active", SignatureAlgorithm.RSA_SHA256, priority = 1)
            val configured = OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = null)),
                explicitlyConfigured = true,
            )

            val selected = resolver(store).selectSigning(
                CapturedAsServerConfig.select(configured, "default"),
                AsSigningRequirement.REQUIRED,
                "RS256",
            ).identifier as ManagedOptsKeyInfo

            assertEquals("hosted-active", selected.identifier.kid)
            assertEquals("RS256", selected.identifier.signatureAlgorithm?.jose?.value)
        }

    @Test
    fun configuredOpaqueServerCanResolveKeyForAnExplicitRequiredOperation() =
        runTest {
            val store = InMemorySigningKeyStore()
            register(store, "tenant-one", "operation-active", SignatureAlgorithm.RSA_SHA256, priority = 1)
            val configured = OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(
                    issuer = "https://opaque-as.example",
                    tokenFormat = TokenFormat.OPAQUE,
                    oidc = FeaturePolicy.DISABLED,
                )),
                explicitlyConfigured = true,
            )

            val selected = resolver(store).selectSigning(
                CapturedAsServerConfig.select(configured, "default"),
                AsSigningRequirement.REQUIRED,
                "RS256",
            ).identifier as ManagedOptsKeyInfo

            assertEquals("operation-active", selected.identifier.kid)
            assertEquals("RS256", selected.identifier.signatureAlgorithm?.jose?.value)
        }

    private fun resolver(
        store: SigningKeyStore,
    ) =
        DefaultAsServerSigningIdentifierResolver(
            execution = TenantOverrideSessionExecution(context.execution, "tenant-one"),
            signingKeyStore = store,
            activeSigningKeySnapshotCache = ActiveSigningKeySnapshotCache(),
        )

    private fun configuredRoot() = OAuth2ServersConfig(servers = mapOf("default" to OAuth2ServerInstanceConfig()))

    @Test
    fun configuredRootRequiresExactExplicitSelectionAndNeverDefaultsMissingKey() = runTest {
        val root = OAuth2ServersConfig(
            defaultServer = "a",
            servers = mapOf("a" to OAuth2ServerInstanceConfig(issuer = "https://a.example"),
                "b" to OAuth2ServerInstanceConfig(issuer = null)),
        )
        assertIs<IllegalArgumentException>(runCatching { CapturedAsServerConfig.select(root, null) }.exceptionOrNull())
        assertIs<IllegalStateException>(runCatching { CapturedAsServerConfig.select(root, "missing") }.exceptionOrNull())
        val selected = CapturedAsServerConfig.select(root, "b")
        assertEquals("b", selected.serverKey)
        assertNull(selected.server?.issuer)
        assertTrue(selected.explicitlyConfigured)
    }

    @Test
    fun synthesizedRootDoesNotAcquireAnExplicitSigningIdentity() = runTest {
        val root = configuredRoot().copy(explicitlyConfigured = false)
        assertIs<IllegalArgumentException>(runCatching { CapturedAsServerConfig.select(root, "default") }.exceptionOrNull())
        val selected = CapturedAsServerConfig.select(root, null)
        assertNull(selected.server)
        assertNull(selected.serverKey)
        assertTrue(!selected.explicitlyConfigured)
    }


    private class CountingSigningKeyStore(private val delegate: SigningKeyStore) : SigningKeyStore by delegate {
        var listAllCalls = 0
            private set
        var revisionCalls = 0
            private set
        var getActiveCalls = 0
            private set

        val readCalls: Int get() = listAllCalls + revisionCalls + getActiveCalls

        override suspend fun listAll(tenantId: String): IdkResult<List<OAuth2SigningKey>, SigningKeyStoreError> {
            listAllCalls += 1
            return delegate.listAll(tenantId)
        }

        override suspend fun contentRevision(tenantId: String): IdkResult<Long, SigningKeyStoreError> {
            revisionCalls += 1
            return delegate.contentRevision(tenantId)
        }

        override suspend fun getActive(tenantId: String): IdkResult<OAuth2SigningKey?, SigningKeyStoreError> {
            getActiveCalls += 1
            return delegate.getActive(tenantId)
        }
    }

    private suspend fun register(
        store: InMemorySigningKeyStore,
        tenantId: String,
        kid: String,
        algorithm: SignatureAlgorithm,
        priority: Int,
    ) {
        val now = Clock.System.now()
        val result =
            store.register(
                OAuth2SigningKey(
                    tenantId = tenantId,
                    keyInfo =
                        KeyInfo<KeyType>(
                            alias = kid,
                            kid = kid,
                            providerId = "software",
                            signatureAlgorithm = algorithm,
                        ),
                    state = OAuth2SigningKeyState.ACTIVE,
                    priority = priority,
                    createdAt = now,
                    notBefore = now,
                ),
            )
        assertTrue(result.isOk)
    }
}
