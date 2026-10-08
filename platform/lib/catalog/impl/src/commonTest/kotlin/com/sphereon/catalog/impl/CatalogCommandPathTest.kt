/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.client.CatalogRemoteTrustScope
import com.sphereon.catalog.client.VerifiedRemoteBody
import com.sphereon.catalog.client.CatalogRemoteSignatureEvidence
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.catalog.model.TrustFrameworkType
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.impl.client.TrustAuthorityHintEgress
import com.sphereon.catalog.impl.client.TrustAuthorityHintResolver
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
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.publication.CatalogPublication
import com.sphereon.catalog.publication.CatalogPublicationListener
import com.sphereon.catalog.publication.CatalogSlugGuard
import com.sphereon.catalog.store.AttestationCatalogStore
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
import com.sphereon.catalog.model.CatalogTypeKeys
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.persistence.memory.InMemoryAttestationCatalogStore
import com.sphereon.core.api.Err
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
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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
    fun linkPersistsTheTypeIdentityAsFormatDocuments() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("typed-pid", "Typed")).value
            val sdJwt = env.link.execute(issuerLink(created.id, vct = "urn:eudi:pid:1")).value
            val mdoc = env.link.execute(issuerLink(created.id, doctype = "org.iso.18013.5.1.mDL")).value
            val stored = env.store.listSchemas("acme", created.id).value
            val sdJwtRecord = stored.single { it.schema.id == sdJwt.id }
            val mdocRecord = stored.single { it.schema.id == mdoc.id }
            assertEquals(
                "urn:eudi:pid:1",
                FormatDocumentValidator.vctValue(sdJwtRecord.documents.single { it.formatIdentifier == "dc+sd-jwt" }.bytes),
            )
            assertEquals(
                "org.iso.18013.5.1.mDL",
                FormatDocumentValidator.docTypeValue(mdocRecord.documents.single { it.formatIdentifier == "mso_mdoc" }.bytes),
            )
            assertEquals(AttestationTypeKey(AttestationTypeKeyKind.VCT, "urn:eudi:pid:1"), CatalogTypeKeys.of(sdJwtRecord))
            assertEquals(AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, "org.iso.18013.5.1.mDL"), CatalogTypeKeys.of(mdocRecord))
        }

    @Test
    fun linkedTypeKeepsItsIdentityAfterPublish() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("keep-mdl", "Keep")).value
            val linked = env.link.execute(issuerLink(created.id, doctype = "org.iso.18013.5.1.mDL")).value
            env.updateSchema
                .execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = linked.id!!,
                        schema = linked,
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.days),
                    ),
                ).value
            env.publish.execute(CatalogIdArgs(created.id)).value
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertEquals(AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, "org.iso.18013.5.1.mDL"), CatalogTypeKeys.of(record))
        }

    @Test
    fun linkedTypeIsListedOnceARulebookIsSuppliedAndKeepsItsFormatDocument() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("list-linked", "List")).value
            val linked = env.link.execute(issuerLink(created.id, vct = "urn:eudi:pid:1")).value
            val withoutRulebook =
                env.updateSchema.execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = linked.id!!,
                        schema = linked,
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.days),
                    ),
                )
            assertTrue(withoutRulebook.isErr)
            assertEquals("A RULEBOOK document is required for listed schemas", withoutRulebook.error.message.defaultMessage)
            val listed =
                env.updateSchema.execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = linked.id!!,
                        schema = linked,
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.days),
                    ),
                )
            assertTrue(listed.isOk, listed.toString())
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertEquals(setOf(CatalogDocumentKind.RULEBOOK, CatalogDocumentKind.FORMAT), record.documents.map { it.kind }.toSet())
            env.publish.execute(CatalogIdArgs(created.id)).value
            val publicList = env.listSchemas.execute(ListSchemasArgs(slug = "list-linked", publishedOnly = true, listedOnly = true))
            assertEquals(listOf(linked.id), publicList.value.data.map { it.id })
        }

    @Test
    fun importLinkWithOpenListingAndRulebookIsServedAtOnce() =
        runTest {
            // Regression: the admin console import linked without a listing window, so every entry
            // was stored with CatalogListingWindow.never and served only after a manual edit.
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("import-served", "Import")).value
            env.publish.execute(CatalogIdArgs(created.id)).value
            val vct = "https://acme.example/public/schema/vct/EuPidE2E-1"
            val linked =
                env.link.execute(
                    issuerLink(created.id, vct = vct).copy(
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow(start = Clock.System.now() - 1.minutes, end = null),
                    ),
                )
            assertTrue(linked.isOk, linked.toString())
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertFalse(record.listing.isEmpty())
            assertNull(record.listing.end)
            assertEquals(AttestationTypeKey(AttestationTypeKeyKind.VCT, vct), CatalogTypeKeys.of(record))
            val publicList = env.listSchemas.execute(ListSchemasArgs(slug = "import-served", publishedOnly = true, listedOnly = true))
            assertEquals(listOf(linked.value.id), publicList.value.data.map { it.id })
        }

    @Test
    fun suppliedRulebookKeepsTheLinkedTypeFormatDocument() =
        runTest {
            // Supplying a rulebook with a link must not drop the type metadata the linked source resolves.
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("hosted-meta", "Hosted")).value
            val linked =
                env.link.execute(
                    LinkSchemaArgs(
                        catalogId = created.id,
                        vctId = "EuPidMeta",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.minutes),
                    ),
                )
            assertTrue(linked.isOk, linked.toString())
            val record = env.store.listSchemas("acme", created.id).value.single()
            val format = record.documents.single { it.kind == CatalogDocumentKind.FORMAT }
            assertTrue(format.bytes.decodeToString().contains("Hosted PID"), format.bytes.decodeToString())
            assertEquals(1, record.documents.count { it.kind == CatalogDocumentKind.RULEBOOK })
        }

    @Test
    fun relinkListsAnEntryThatWasOnlyEverAMember() =
        runTest {
            // Entries imported before the fix are unlisted and have no rulebook. Importing again with a
            // listing and a rulebook lists them in place instead of returning them unchanged.
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("relink", "Relink")).value
            val first = env.link.execute(issuerLink(created.id, vct = "urn:eudi:pid:1")).value
            assertTrue(env.store.listSchemas("acme", created.id).value.single().listing.isEmpty())
            val again =
                env.link.execute(
                    issuerLink(created.id, vct = "urn:eudi:pid:1").copy(
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.minutes),
                    ),
                )
            assertTrue(again.isOk, again.toString())
            assertEquals(first.id, again.value.id)
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertTrue(record.listing.includes(Clock.System.now()))
            assertEquals(setOf(CatalogDocumentKind.RULEBOOK, CatalogDocumentKind.FORMAT), record.documents.map { it.kind }.toSet())
        }

    @Test
    fun relinkKeepsTheListingOfAnEntryThatHasARulebook() =
        runTest {
            // A withdrawn entry that was listed before (it has a rulebook) stays withdrawn on re-import.
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("withdrawn", "Withdrawn")).value
            val first = env.link.execute(issuerLink(created.id, vct = "urn:eudi:pid:1")).value
            env.updateSchema
                .execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = first.id!!,
                        schema = first,
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.never(Clock.System.now()),
                    ),
                ).value
            val again =
                env.link.execute(
                    issuerLink(created.id, vct = "urn:eudi:pid:1").copy(
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.minutes),
                    ),
                )
            assertTrue(again.isOk, again.toString())
            assertTrue(env.store.listSchemas("acme", created.id).value.single().listing.isEmpty())
        }

    @Test
    fun legacyLinkedRecordWithoutFormatDocumentIsBackfilledWhenListed() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("legacy-link", "Legacy")).value
            val legacy =
                SchemaMeta(
                    id = "22222222-2222-2222-2222-222222222222",
                    version = "1.0.0",
                    rulebookURI = "about:blank",
                    attestationLoS = "iso_18045_high",
                    bindingType = "key",
                    supportedFormats = listOf("mso_mdoc"),
                    schemaURIs = listOf(SchemaUriRef("mso_mdoc", "org.iso.18013.5.1.mDL")),
                )
            env.store.saveSchema(
                "acme",
                com.sphereon.catalog.model.AttestationSchemaRecord(
                    catalogId = created.id,
                    schema = legacy,
                    provenance = com.sphereon.catalog.model.CatalogSchemaProvenance.LINKED_DESIGN,
                    listing = CatalogListingWindow.never(Clock.System.now()),
                    documents = emptyList(),
                ),
            )
            assertEquals(
                AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, "org.iso.18013.5.1.mDL"),
                CatalogTypeKeys.of(env.store.listSchemas("acme", created.id).value.single()),
            )
            val listed =
                env.updateSchema.execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = legacy.id!!,
                        schema = legacy,
                        documents = listOf(rulebook()),
                        listing = CatalogListingWindow.open(Clock.System.now() - 1.days),
                    ),
                )
            assertTrue(listed.isOk, listed.toString())
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertEquals(
                "org.iso.18013.5.1.mDL",
                FormatDocumentValidator.docTypeValue(record.documents.single { it.formatIdentifier == "mso_mdoc" }.bytes),
            )
        }

    @Test
    fun linkedTypeIdentityCannotBeChanged() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("fixed-type", "Fixed")).value
            val linked = env.link.execute(issuerLink(created.id, vct = "urn:eudi:pid:1")).value
            val changed =
                env.updateSchema.execute(
                    UpdateSchemaArgs(
                        catalogId = created.id,
                        schemaId = linked.id!!,
                        schema = linked.copy(schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "urn:other:type"))),
                    ),
                )
            assertTrue(changed.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", changed.error.code)
            val record = env.store.listSchemas("acme", created.id).value.single()
            assertEquals(AttestationTypeKey(AttestationTypeKeyKind.VCT, "urn:eudi:pid:1"), CatalogTypeKeys.of(record))
        }

    @Test
    fun linkWithoutBoundVctSourceFailsClosed() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("nolink-pid", "NoLink")).value
            val linked =
                LinkSchemaCommandImpl(TestSessionExecution, env.store, DefaultCatalogLinkedTypeSource(), authorization = AllowAllCatalogAuthorization).execute(
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
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client, TrustAuthorityHintResolver(emptySet()), authorization = AllowAllCatalogAuthorization)
            val report = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1", "domain-a"))
            assertTrue(report.isOk)
            assertEquals(2, report.value.imported)
            val management = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = false))
            assertEquals(2, management.value.total)
            val listedOnly = env.listSchemas.execute(ListSchemasArgs(catalogId = created.id, listedOnly = true))
            assertEquals(1, listedOnly.value.total)
            val stored = env.store.listSchemas("acme", created.id).value
            assertTrue(stored!!.any { it.documents.any { doc -> doc.kind == CatalogDocumentKind.RULEBOOK } })
            val firstIds = stored.map { it.schema.id }.toSet()
            val again = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1", "domain-a"))
            assertTrue(again.isOk)
            val afterUpsert = env.store.listSchemas("acme", created.id).value!!
            assertEquals(firstIds, afterUpsert.map { it.schema.id }.toSet())
            assertEquals(2, afterUpsert.size)
        }

    @Test
    fun importRemoteRecordsSignatureDiagnosticsAndPassesTrustScope() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("imp-signed", "Signed")).value
            val client = ScopedRecordingClient(listOf(schema("https://example.test/vct/good").copy(id = "good")))
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client, TrustAuthorityHintResolver(emptySet()), authorization = AllowAllCatalogAuthorization)
            val report = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/api/v1", "domain-a"))
            assertTrue(report.isOk)
            assertEquals(listOf(CatalogRemoteTrustScope("domain-a", created.id)), client.scopes.distinct())
            val codes = report.value.diagnostics.map { it.code }
            assertTrue("catalog.remote.signature-verified" in codes, codes.toString())
        }

    @Test
    fun importRemoteFailsClosedWhenTheClientRejectsTheListing() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("imp-rejected", "Rejected")).value
            val client = ScopedRecordingClient(listOf(schema().copy(id = "good")), reject = true)
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client, TrustAuthorityHintResolver(emptySet()), authorization = AllowAllCatalogAuthorization)
            val report = importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/api/v1", "domain-a"))
            assertTrue(report.isErr)
            assertEquals(0, env.store.listSchemas("acme", created.id).value!!.size)
        }

    @Test
    fun importRemoteRequiresADomain() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("imp-nodomain", "No domain")).value
            val client = ScopedRecordingClient(emptyList())
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client, TrustAuthorityHintResolver(emptySet()), authorization = AllowAllCatalogAuthorization)
            assertTrue(importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/api/v1", " ")).isErr)
            assertTrue(client.scopes.isEmpty())
        }

    @Test
    fun trustAuthorityHintsAreDispatchedToTheMatchingIdentifierMethods() =
        runTest {
            val seen = mutableListOf<ExternalIdentifierOpts>()
            val resolver = TrustAuthorityHintResolver(setOf(RecordingIdentifierService(seen)), Provider { TrustAuthorityHintEgress { true } })
            val diagnostics =
                resolver.resolve(
                    listOf(
                        TrustAuthority(TrustFrameworkType.etsi_tl, "https://tl.example/tsl.xml"),
                        TrustAuthority(TrustFrameworkType.etsi_tl, "https://lote.example/lote.json", isLOTE = true),
                        TrustAuthority(TrustFrameworkType.openid_federation, "https://fed.example"),
                        TrustAuthority(TrustFrameworkType.aki, "c2tpLW9ubHk"),
                    ),
                )
            assertEquals(4, diagnostics.size)
            assertEquals(listOf("etsi_tsl", "etsi_tsl", "ENTITY_ID"), seen.map { it.method?.methodName })
            assertTrue(diagnostics.all { it.code == TrustAuthorityHintResolver.UNRESOLVED }, diagnostics.toString())
            assertTrue(diagnostics[1].message.contains("LoTE"))
            val none =
                TrustAuthorityHintResolver(emptySet(), Provider { TrustAuthorityHintEgress { true } }).resolve(
                    listOf(TrustAuthority(TrustFrameworkType.etsi_tl, "https://x.example")),
                )
            assertEquals(TrustAuthorityHintResolver.UNSUPPORTED, none.single().code)
        }

    @Test
    fun trustAuthorityHintUrlsAreNotFetchedWithoutAnEgressPolicy() =
        runTest {
            val seen = mutableListOf<ExternalIdentifierOpts>()
            val hints =
                listOf(
                    TrustAuthority(TrustFrameworkType.etsi_tl, "http://169.254.169.254/latest/meta-data"),
                    TrustAuthority(TrustFrameworkType.openid_federation, "https://localhost:8443"),
                )
            val denied = TrustAuthorityHintResolver(setOf(RecordingIdentifierService(seen))).resolve(hints)
            assertTrue(seen.isEmpty(), "no request may be made for a hint URL of an unsigned listing")
            assertTrue(denied.all { it.code == TrustAuthorityHintResolver.SKIPPED }, denied.toString())

            val refusing = TrustAuthorityHintResolver(setOf(RecordingIdentifierService(seen)), Provider { TrustAuthorityHintEgress { false } }).resolve(hints)
            assertTrue(seen.isEmpty())
            assertTrue(refusing.all { it.code == TrustAuthorityHintResolver.SKIPPED })
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
            val importer = ImportRemoteCatalogCommandImpl(TestSessionExecution, env.store, client, TrustAuthorityHintResolver(emptySet()), authorization = AllowAllCatalogAuthorization)
            importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1", "domain-a"))
            client.schemas.removeAll { it.id == "drop" }
            importer.execute(ImportRemoteCatalogArgs(created.id, "https://remote.test/public/catalogs/x/api/v1", "domain-a"))
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

    @Test
    fun aPublicationListenerSeesTheHostedRecordsBeforeTheCatalogIsPublishedAndFailsThePublication() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("listened", "Listened")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))

            val failing = RecordingPublicationListener(fail = true)
            val refused = PublishCatalogCommandImpl(TestSessionExecution, env.store, failing, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id))
            assertTrue(refused.isErr)
            assertEquals(AttestationCatalogStatus.DRAFT, env.store.findCatalogById("acme", created.id).value?.status, "a failing listener leaves the catalog a draft")

            val listener = RecordingPublicationListener()
            val published = PublishCatalogCommandImpl(TestSessionExecution, env.store, listener, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id)).value
            assertEquals(AttestationCatalogStatus.PUBLISHED, published.status)
            val seen = listener.published.single()
            assertEquals("listened", seen.catalog.slug)
            assertEquals("/public/catalogs/listened", seen.publicUrl)
            assertTrue(seen.records.single().schema.rulebookURI.contains("/public/catalogs/listened/"), "the listener gets the hosted URIs")

            DisableCatalogCommandImpl(TestSessionExecution, env.store, listener, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id)).value
            assertEquals(listOf("listened"), listener.disabled.map { it.slug })
        }

    @Test
    fun aSlugGuardCanRefuseTheSlugOfANewOrRenamedCatalog() =
        runTest {
            val env = Env()
            val asked = mutableListOf<Triple<String, String, String>>()
            val guard =
                CatalogSlugGuard { tenantId, slug, catalogId ->
                    asked += Triple(tenantId, slug, catalogId)
                    if (slug.startsWith("held-")) Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "held by another catalogue")) else Ok(Unit)
                }
            val create = CreateCatalogCommandImpl(TestSessionExecution, env.store, guard, authorization = AllowAllCatalogAuthorization)
            val update = UpdateCatalogCommandImpl(TestSessionExecution, env.store, guard, authorization = AllowAllCatalogAuthorization)

            assertTrue(create.execute(CreateCatalogArgs("held-by-authored", "Refused")).isErr)
            assertTrue(env.store.findCatalogBySlug("acme", "held-by-authored").value == null, "a refused slug creates nothing")

            val created = create.execute(CreateCatalogArgs("free-slug", "Free")).value
            assertEquals("free-slug", created.slug)
            assertEquals(created.id, asked.last().third, "the guard learns which catalog wants the slug")

            val renamed = update.execute(UpdateCatalogArgs(created.id, "Free", null, false, slug = "held-now"))
            assertTrue(renamed.isErr)
            assertEquals("free-slug", env.store.findCatalogById("acme", created.id).value?.slug)
        }

    /** A store that refuses to save a catalog in the given status; everything else goes to the in-memory store. */
    private class StatusRefusingStore(
        private val delegate: InMemoryAttestationCatalogStore,
        private val refuse: AttestationCatalogStatus,
    ) : AttestationCatalogStore by delegate {
        override suspend fun saveCatalog(
            tenantId: String,
            catalog: AttestationCatalog,
        ): IdkResult<AttestationCatalog, IdkError> =
            if (catalog.status == refuse) Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "store refused")) else delegate.saveCatalog(tenantId, catalog)
    }

    @Test
    fun aListenerFailureDuringPublishCompensatesSoNoRepresentationStaysServed() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("compensated", "Compensated")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))
            val listener = RecordingPublicationListener(fail = true)

            val refused = PublishCatalogCommandImpl(TestSessionExecution, env.store, listener, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id))

            assertTrue(refused.isErr)
            assertEquals(listOf("compensated"), listener.disabled.map { it.slug }, "the listener is told to undo whatever it stored")
            assertEquals(AttestationCatalogStatus.DRAFT, env.store.findCatalogById("acme", created.id).value?.status)
        }

    @Test
    fun aFailedCatalogSaveAfterTheListenerSucceededCompensatesTheListener() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("save-fails", "Save fails")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))
            val listener = RecordingPublicationListener()
            val store = StatusRefusingStore(env.store, AttestationCatalogStatus.PUBLISHED)

            val refused = PublishCatalogCommandImpl(TestSessionExecution, store, listener, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id))

            assertTrue(refused.isErr)
            assertEquals(1, listener.published.size)
            assertEquals(listOf("save-fails"), listener.disabled.map { it.slug }, "the CoS representation is not left served for a catalog that is still a draft")
            assertEquals(AttestationCatalogStatus.DRAFT, env.store.findCatalogById("acme", created.id).value?.status)
        }

    @Test
    fun aListenerFailureDuringDisableKeepsTheCatalogPublished() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("stays-up", "Stays up")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))
            env.publish.execute(CatalogIdArgs(created.id)).value
            val failing =
                object : CatalogPublicationListener {
                    override suspend fun published(publication: CatalogPublication): IdkResult<Unit, IdkError> = Ok(Unit)

                    override suspend fun disabled(catalog: AttestationCatalog): IdkResult<Unit, IdkError> =
                        Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "cannot stop serving"))
                }

            val refused = DisableCatalogCommandImpl(TestSessionExecution, env.store, failing, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id))

            assertTrue(refused.isErr)
            assertEquals(AttestationCatalogStatus.PUBLISHED, env.store.findCatalogById("acme", created.id).value?.status, "still served, so still PUBLISHED")
        }

    @Test
    fun aDisabledCatalogCanBePublishedAgainButAPublishedOneCannot() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("republished", "Republished")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))
            val listener = RecordingPublicationListener()
            val publish = PublishCatalogCommandImpl(TestSessionExecution, env.store, listener, authorization = AllowAllCatalogAuthorization)
            val disable = DisableCatalogCommandImpl(TestSessionExecution, env.store, listener, authorization = AllowAllCatalogAuthorization)

            val first = publish.execute(CatalogIdArgs(created.id)).value
            assertEquals(AttestationCatalogStatus.PUBLISHED, first.status)
            assertTrue(publish.execute(CatalogIdArgs(created.id)).isErr, "a published catalog is not published twice")

            assertEquals(AttestationCatalogStatus.DISABLED, disable.execute(CatalogIdArgs(created.id)).value.status)
            val again = publish.execute(CatalogIdArgs(created.id)).value

            assertEquals(AttestationCatalogStatus.PUBLISHED, again.status)
            assertTrue(again.version > first.version)
            assertEquals(2, listener.published.size, "the publication listener builds the representations again")
            assertEquals("republished", listener.published.last().catalog.slug)
        }

    @Test
    fun aFailedRepublishLeavesTheCatalogDisabledAndUndoesTheListener() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("stays-off", "Stays off")).value
            env.createSchema.execute(CreateSchemaArgs(created.id, schema("https://example.test/vct/pid"), documents()))
            env.publish.execute(CatalogIdArgs(created.id)).value
            env.disable.execute(CatalogIdArgs(created.id)).value
            val failing = RecordingPublicationListener(fail = true)

            val refused = PublishCatalogCommandImpl(TestSessionExecution, env.store, failing, authorization = AllowAllCatalogAuthorization).execute(CatalogIdArgs(created.id))

            assertTrue(refused.isErr)
            assertEquals(AttestationCatalogStatus.DISABLED, env.store.findCatalogById("acme", created.id).value?.status)
            assertEquals(listOf("stays-off"), failing.disabled.map { it.slug })
        }

    private class RecordingPublicationListener(
        private val fail: Boolean = false,
    ) : CatalogPublicationListener {
        val published = mutableListOf<CatalogPublication>()
        val disabled = mutableListOf<AttestationCatalog>()

        override suspend fun published(publication: CatalogPublication): IdkResult<Unit, IdkError> {
            published += publication
            return if (fail) Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "listener refused")) else Ok(Unit)
        }

        override suspend fun disabled(catalog: AttestationCatalog): IdkResult<Unit, IdkError> {
            disabled += catalog
            return Ok(Unit)
        }
    }

    private class Env {
        val store = InMemoryAttestationCatalogStore()
        val createCatalog = CreateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val updateCatalog = UpdateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val createSchema = CreateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val updateSchema = UpdateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val publish = PublishCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val disable = DisableCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
        val listSchemas = ListSchemasCommandImpl(TestSessionExecution, store)
        val getSchema = GetSchemaCommandImpl(TestSessionExecution, store)
        val link = LinkSchemaCommandImpl(TestSessionExecution, store, FakeLinkedTypeSource(), authorization = AllowAllCatalogAuthorization)
        val importRulebooks = ImportRulebooksCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)
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

    /** A link as the admin console's issuer import sends it: the type named explicitly, no design. */
    private fun issuerLink(
        catalogId: String,
        vct: String? = null,
        doctype: String? = null,
    ): LinkSchemaArgs {
        val format = if (doctype != null) "mso_mdoc" else "dc+sd-jwt"
        return LinkSchemaArgs(
            catalogId = catalogId,
            vct = vct,
            doctype = doctype,
            version = "1.0.0",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf(format),
            schemaURIs = listOf(SchemaUriRef(format, vct ?: doctype!!)),
        )
    }

    private fun rulebook() =
        AttestationSchemaDocument(
            kind = CatalogDocumentKind.RULEBOOK,
            mediaType = "text/markdown",
            bytes = "# rulebook".encodeToByteArray(),
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
        ) = if (vctId == "EuPidMeta") {
            Ok(
                LinkedTypeSnapshot(
                    vct = "https://acme.example/public/schema/vct/EuPid",
                    schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://acme.example/public/schema/vct/EuPid")),
                    documents =
                        listOf(
                            AttestationSchemaDocument(
                                kind = CatalogDocumentKind.FORMAT,
                                formatIdentifier = "dc+sd-jwt",
                                mediaType = "application/json",
                                bytes = """{"vct":"https://acme.example/public/schema/vct/EuPid","name":"Hosted PID"}""".encodeToByteArray(),
                            ),
                        ),
                ),
            )
        } else if (vctId == "EuPid") {
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

        override suspend fun listSchemasScoped(
            baseUrl: String,
            limit: Int,
            offset: Int,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<PaginatedSchemaList>, IdkError> =
            listSchemas(baseUrl, limit, offset).map { VerifiedRemoteBody(it, null) }

        override suspend fun getSchemaScoped(
            baseUrl: String,
            schemaId: String,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<SchemaMeta>, IdkError> =
            getSchema(baseUrl, schemaId).map { VerifiedRemoteBody(it, null) }
    }

    private class ScopedRecordingClient(
        private val schemas: List<SchemaMeta>,
        private val reject: Boolean = false,
    ) : CatalogRemoteClient {
        val scopes = mutableListOf<CatalogRemoteTrustScope>()
        private val evidence = CatalogRemoteSignatureEvidence("https://remote.test", "kid-1", "ES256")

        override suspend fun listSchemas(baseUrl: String, limit: Int, offset: Int): IdkResult<PaginatedSchemaList, IdkError> = error("unscoped fetch must not be used")

        override suspend fun getSchema(baseUrl: String, schemaId: String): IdkResult<SchemaMeta, IdkError> = error("unscoped fetch must not be used")

        override suspend fun listSchemasScoped(
            baseUrl: String,
            limit: Int,
            offset: Int,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<PaginatedSchemaList>, IdkError> {
            scopes += trust
            if (reject) return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "signature not trusted"))
            return Ok(
                VerifiedRemoteBody(
                    PaginatedSchemaList(schemas.size, limit, offset, schemas.drop(offset).take(limit)),
                    evidence,
                ),
            )
        }

        override suspend fun getSchemaScoped(
            baseUrl: String,
            schemaId: String,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<SchemaMeta>, IdkError> {
            scopes += trust
            return Ok(VerifiedRemoteBody(schemas.first { it.id == schemaId }, evidence))
        }

        override suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError> =
            Ok(CatalogDocument("application/json", "{\"vct\":\"$uri\"}".encodeToByteArray()))
    }

    private class RecordingIdentifierService(
        private val seen: MutableList<ExternalIdentifierOpts>,
    ) : ExternalIdentifierService {
        override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()

        override suspend fun isSupportedIdentifier(identifier: Any): Boolean = true

        override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = true

        override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = true

        override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult) = Ok(opts as ExternalIdentifierOpts)

        override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> {
            seen += opts as ExternalIdentifierOpts
            return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "offline"))
        }
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

        override suspend fun listSchemasScoped(
            baseUrl: String,
            limit: Int,
            offset: Int,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<PaginatedSchemaList>, IdkError> =
            listSchemas(baseUrl, limit, offset).map { VerifiedRemoteBody(it, null) }

        override suspend fun getSchemaScoped(
            baseUrl: String,
            schemaId: String,
            trust: CatalogRemoteTrustScope,
        ): IdkResult<VerifiedRemoteBody<SchemaMeta>, IdkError> =
            getSchema(baseUrl, schemaId).map { VerifiedRemoteBody(it, null) }

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
