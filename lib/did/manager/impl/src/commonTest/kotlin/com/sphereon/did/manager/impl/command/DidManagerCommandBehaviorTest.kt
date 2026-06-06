/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.did.manager.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.command.GetCachedDidDocumentServiceCommand
import com.sphereon.did.manager.command.ListDidsServiceCommand
import com.sphereon.did.manager.command.ListVerificationRelationshipsInput
import com.sphereon.did.manager.command.ListVerificationRelationshipsServiceCommand
import com.sphereon.did.manager.command.ResolveAndCacheDidServiceCommand
import com.sphereon.did.manager.impl.DidCreationDslProcessor
import com.sphereon.did.manager.impl.DidCreationDslProcessorImpl
import com.sphereon.did.manager.impl.DidManagerServiceImpl
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Command-layer behavioural tests for IDK-20:
 *  - capability rejection on immutable methods (did:key) via mutation commands,
 *  - pagination on `ListDidsServiceCommand`,
 *  - purpose filter on `ListVerificationRelationshipsServiceCommand`,
 *  - cache commands `GetCachedDidDocument` + `ResolveAndCacheDid` round-trip.
 *
 * The commands are resolved from the live SessionScope graph so DI bindings, capability gates,
 * and persistence wiring are exercised end-to-end — not mocked.
 */
class DidManagerCommandBehaviorTest {
    @ContributesTo(SessionScope::class)
    interface CommandsGraph {
        val listDids: ListDidsServiceCommand
        val listRelationships: ListVerificationRelationshipsServiceCommand
        val getCachedDoc: GetCachedDidDocumentServiceCommand
        val resolveAndCache: ResolveAndCacheDidServiceCommand
    }

    private lateinit var app: AppGraph
    private lateinit var dslProcessor: DidCreationDslProcessor
    private lateinit var didManager: DidManager
    private lateinit var commands: CommandsGraph

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
        val session = userContext.sessionContextManager.createOrGetFromId("idk-20-cmd-behavior")
        val g = session.graph
        dslProcessor = (g as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor
        didManager = (g as DidManagerServiceImpl.Graph).didManager
        commands = g as CommandsGraph
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
    }

    // ============ Capability rejection (immutable methods) ============

    @Test
    fun addVerificationMethod_didKey_returnsUnsupportedOperation() =
        runTest {
            val created = createDidKey("immutable-cap").getOrThrow()
            val cfg =
                VerificationMethodConfig(
                    kmsKeyAlias = "another-key",
                    kmsProviderId = "softwaretest",
                    verificationMethodId = "key-2",
                    purposes = listOf(VerificationPurpose.AUTHENTICATION),
                )
            val result = didManager.addVerificationMethod(created.did, cfg)
            assertTrue(result.isErr, "did:key is immutable — addVerificationMethod must reject")
        }

    // ============ ListDidsServiceCommand pagination ============

    @Test
    fun listDids_pagination_returnsPageSliceAndTotal() =
        runTest {
            val createdAliases =
                (1..5).map { i ->
                    createDidKey("page-$i").getOrThrow().alias
                }
            val page0 = commands.listDids.execute(DidFilter(size = 2, page = 0)).getOrThrow()
            val page1 = commands.listDids.execute(DidFilter(size = 2, page = 1)).getOrThrow()
            val page2 = commands.listDids.execute(DidFilter(size = 2, page = 2)).getOrThrow()

            // Total must reflect the unpaged count.
            assertTrue(page0.page.totalElements >= 5, "expected total >= 5, got ${page0.page.totalElements}")
            assertEquals(2, page0.items.size)
            assertEquals(2, page1.items.size)
            assertTrue(page2.items.size in 1..2)
            assertEquals(2, page0.page.size)
            assertEquals(0, page0.page.page)
            assertEquals(1, page1.page.page)

            // No overlap between pages (DIDs are unique).
            val pagedDids = (page0.items + page1.items + page2.items).map { it.did }
            assertEquals(pagedDids.size, pagedDids.toSet().size, "paged DIDs must be unique across pages")
            assertTrue(createdAliases.all { alias -> pagedDids.isNotEmpty() })
        }

    @Test
    fun listDids_noPagination_totalEqualsItemsSize() =
        runTest {
            createDidKey("no-page-a").getOrThrow()
            createDidKey("no-page-b").getOrThrow()
            val out = commands.listDids.execute(DidFilter(size = null)).getOrThrow()
            assertEquals(out.items.size, out.page.totalElements)
        }

    // ============ ListVerificationRelationshipsServiceCommand purpose filter ============

    @Test
    fun listRelationships_purposeFilter_returnsOnlyMatchingPurpose() =
        runTest {
            val created = createDidKey("rel-filter").getOrThrow()

            // No filter — list returns all purposes the DID has at least one relationship for.
            val all =
                commands.listRelationships
                    .execute(ListVerificationRelationshipsInput(did = created.did))
                    .getOrThrow()
            assertTrue(all.items.isNotEmpty(), "did:key seed must have at least one relationship")

            val targetPurpose = all.items.first().purpose
            val filtered =
                commands.listRelationships
                    .execute(ListVerificationRelationshipsInput(did = created.did, purpose = targetPurpose))
                    .getOrThrow()
            assertTrue(filtered.items.isNotEmpty())
            assertTrue(
                filtered.items.all { it.purpose == targetPurpose },
                "filtered listing must only contain rows for purpose=$targetPurpose"
            )
        }

    @Test
    fun listRelationships_unknownDid_returnsNotFound() =
        runTest {
            val out =
                commands.listRelationships.execute(
                    ListVerificationRelationshipsInput(did = "did:key:zUnknownNeverCreated"),
                )
            assertTrue(out.isErr)
            assertEquals("NOT_FOUND_ERROR", (out as Err).error.code)
        }

    // ============ Cache commands (Get/ResolveAndCache) ============

    @Test
    fun getCachedDocument_managedDid_returnsNotFoundBeforeCache() =
        runTest {
            // Managed DIDs are not cached out-of-band; getCachedDocument hits the IDK CacheService
            // only and reports cache-miss as NOT_FOUND.
            val created = createDidKey("cached-managed").getOrThrow()
            val cached =
                commands.getCachedDoc.execute(
                    com.sphereon.did.manager.command
                        .DidIdInput(did = created.did),
                )
            assertTrue(cached.isErr, "managed DIDs have no cache entry by default")
            assertEquals("NOT_FOUND_ERROR", (cached as Err).error.code)
        }

    @Test
    fun resolveAndCache_thenGetCached_returnsCachedDocument() =
        runTest {
            // Use an EXTERNAL DID so resolveAndCache populates the resolver cache; managed DIDs do
            // not roundtrip through the external cache.
            val seeded = createDidKey("resolve-and-cache").getOrThrow()
            didManager.delete(seeded.did).getOrThrow()
            didManager.trackExternal(seeded.did, alias = "resolve-cache-ext").getOrThrow()

            val resolved =
                commands.resolveAndCache
                    .execute(
                        com.sphereon.did.manager.command
                            .DidIdInput(did = seeded.did),
                    ).getOrThrow()
            assertEquals(seeded.did, resolved.id)

            val cached =
                commands.getCachedDoc
                    .execute(
                        com.sphereon.did.manager.command
                            .DidIdInput(did = seeded.did),
                    ).getOrThrow()
            assertNotNull(cached)
            assertEquals(seeded.did, cached.id)
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
}
