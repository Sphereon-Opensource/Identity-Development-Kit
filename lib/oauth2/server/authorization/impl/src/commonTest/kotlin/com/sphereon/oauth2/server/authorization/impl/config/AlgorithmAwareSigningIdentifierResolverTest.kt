package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TenantOverrideSessionExecution
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

            val rsa = resolver.resolveSigningIdentifier("RS256") as ManagedOptsKeyInfo
            val ec = resolver.resolveSigningIdentifier("ES384") as ManagedOptsKeyInfo

            assertEquals("rsa-kid", rsa.identifier.kid)
            assertEquals("ec-kid", ec.identifier.kid)
            assertEquals(setOf("RS256", "ES384"), resolver.supportedSigningAlgorithms())
        }

    @Test
    fun rejectsAnAlgorithmWithoutAnActivePrivateKey() =
        runTest {
            val store = InMemorySigningKeyStore()
            register(store, "tenant-one", "rsa-kid", SignatureAlgorithm.RSA_SHA256, priority = 100)

            val failure = runCatching { resolver(store).resolveSigningIdentifier("ES256") }.exceptionOrNull()

            assertTrue(failure is OAuth2SigningKeyUnavailableException)
        }

    private fun resolver(store: InMemorySigningKeyStore) =
        DefaultAsServerSigningIdentifierResolver(
            execution = TenantOverrideSessionExecution(context.execution, "tenant-one"),
            configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to OAuth2ServerInstanceConfig(),
                            ),
                    ),
                ),
            signingKeyStore = store,
            activeSigningKeySnapshotCache = ActiveSigningKeySnapshotCache(),
        )

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
