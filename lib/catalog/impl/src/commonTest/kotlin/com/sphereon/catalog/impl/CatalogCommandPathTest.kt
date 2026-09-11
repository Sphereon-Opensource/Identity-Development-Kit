/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.client.LinkedTypeSnapshot
import com.sphereon.catalog.command.CatalogIdArgs
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.EvaluateCatalogVerificationArgs
import com.sphereon.catalog.command.ImportRemoteCatalogArgs
import com.sphereon.catalog.command.ImportRulebooksArgs
import com.sphereon.catalog.command.LinkSchemaArgs
import com.sphereon.catalog.command.ListSchemasArgs
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.command.UpdateCatalogArgs
import com.sphereon.catalog.command.UpdateSchemaArgs
import com.sphereon.catalog.impl.client.DefaultCatalogLinkedTypeSource
import com.sphereon.catalog.impl.command.CreateCatalogCommandImpl
import com.sphereon.catalog.impl.command.CreateSchemaCommandImpl
import com.sphereon.catalog.impl.command.DisableCatalogCommandImpl
import com.sphereon.catalog.impl.command.EvaluateCatalogVerificationCommandImpl
import com.sphereon.catalog.impl.command.GetSchemaCommandImpl
import com.sphereon.catalog.impl.command.ImportRemoteCatalogCommandImpl
import com.sphereon.catalog.impl.command.ImportRulebooksCommandImpl
import com.sphereon.catalog.impl.command.LinkSchemaCommandImpl
import com.sphereon.catalog.impl.command.ListSchemasCommandImpl
import com.sphereon.catalog.impl.command.PublishCatalogCommandImpl
import com.sphereon.catalog.impl.command.ResolveAttestationTypeCommandImpl
import com.sphereon.catalog.impl.command.UpdateCatalogCommandImpl
import com.sphereon.catalog.impl.command.UpdateSchemaCommandImpl
import com.sphereon.catalog.impl.facade.CatalogManagementFacadeImpl
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogListingWindow
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.catalog.model.TrustFrameworkType
import com.sphereon.catalog.persistence.memory.InMemoryAttestationCatalogStore
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class CatalogCommandPathTest {
    @Test
    fun createPublishAndPublicList() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("webuild-pid", "PID")).value
            env.createSchema.execute(
                CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()),
            )
            val published = env.publish.execute(CatalogIdArgs(created.id)).value
            assertEquals(AttestationCatalogStatus.PUBLISHED, published.status)
            val publicList =
                env.listSchemas.execute(ListSchemasArgs(slug = "webuild-pid", publishedOnly = true, listedOnly = true))
            assertTrue(publicList.isOk)
            assertEquals(1, publicList.value.total)
            assertTrue(
                publicList.value.data
                    .single()
                    .rulebookURI
                    .contains("/public/catalogs/webuild-pid/")
            )
        }

    @Test
    fun createRejectsBareJsonSchemaAsSdJwtFormat() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("bad-vct", "Bad")).value
            val result =
                env.createSchema.execute(
                    CreateSchemaArgs(
                        created.id,
                        schema(),
                        documents().map { doc ->
                            if (doc.formatIdentifier == "dc+sd-jwt") {
                                doc.copy(bytes = """{"properties":{"vct":{"const":"urn:eudi:pid:1"}}}""".encodeToByteArray())
                            } else {
                                doc
                            }
                        },
                    ),
                )
            assertTrue(result.isErr)
        }

    @Test
    fun managementListSeesDraftSchemas() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("draft-pid", "Draft")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema(), documents()))
            val listed = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id))
            assertTrue(listed.isOk)
            assertEquals(1, listed.value.total)
            val fetched =
                env.getSchema.execute(
                    SchemaIdArgs(
                        catalogId = created.id,
                        schemaId =
                            listed.value.data
                                .single()
                                .id!!
                    )
                )
            assertTrue(fetched.isOk)
        }

    @Test
    fun authoredSchemaHonoursScheduledListingWindow() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("own-pid", "Own")).value
            val now = Clock.System.now()
            val createdSchema =
                env.createSchema
                    .execute(
                        CreateSchemaArgs(
                            created.id,
                            schema("https://example.test/vct/own"),
                            documents(),
                            listing = CatalogListingWindow(start = now + 30.days, end = now + 60.days),
                        ),
                    ).value
            env.publish.execute(CatalogIdArgs(created.id))
            val listedNow =
                env.listSchemas.execute(ListSchemasArgs(slug = "own-pid", publishedOnly = true, listedOnly = true))
            assertEquals(emptyList(), listedNow.value.data.map { it.id })
            val all =
                env.listSchemas.execute(ListSchemasArgs(slug = "own-pid", publishedOnly = true, listedOnly = false))
            assertEquals(listOf(createdSchema.id), all.value.data.map { it.id })
        }

    @Test
    fun updateListingOnOwnCatalogDoesNotWipeDocuments() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("own-update", "Own")).value
            val createdSchema =
                env.createSchema.execute(CreateSchemaArgs(created.id, schema(), documents())).value
            val schemaId = requireNotNull(createdSchema.id)
            val withdrawnAt = Clock.System.now()
            val updated =
                env.updateSchema.execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = schemaId,
                        schema = createdSchema,
                        listing = CatalogListingWindow.never(withdrawnAt),
                    ),
                )
            assertTrue(updated.isOk)
            val stored =
                env.store
                    .listSchemas("acme", created.id)
                    .value
                    .single()
            assertTrue(stored.listing.isEmpty())
            assertEquals(2, stored.documents.size)
            val listedNow =
                env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = true))
            assertEquals(emptyList(), listedNow.value.data.map { it.id })
        }

    @Test
    fun createWithoutDocumentsIsRejected() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("docs-pid", "Docs")).value
            val result = env.createSchema.execute(CreateSchemaArgs(created.id, schema(), emptyList()))
            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun publishWithoutDocumentsIsRejected() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("pub-docs", "Docs")).value
            env.store.saveSchema(
                "acme",
                com.sphereon.catalog.model.AttestationSchemaRecord(
                    catalogId = created.id,
                    schema = schema().copy(id = "11111111-1111-1111-1111-111111111111"),
                    provenance = com.sphereon.catalog.model.CatalogSchemaProvenance.AUTHORED,
                    documents = emptyList(),
                ),
            )
            val result = env.publish.execute(CatalogIdArgs(created.id))
            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun duplicateSlugIsIllegalArgument() =
        runTest {
            val env = Env()
            env.createCatalog.execute(CreateCatalogArgs("dup-slug", "One"))
            val second = env.createCatalog.execute(CreateCatalogArgs("dup-slug", "Two"))
            assertTrue(second.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", second.error.code)
        }

    @Test
    fun draftSlugCanChangeAndPublishedSlugCannot() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("old-slug", "Old")).value
            val renamed =
                env.updateCatalog
                    .execute(
                        UpdateCatalogArgs(created.id, "Old", null, false, slug = "new-slug"),
                    ).value
            assertEquals("new-slug", renamed.slug)
            env.createSchema.execute(CreateSchemaArgs(created.id, schema(), documents()))
            env.publish.execute(CatalogIdArgs(created.id))
            val blocked =
                env.updateCatalog.execute(
                    UpdateCatalogArgs(created.id, "Old", null, false, slug = "blocked"),
                )
            assertTrue(blocked.isErr)
            assertEquals("ILLEGAL_STATE_ERROR", blocked.error.code)
        }

    @Test
    fun illegalStatusTransitions() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("status-pid", "Status")).value
            val disableDraft = env.disable.execute(CatalogIdArgs(created.id))
            assertTrue(disableDraft.isErr)
            assertEquals("ILLEGAL_STATE_ERROR", disableDraft.error.code)
            env.createSchema.execute(CreateSchemaArgs(created.id, schema(), documents()))
            env.publish.execute(CatalogIdArgs(created.id))
            val republish = env.publish.execute(CatalogIdArgs(created.id))
            assertTrue(republish.isErr)
            assertEquals("ILLEGAL_STATE_ERROR", republish.error.code)
        }

    @Test
    fun disableMakesPublicLookupNotFound() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("off-pid", "Off")).value
            val schema = env.createSchema.execute(CreateSchemaArgs(created.id, schema(), documents())).value
            env.publish.execute(CatalogIdArgs(created.id))
            env.disable.execute(CatalogIdArgs(created.id))
            val listed = env.listSchemas.execute(ListSchemasArgs(slug = "off-pid", publishedOnly = true))
            assertTrue(listed.isErr)
            assertEquals("NOT_FOUND_ERROR", listed.error.code)
            val fetched =
                env.getSchema.execute(SchemaIdArgs(slug = "off-pid", schemaId = schema.id!!, publishedOnly = true))
            assertTrue(fetched.isErr)
        }

    @Test
    fun linkFillsHostedVctFromVctId() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("link-pid", "Link")).value
            val linked =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPid",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        documents = documents(),
                    ),
                )
            assertTrue(linked.isOk)
            assertEquals(
                "/public/schema/vct/EuPid",
                linked.value.schemaURIs
                    .single()
                    .uri
            )
        }

    @Test
    fun relinkingTheSameDesignIsIdempotent() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("relink-pid", "Relink")).value
            val first =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPid",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        documents = documents(),
                    ),
                )
            val second =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPid",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        documents = documents(),
                    ),
                )
            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertEquals(first.value.id, second.value.id)
            val listed = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, linkedVctId = "EuPid"))
            assertEquals(1, listed.value.total)
        }

    @Test
    fun linkWithoutRulebookIsMembershipOnly() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("member-pid", "Member")).value
            val linked =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPid",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                    ),
                )
            assertTrue(linked.isOk)
            val stored =
                env.store
                    .listSchemas("acme", created.id)
                    .value
                    .single()
            assertTrue(stored.listing.isEmpty())
            val listedOnly = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = true))
            assertEquals(0, listedOnly.value.total)
        }

    @Test
    fun linkWithOpenListingRequiresARulebook() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("listed-pid", "Listed")).value
            val linked =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPid",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        listing =
                            CatalogListingWindow.open(
                                kotlin.time.Clock.System
                                    .now()
                            ),
                    ),
                )
            assertTrue(linked.isErr)
            assertEquals("A RULEBOOK document is required for listed schemas", linked.error.message.defaultMessage)
        }

    @Test
    fun linkWithoutBoundVctSourceFailsClosed() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("nolink-pid", "NoLink")).value
            val linked =
                LinkSchemaCommandImpl(TestSessionExecution, env.store, DefaultCatalogLinkedTypeSource()).execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "missing",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        documents = documents(),
                    ),
                )
            assertTrue(linked.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", linked.error.code)
        }

    @Test
    fun importRemoteFetchesRulebookAndKeepsInvalidRowsUnlisted() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("imp-pid", "Import")).value
            val client =
                FixtureRemoteClient(
                    pages =
                        listOf(
                            PaginatedSchemaList(
                                total = 2,
                                limit = 100,
                                offset = 0,
                                data =
                                    listOf(
                                        schema("https://example.test/vct/good").copy(id = "good"),
                                        schema("https://example.test/vct/bad").copy(
                                            id = "bad",
                                            attestationLoS = "not-a-los",
                                        ),
                                    ),
                            ),
                        ),
                    documents =
                        mapOf(
                            "https://example.test/vct/good" to
                                CatalogDocument(
                                    "application/json",
                                    """{"vct":"https://example.test/vct/good"}""".encodeToByteArray(),
                                ),
                            "https://example.test/vct/bad" to
                                CatalogDocument(
                                    "application/json",
                                    """{"vct":"https://example.test/vct/bad"}""".encodeToByteArray(),
                                ),
                            "https://example.test/rulebook" to CatalogDocument("text/markdown", "# rb".encodeToByteArray()),
                        ),
                )
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client)
            val report = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1"))
            assertTrue(report.isOk)
            assertEquals(2, report.value.imported)
            val management = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = false))
            assertEquals(2, management.value.total)
            val listedOnly = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = true))
            assertEquals(1, listedOnly.value.total)
            val stored = env.store.listSchemas("acme", created.id).value
            assertTrue(stored!!.any { it.documents.any { doc -> doc.kind == CatalogDocumentKind.RULEBOOK } })
            val firstIds = stored.map { it.schema.id }.toSet()
            val again = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1"))
            assertTrue(again.isOk)
            val afterUpsert = env.store.listSchemas("acme", created.id).value!!
            assertEquals(firstIds, afterUpsert.map { it.schema.id }.toSet())
            assertEquals(2, afterUpsert.size)
        }

    @Test
    fun importRemoteWithdrawsImportedTypesRemovedFromTheRemoteList() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("imp-gone", "Import gone")).value
            val client =
                MutableRemoteClient(
                    schemas =
                        mutableListOf(
                            schema("https://example.test/vct/keep").copy(id = "keep"),
                            schema("https://example.test/vct/drop").copy(id = "drop"),
                        ),
                )
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client)
            importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1"))
            client.schemas.removeAll { it.id == "drop" }
            importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1"))
            val stored = env.store.listSchemas("acme", created.id).value!!
            assertEquals(2, stored.size)
            assertTrue(
                stored.single { it.schema.id == "keep" }.listing.includes(
                    kotlin.time.Clock.System
                        .now()
                )
            )
            assertTrue(stored.single { it.schema.id == "drop" }.listing.isEmpty())
        }

    @Test
    fun rulebookGitUrlIsRejected() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("git-pid", "Git")).value
            val result =
                env.importRulebooks.execute(
                    ImportRulebooksArgs(
                        catalogId = created.id,
                        files = emptyMap(),
                        sourceUrl = "https://example.test/rulebook-catalog.git",
                    ),
                )
            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun evaluateTrustedAuthoritiesAndIncludeDisabled() =
        runTest {
            val env = Env()
            val created =
                env.createCatalog.execute(CreateCatalogArgs("eval-pid", "Eval", verificationEnabled = true)).value
            env.createSchema.execute(
                CreateSchemaArgs(
                    created.id,
                    schema("https://example.test/vct/pid").copy(
                        trustedAuthorities = listOf(TrustAuthority(TrustFrameworkType.etsi_tl, "https://tl.example.test")),
                    ),
                    documents(),
                ),
            )
            env.publish.execute(CatalogIdArgs(created.id))
            val decision =
                env.evaluate
                    .execute(
                        EvaluateCatalogVerificationArgs(
                            type = AttestationTypeKey(AttestationTypeKeyKind.VCT, "pid"),
                            mode = CatalogVerificationMode.TYPE_AND_TRUSTED_AUTHORITIES,
                        ),
                    ).value
            assertEquals(CatalogVerificationOutcome.ALLOW, decision.outcome)
            assertEquals("https://tl.example.test", decision.selectedAuthorities.single().value)
            env.disable.execute(CatalogIdArgs(created.id))
            val hidden =
                env.resolve.execute(ResolveAttestationTypeArgs(AttestationTypeKey(AttestationTypeKeyKind.VCT, "pid")))
            assertEquals(0, hidden.value.total)
            val included =
                env.resolve.execute(
                    ResolveAttestationTypeArgs(
                        type = AttestationTypeKey(AttestationTypeKeyKind.VCT, "pid"),
                        includeDisabled = true,
                    ),
                )
            assertEquals(1, included.value.total)
        }

    @Test
    fun facadeDelegatesCreate() =
        runTest {
            val env = Env()
            val facade =
                CatalogManagementFacadeImpl(
                    createCatalogCommand = env.createCatalog,
                    updateCatalogCommand = env.updateCatalog,
                    publishCatalogCommand = env.publish,
                    disableCatalogCommand = env.disable,
                    createSchemaCommand = env.createSchema,
                    updateSchemaCommand = UnusedUpdateSchema,
                    deleteSchemaCommand = UnusedDeleteSchema,
                    linkSchemaCommand = env.link,
                    importRemoteCommand = UnusedImportRemote,
                    importRulebooksCommand = env.importRulebooks,
                )
            val created = facade.createCatalog(CreateCatalogArgs("facade-pid", "Facade"))
            assertTrue(created.isOk)
            assertEquals(
                "facade-pid",
                env.store
                    .findCatalogBySlug("acme", "facade-pid")
                    .value
                    ?.slug
            )
        }

    private class Env {
        val store = InMemoryAttestationCatalogStore()
        val createCatalog = CreateCatalogCommandImpl(TestSessionExecution, store)
        val updateCatalog = UpdateCatalogCommandImpl(TestSessionExecution, store)
        val createSchema = CreateSchemaCommandImpl(TestSessionExecution, store)
        val updateSchema = UpdateSchemaCommandImpl(TestSessionExecution, store)
        val publish = PublishCatalogCommandImpl(TestSessionExecution, store)
        val disable = DisableCatalogCommandImpl(TestSessionExecution, store)
        val listSchemas = ListSchemasCommandImpl(TestSessionExecution, store)
        val getSchema = GetSchemaCommandImpl(TestSessionExecution, store)
        val link = LinkSchemaCommandImpl(TestSessionExecution, store, FakeLinkedTypeSource())
        val importRulebooks = ImportRulebooksCommandImpl(TestSessionExecution, store)
        val resolve = ResolveAttestationTypeCommandImpl(TestSessionExecution, store)
        val evaluate = EvaluateCatalogVerificationCommandImpl(TestSessionExecution, store)
    }

    private fun schema(uri: String = "https://example.test/vct/pid") =
        SchemaMeta(
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", uri)),
        )

    private fun documents() =
        listOf(
            AttestationSchemaDocument(
                kind = CatalogDocumentKind.RULEBOOK,
                mediaType = "text/markdown",
                bytes = "# rulebook".encodeToByteArray(),
            ),
            AttestationSchemaDocument(
                kind = CatalogDocumentKind.FORMAT,
                formatIdentifier = "dc+sd-jwt",
                mediaType = "application/json",
                bytes = """{"vct":"https://example.test/vct/pid"}""".encodeToByteArray(),
            ),
        )

    private class FakeLinkedTypeSource : CatalogLinkedTypeSource {
        override suspend fun resolve(
            designId: String?,
            vctId: String?
        ) = if (vctId == "EuPid") {
            Ok(
                LinkedTypeSnapshot(
                    vct = "/public/schema/vct/EuPid",
                    schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "/public/schema/vct/EuPid")),
                ),
            )
        } else {
            Ok(null)
        }
    }

    private class FixtureRemoteClient(
        private val pages: List<PaginatedSchemaList>,
        private val documents: Map<String, CatalogDocument>,
    ) : CatalogRemoteClient {
        override suspend fun listSchemas(
            baseUrl: String,
            limit: Int,
            offset: Int
        ): IdkResult<PaginatedSchemaList, IdkError> = Ok(pages.first())

        override suspend fun getSchema(
            baseUrl: String,
            schemaId: String
        ): IdkResult<SchemaMeta, IdkError> = Ok(pages.first().data.first { it.id == schemaId })

        override suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError> = Ok(documents.getValue(uri))
    }

    private class MutableRemoteClient(
        val schemas: MutableList<SchemaMeta>,
    ) : CatalogRemoteClient {
        override suspend fun listSchemas(
            baseUrl: String,
            limit: Int,
            offset: Int
        ): IdkResult<PaginatedSchemaList, IdkError> = Ok(PaginatedSchemaList(total = schemas.size, limit = limit, offset = offset, data = schemas.drop(offset).take(limit)))

        override suspend fun getSchema(
            baseUrl: String,
            schemaId: String
        ): IdkResult<SchemaMeta, IdkError> = Ok(schemas.first { it.id == schemaId })

        override suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError> =
            Ok(
                CatalogDocument(
                    mediaType = if (uri.contains("rulebook")) "text/markdown" else "application/json",
                    bytes =
                        if (uri.contains("rulebook")) {
                            "# rb".encodeToByteArray()
                        } else {
                            """{"vct":"$uri"}""".encodeToByteArray()
                        },
                ),
            )
    }

    private object UnusedUpdateSchema : com.sphereon.catalog.command.UpdateSchemaCommand {
        override val inputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<com.sphereon.catalog.command.UpdateSchemaArgs>()
        override val outputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<SchemaMeta>()
        override val isEnabled = true

        override suspend fun execute(args: com.sphereon.catalog.command.UpdateSchemaArgs) = error("unused")

        override suspend fun supports(args: Any) = false
    }

    private object UnusedDeleteSchema : com.sphereon.catalog.command.DeleteSchemaCommand {
        override val inputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<SchemaIdArgs>()
        override val outputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<Unit>()
        override val isEnabled = true

        override suspend fun execute(args: SchemaIdArgs) = error("unused")

        override suspend fun supports(args: Any) = false
    }

    private object UnusedImportRemote : com.sphereon.catalog.command.ImportRemoteCatalogCommand {
        override val inputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<ImportRemoteCatalogArgs>()
        override val outputTypeToken =
            com.sphereon.core.api.binary
                .typeToken<com.sphereon.catalog.model.CatalogImportReport>()
        override val isEnabled = true

        override suspend fun execute(args: ImportRemoteCatalogArgs) = error("unused")

        override suspend fun supports(args: Any) = false
    }

    private object TestSessionExecution : SessionExecution {
        override val tenantId: String = "acme"
        override val principalId: String = "operator"
        override val correlationId: String = "corr-catalog-path"
        override val sessionContextManager: SessionContextManager get() = error("unused")
        override val sessionContext: SessionContext = NoOpSessionContext
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
        override val conf: ContextConfig = NoOpContextConfig
    }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw IllegalStateException("unused")
        override val tenant: TenantConfigService get() = throw IllegalStateException("unused")
        override val principal: PrincipalConfigService get() = throw IllegalStateException("unused")

        override fun conf(level: ConfigLevel): ConfigService = throw IllegalStateException("unused")
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "catalog-path-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw IllegalStateException("unused")
    }
}
