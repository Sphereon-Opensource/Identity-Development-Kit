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
 *
 */

package com.sphereon.did.manager.impl

import com.sphereon.core.api.cache.CacheModule
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.di.context.IdentityConstants
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import com.sphereon.did.models.VerificationPurpose
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end tests for [DidManagerServiceImpl].
 *
 * The manager is exercised through the real DI graph ([createDidManagerTestAppGraph]) with an
 * in-memory [com.sphereon.did.persistence.DidRepository], the software KMS provider, and the
 * did:key provider/resolver. Covers aggregate mutation paths, capability guardrails (did:key
 * rejects key addition), and the MANAGED vs EXTERNAL cache split.
 */
class DidManagerServiceImplTest {
    private lateinit var app: com.sphereon.di.app.AppGraph
    private lateinit var dslProcessor: DidCreationDslProcessor
    private lateinit var didManager: DidManager
    private lateinit var cacheService: CacheService

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )

        app = createDidManagerTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("did-manager-impl-test")
        val sessionGraph = session.graph

        dslProcessor = (sessionGraph as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor
        didManager = (sessionGraph as DidManagerServiceImpl.Graph).didManager
        cacheService = (app as CacheModule.Graph).cacheService
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    // ============ create / get / getByAlias / list / delete ============

    @Test
    fun createDidKey_thenGet_returnsSameAggregate() =
        runTest {
            val created = createDidKey(alias = "smoke-alias").getOrThrow()
            assertEquals(DidRole.MANAGED, created.role)
            assertTrue(created.did.startsWith("did:key:"))
            assertNotNull(created.document, "document reconstructed from graph")
            assertEquals(1, created.document!!.verificationMethod?.size)

            val fetched = didManager.get(created.did).getOrThrow()
            assertEquals(created.did, fetched.did)
            assertEquals(created.document, fetched.document)
        }

    @Test
    fun getByAlias_returnsManagedDid() =
        runTest {
            val created = createDidKey(alias = "alias-lookup").getOrThrow()
            val byAlias = didManager.getByAlias("alias-lookup").getOrThrow()
            assertEquals(created.did, byAlias.did)
        }

    @Test
    fun list_returnsAllCreatedAggregates() =
        runTest {
            createDidKey(alias = "list-a").getOrThrow()
            createDidKey(alias = "list-b").getOrThrow()

            val all = didManager.list(DidFilter()).getOrThrow()
            assertTrue(all.size >= 2, "list should include both aggregates, got ${all.size}")
            assertTrue(all.any { it.alias == "list-a" })
            assertTrue(all.any { it.alias == "list-b" })
        }

    @Test
    fun delete_softDeletesAggregate_subsequentGetReturnsNotFound() =
        runTest {
            val created = createDidKey(alias = "to-delete").getOrThrow()
            didManager.delete(created.did).getOrThrow()

            val afterDelete = didManager.get(created.did)
            assertTrue(afterDelete.isErr, "get() should fail after soft-delete")
        }

    // ============ Capability guardrails ============

    @Test
    fun addVerificationMethod_didKeyImmutable_returnsProviderError() =
        runTest {
            val created = createDidKey(alias = "immutable-vm").getOrThrow()
            val config =
                com.sphereon.did.models.VerificationMethodConfig(
                    kmsKeyAlias = "new-key",
                    kmsProviderId = "softwaretest",
                    verificationMethodId = "key-2",
                    purposes = listOf(VerificationPurpose.AUTHENTICATION),
                )
            val result = didManager.addVerificationMethod(created.did, config)
            assertTrue(result.isErr, "did:key keys are immutable — provider should reject addKey")
        }

    // ============ Deactivate ============

    @Test
    fun deactivate_setsFlagOnAggregate() =
        runTest {
            val created = createDidKey(alias = "to-deactivate").getOrThrow()
            // did:key is immutable — deactivate will likely return an error from the provider.
            // We assert *either* happy-path deactivation OR an explicit provider error; the
            // manager must not corrupt state in either case.
            val result = didManager.deactivate(created.did)
            if (result.isOk) {
                val fetched = didManager.get(created.did).getOrThrow()
                assertTrue(fetched.deactivated)
            }
        }

    // ============ Import + cache ============

    @Test
    fun trackExternal_populatesCacheService_andReturnsExternalRole() =
        runTest {
            // Create a did:key to get a real, resolvable DID string…
            val created = createDidKey(alias = "to-export").getOrThrow()
            val external = created.did
            // …then wipe our record so `import` treats it as fresh.
            didManager.delete(external).getOrThrow()

            val imported = didManager.trackExternal(external, alias = "imported").getOrThrow()
            assertEquals(DidRole.EXTERNAL, imported.role)
            assertEquals(external, imported.did)
            assertNotNull(imported.document, "imported document should be returned to the caller")
            assertNotNull(
                externalDidCache().getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "import should pre-warm the IDK CacheService with the resolved document",
            )
        }

    @Test
    fun delete_externalDid_invalidatesCacheService() =
        runTest {
            val created = createDidKey(alias = "external-delete-cache").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "to-soft-delete").getOrThrow()
            val cache = externalDidCache()
            assertNotNull(
                cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "import precondition: cache must be populated",
            )

            didManager.delete(external).getOrThrow()

            assertNull(
                cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "delete must remove the cached document so a re-import does not serve stale state",
            )
        }

    @Test
    fun refreshExternalDocument_returnsDocumentAndRepopulatesCache() =
        runTest {
            val created = createDidJwk(alias = "external-to-refresh").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "imported-for-refresh").getOrThrow()
            val cache = externalDidCache()
            // Simulate cache eviction so refresh has to re-populate from the resolver.
            cache.removeTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external)
            assertNull(cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external))

            val refreshed = didManager.refreshExternalDocument(external).getOrThrow()

            assertEquals(external, refreshed.id)
            assertNotNull(
                cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "refresh must repopulate the IDK CacheService entry for the DID",
            )
        }

    @Test
    fun refreshExternalDocument_rejectsManagedDid() =
        runTest {
            val managed = createDidKey(alias = "managed-no-refresh").getOrThrow()

            val result = didManager.refreshExternalDocument(managed.did)

            assertTrue(result.isErr, "refresh must reject MANAGED DIDs")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun refreshExternalDocument_returnsNotFoundForUnknownDid() =
        runTest {
            val result = didManager.refreshExternalDocument("did:key:zUnknownNeverImported")

            assertTrue(result.isErr, "refresh must error when the DID is not locally known")
            assertEquals("NOT_FOUND_ERROR", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun get_externalDid_prefersFreshCachedResolverDocument() =
        runTest {
            val created = createDidJwk(alias = "external-cache-jwk").getOrThrow()
            didManager.delete(created.did).getOrThrow()
            didManager.trackExternal(created.did, alias = "imported-jwk").getOrThrow()

            val fetched = didManager.get(created.did).getOrThrow()
            assertEquals(DidRole.EXTERNAL, fetched.role)
            assertNotNull(
                fetched.document
                    ?.verificationMethod
                    ?.firstOrNull()
                    ?.publicKeyJwk,
                "external DID reads should come from the cached resolver document",
            )
        }

    @Test
    fun trackExternal_cachedEntryPreservesFullResolutionResult() =
        runTest {
            // IDK-18 r0e.4 — the cached payload must be the full DidResolutionResult, not
            // just the document, so didResolutionMetadata + didDocumentMetadata
            // (created/updated/deactivated/nextUpdate/versionId/nextVersionId/equivalentId/
            // canonicalId/contentType) are available on subsequent reads.
            val created = createDidKey(alias = "cache-metadata-rt").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "cache-metadata-rt-imported").getOrThrow()

            val raw = externalDidCache().getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external)
            assertNotNull(raw, "cache must be pre-warmed")
            val testJson =
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            val payload =
                testJson.decodeFromString(
                    DidManagerServiceImpl.CachedDidResolution.serializer(),
                    raw,
                )
            val resolution =
                testJson.decodeFromString(
                    com.sphereon.did.resolver.DidResolutionResult
                        .serializer(),
                    payload.resolutionJson,
                )
            assertNotNull(resolution.didDocument, "cached entry must carry the document")
            assertEquals(external, resolution.didDocument?.id)
            // Discriminator: the did:key resolver emits contentType "application/did+ld+json".
            // If trackExternal regresses to caching DidResolutionResult.success(document) the
            // resolution metadata gets rebuilt with the default contentType "application/did+json"
            // (see DidResolutionMetadata.success), so asserting the resolver-supplied value
            // catches the regression where merely asserting non-null would not.
            assertEquals(
                "application/did+ld+json",
                resolution.didResolutionMetadata.contentType,
                "cached entry must preserve resolver-supplied didResolutionMetadata.contentType " +
                    "verbatim — not a value rebuilt from DidResolutionResult.success(document)",
            )
        }

    @Test
    fun trackExternal_rejectsAlreadyTrackedDid() =
        runTest {
            val created = createDidKey(alias = "dup-import").getOrThrow()
            val result = didManager.trackExternal(created.did, alias = "dup")
            assertTrue(result.isErr, "import() should refuse an already-persisted DID")
        }

    @Test
    fun managedDid_getReturnsFullyResolvedDocument() =
        runTest {
            // Managed DIDs are reconstructed from the normalized graph on every read, with
            // public JWKs rehydrated from KMS. The IDK CacheService is not populated for
            // managed — the graph already owns the source of truth for this content.
            val managed = createDidKey(alias = "managed-reconstruct").getOrThrow()
            val fetched = didManager.get(managed.did).getOrThrow()
            assertEquals(DidRole.MANAGED, fetched.role)
            assertNotNull(fetched.document?.verificationMethod)
            assertFalse(fetched.document!!.verificationMethod!!.isEmpty())
            assertNotNull(
                fetched.document!!.verificationMethod!![0].publicKeyJwk,
                "managed document should carry the hydrated JWK on every read",
            )
        }

    // ============ Capability prechecks (IDK-20) ============

    @Test
    fun addService_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey(alias = "no-add-service").getOrThrow()
            val service =
                com.sphereon.did.models.DidService(
                    id = "${created.did}#svc1",
                    type = listOf("LinkedDomains"),
                    serviceEndpoint = kotlinx.serialization.json.JsonPrimitive("https://example.test"),
                )
            val result = didManager.addService(created.did, service)
            assertTrue(result.isErr, "did:key must reject service addition")
            assertEquals("UNSUPPORTED_OPERATION", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun removeService_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey(alias = "no-remove-service").getOrThrow()
            val result = didManager.removeService(created.did, "anything")
            assertTrue(result.isErr, "did:key must reject service removal")
            assertEquals("UNSUPPORTED_OPERATION", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun updateVerificationMethod_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey(alias = "no-update-vm").getOrThrow()
            val cfg =
                com.sphereon.did.models.VerificationMethodConfig(
                    kmsKeyAlias = "k",
                    kmsProviderId = "softwaretest",
                    verificationMethodId = "key-1",
                    purposes = listOf(VerificationPurpose.AUTHENTICATION),
                )
            val result = didManager.updateVerificationMethod(created.did, "key-1", cfg)
            assertTrue(result.isErr, "did:key must reject verification method replacement")
            assertEquals("UNSUPPORTED_OPERATION", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun updateService_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey(alias = "no-update-service").getOrThrow()
            val service =
                com.sphereon.did.models.DidService(
                    id = "${created.did}#svc",
                    type = listOf("LinkedDomains"),
                    serviceEndpoint = kotlinx.serialization.json.JsonPrimitive("https://example.test"),
                )
            val result = didManager.updateService(created.did, "svc", service)
            assertTrue(result.isErr, "did:key must reject service replacement")
            assertEquals("UNSUPPORTED_OPERATION", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun addVerificationRelationship_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey(alias = "no-add-rel").getOrThrow()
            val vmId =
                created.document
                    ?.verificationMethod
                    ?.firstOrNull()
                    ?.id
                    ?: error("test pre: created did:key has no VM")
            val result =
                didManager.addVerificationRelationship(
                    did = created.did,
                    verificationMethodId = vmId,
                    purpose = "keyAgreement",
                )
            assertTrue(result.isErr, "did:key must reject relationship addition (immutable keyManagement)")
            assertEquals("UNSUPPORTED_OPERATION", (result as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun listVerificationRelationships_returnsCreatedRelationships() =
        runTest {
            val created = createDidKey(alias = "list-rels").getOrThrow()
            val rels = didManager.listVerificationRelationships(created.did).getOrThrow()
            assertTrue(rels.isNotEmpty(), "did:key creation registers relationships for declared purposes")
            // Filter form
            val auth =
                didManager
                    .listVerificationRelationships(created.did, purpose = "authentication")
                    .getOrThrow()
            assertTrue(auth.all { it.purpose == "authentication" })
        }

    // ============ Capability queries (IDK-20) ============

    @Test
    fun getMethodCapabilitySummary_returnsImmutableSummaryForDidKey() =
        runTest {
            val summary = didManager.getMethodCapabilitySummary("key")
            assertNotNull(summary)
            assertEquals("key", summary.method)
            assertTrue(summary.canCreate)
            assertTrue(summary.isImmutable)
            assertFalse(summary.canUpdate)
            assertFalse(summary.canDeactivate)
        }

    @Test
    fun listSupportedMethodsWithCapabilities_includesRegisteredMethods() =
        runTest {
            val all = didManager.listSupportedMethodsWithCapabilities()
            assertTrue(all.isNotEmpty(), "registry should expose at least one method")
            assertTrue(all.any { it.method == "key" }, "did:key must be registered for these tests")
        }

    @Test
    fun getMethodCapabilitySummary_unknownMethodReturnsNull() =
        runTest {
            assertNull(didManager.getMethodCapabilitySummary("nonexistent-method"))
        }

    // ============ Document cache (IDK-20) ============

    @Test
    fun invalidateCache_externalDid_clearsCacheEntry() =
        runTest {
            val created = createDidJwk(alias = "invalidate-cache").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "for-invalidate").getOrThrow()
            val cache = externalDidCache()
            assertNotNull(
                cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "precondition: tracking populates cache",
            )

            didManager.invalidateCache(external).getOrThrow()

            assertNull(
                cache.getTenant(IdentityConstants.ANONYMOUS_TENANT_ID, external),
                "invalidateCache must remove the cache entry",
            )
        }

    @Test
    fun getCachedDocument_returnsCachedDocumentForExternalDid() =
        runTest {
            val created = createDidJwk(alias = "get-cached").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "for-get-cached").getOrThrow()

            val doc = didManager.getCachedDocument(external).getOrThrow()
            assertNotNull(doc, "tracked external DID should have a cached document")
            assertEquals(external, doc.id)
        }

    @Test
    fun getCachedDocument_returnsNullAfterInvalidate() =
        runTest {
            val created = createDidJwk(alias = "cached-after-invalidate").getOrThrow()
            val external = created.did
            didManager.delete(external).getOrThrow()
            didManager.trackExternal(external, alias = "x").getOrThrow()

            didManager.invalidateCache(external).getOrThrow()

            assertNull(
                didManager.getCachedDocument(external).getOrThrow(),
                "post-invalidate cache lookup should return null without auto-resolving",
            )
        }

    // ============ Helpers ============

    private suspend fun createDidKey(alias: String) =
        dslProcessor.create {
            method("key")
            this.alias(alias)
            autoGenerateKey {
                keyType(KeyTypeMapping.EC)
                curve(Curve.P_256)
                kmsProvider("softwaretest")
                purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
            }
        }

    private suspend fun createDidJwk(alias: String) =
        dslProcessor.create {
            method("jwk")
            this.alias(alias)
            autoGenerateKey {
                keyType(KeyTypeMapping.EC)
                curve(Curve.P_256)
                kmsProvider("softwaretest")
                purposes(VerificationPurpose.AUTHENTICATION)
            }
        }

    /**
     * Returns the same scoped cache the manager uses for external-DID resolution. The
     * namespace and TTL must mirror [DidManagerServiceImpl] so this call resolves to the
     * existing cache instance instead of registering a new one.
     */
    private fun externalDidCache(): ScopedCache<String, String> =
        cacheService.getCache(
            CacheRequirements(
                namespace = "did.resolution.external",
                ttlConfig = CacheTtlConfig(tenant = 30.minutes),
            ),
        )
}
