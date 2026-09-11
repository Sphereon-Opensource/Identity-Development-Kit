/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.command.ListSchemasArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.impl.command.GetSchemaCommandImpl
import com.sphereon.catalog.impl.command.ListSchemasCommandImpl
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogSchemaProvenance
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

class CatalogSchemasQueryTest {
    @Test
    fun listFiltersByTs11QueryParamsAndPaginates() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            store.saveCatalog("acme", published("pub", "webuild-pid"))
            store.saveSchema(
                "acme",
                schemaRecord(
                    catalogId = "pub",
                    id = "schema-sdjwt",
                    format = "dc+sd-jwt",
                    los = "iso_18045_high",
                    binding = "key",
                    schemaUri = "https://example.test/vct/pid",
                    rulebookUri = "https://example.test/rb/pid",
                    authority = TrustAuthority(TrustFrameworkType.etsi_tl, "EU"),
                ),
            )
            store.saveSchema(
                "acme",
                schemaRecord(
                    catalogId = "pub",
                    id = "schema-mdoc",
                    format = "mso_mdoc",
                    los = "iso_18045_moderate",
                    binding = "claim",
                    schemaUri = "https://example.test/doctype/mdl",
                    rulebookUri = "https://example.test/rb/mdl",
                    authority = TrustAuthority(TrustFrameworkType.aki, "abc"),
                ),
            )
            val list = ListSchemasCommandImpl(TestSessionExecution, store)

            val byFormat =
                list.execute(
                    ListSchemasArgs(slug = "webuild-pid", supportedFormats = listOf("dc+sd-jwt")),
                )
            assertTrue(byFormat.isOk)
            assertEquals(listOf("schema-sdjwt"), byFormat.value.data.map { it.id })

            val byLoS =
                list.execute(ListSchemasArgs(slug = "webuild-pid", attestationLoS = "iso_18045_moderate"))
            assertEquals(listOf("schema-mdoc"), byLoS.value.data.map { it.id })

            val byBinding = list.execute(ListSchemasArgs(slug = "webuild-pid", bindingType = "key"))
            assertEquals(listOf("schema-sdjwt"), byBinding.value.data.map { it.id })

            val byAuthority =
                list.execute(
                    ListSchemasArgs(
                        slug = "webuild-pid",
                        trustedAuthoritiesFrameworkType = "etsi_tl",
                        trustedAuthoritiesValue = "EU",
                    ),
                )
            assertEquals(listOf("schema-sdjwt"), byAuthority.value.data.map { it.id })

            val byUri =
                list.execute(
                    ListSchemasArgs(
                        slug = "webuild-pid",
                        schemaUri = "https://example.test/doctype/mdl",
                        rulebookUri = "https://example.test/rb/mdl",
                    ),
                )
            assertEquals(listOf("schema-mdoc"), byUri.value.data.map { it.id })

            val byLinkedVct =
                list.execute(ListSchemasArgs(slug = "webuild-pid", linkedVctId = "EuPid"))
            assertEquals(listOf("schema-sdjwt"), byLinkedVct.value.data.map { it.id })

            val page =
                list.execute(ListSchemasArgs(slug = "webuild-pid", publishedOnly = true, limit = 1, offset = 1))
            assertEquals(2, page.value.total)
            assertEquals(1, page.value.data.size)
            assertEquals(
                "schema-mdoc",
                page.value.data
                    .single()
                    .id
            )
        }

    @Test
    fun publicLookupReturnsNotFoundForDraftSlug() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            store.saveCatalog("acme", published("draft-1", "draft-pid").copy(status = AttestationCatalogStatus.DRAFT))
            store.saveSchema(
                "acme",
                schemaRecord(
                    catalogId = "draft-1",
                    id = "schema-draft",
                    format = "dc+sd-jwt",
                    los = "iso_18045_high",
                    binding = "key",
                    schemaUri = "https://example.test/vct/draft",
                    rulebookUri = "https://example.test/rb/draft",
                ),
            )
            val list = ListSchemasCommandImpl(TestSessionExecution, store)
            val get = GetSchemaCommandImpl(TestSessionExecution, store)

            val listed = list.execute(ListSchemasArgs(slug = "draft-pid", publishedOnly = true))
            assertTrue(listed.isErr)
            assertEquals("NOT_FOUND_ERROR", listed.error.code)

            val management = list.execute(ListSchemasArgs(slug = "draft-pid"))
            assertTrue(management.isOk)
            assertEquals(1, management.value.total)

            val fetched =
                get.execute(SchemaIdArgs(slug = "draft-pid", schemaId = "schema-draft", publishedOnly = true))
            assertTrue(fetched.isErr)
            assertEquals("NOT_FOUND_ERROR", fetched.error.code)
        }

    @Test
    fun listedOnlyFalseIncludesUnlistedRows() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            store.saveCatalog("acme", published("pub", "webuild-pid"))
            store.saveSchema(
                "acme",
                schemaRecord(
                    catalogId = "pub",
                    id = "schema-listed",
                    format = "dc+sd-jwt",
                    los = "iso_18045_high",
                    binding = "key",
                    schemaUri = "https://example.test/vct/listed",
                    rulebookUri = "https://example.test/rb/listed",
                ),
            )
            store.saveSchema(
                "acme",
                schemaRecord(
                    catalogId = "pub",
                    id = "schema-unlisted",
                    format = "dc+sd-jwt",
                    los = "iso_18045_high",
                    binding = "key",
                    schemaUri = "https://example.test/vct/unlisted",
                    rulebookUri = "https://example.test/rb/unlisted",
                ).copy(
                    listing =
                        com.sphereon.catalog.model.CatalogListingWindow
                            .never(
                                kotlin.time.Clock.System
                                    .now()
                            )
                ),
            )
            val list = ListSchemasCommandImpl(TestSessionExecution, store)
            val listedOnly = list.execute(ListSchemasArgs(slug = "webuild-pid", listedOnly = true))
            assertEquals(listOf("schema-listed"), listedOnly.value.data.map { it.id })
            val all = list.execute(ListSchemasArgs(slug = "webuild-pid", listedOnly = false))
            assertEquals(2, all.value.total)
        }

    private fun published(
        id: String,
        slug: String
    ) = AttestationCatalog(
        id = id,
        slug = slug,
        displayName = slug,
        status = AttestationCatalogStatus.PUBLISHED,
    )

    private fun schemaRecord(
        catalogId: String,
        id: String,
        format: String,
        los: String,
        binding: String,
        schemaUri: String,
        rulebookUri: String,
        authority: TrustAuthority? = null,
        linkedVctId: String? = if (id == "schema-sdjwt") "EuPid" else null,
    ) = AttestationSchemaRecord(
        catalogId = catalogId,
        schema =
            SchemaMeta(
                id = id,
                version = "1.0.0",
                rulebookURI = rulebookUri,
                trustedAuthorities = listOfNotNull(authority),
                attestationLoS = los,
                bindingType = binding,
                supportedFormats = listOf(format),
                schemaURIs = listOf(SchemaUriRef(format, schemaUri)),
            ),
        provenance = CatalogSchemaProvenance.AUTHORED,
        linkedVctId = linkedVctId,
    )

    private object TestSessionExecution : SessionExecution {
        override val tenantId: String = "acme"
        override val principalId: String = "anonymous"
        override val correlationId: String = "corr-catalog-query"
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
        override val id: String = "catalog-query-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw IllegalStateException("unused")
    }
}
