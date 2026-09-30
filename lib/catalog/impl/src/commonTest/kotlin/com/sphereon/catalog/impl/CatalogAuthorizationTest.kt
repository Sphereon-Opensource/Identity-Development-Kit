/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.authorization.CatalogAuthorization
import com.sphereon.catalog.authorization.CatalogPermissionIds
import com.sphereon.catalog.authorization.DenyAllCatalogAuthorization
import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.LinkedTypeSnapshot
import com.sphereon.catalog.command.CatalogIdArgs
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.ImportRemoteCatalogArgs
import com.sphereon.catalog.command.ImportRulebooksArgs
import com.sphereon.catalog.command.LinkSchemaArgs
import com.sphereon.catalog.command.ListCatalogsArgs
import com.sphereon.catalog.command.ListSchemasArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.command.UpdateCatalogArgs
import com.sphereon.catalog.command.UpdateSchemaArgs
import com.sphereon.catalog.impl.client.TrustAuthorityHintResolver
import com.sphereon.catalog.impl.client.UnimplementedCatalogRemoteClient
import com.sphereon.catalog.impl.command.CreateCatalogCommandImpl
import com.sphereon.catalog.impl.command.CreateSchemaCommandImpl
import com.sphereon.catalog.impl.command.DeleteSchemaCommandImpl
import com.sphereon.catalog.impl.command.DisableCatalogCommandImpl
import com.sphereon.catalog.impl.command.GetCatalogCommandImpl
import com.sphereon.catalog.impl.command.GetSchemaCommandImpl
import com.sphereon.catalog.impl.command.ImportRemoteCatalogCommandImpl
import com.sphereon.catalog.impl.command.ImportRulebooksCommandImpl
import com.sphereon.catalog.impl.command.LinkSchemaCommandImpl
import com.sphereon.catalog.impl.command.ListCatalogsCommandImpl
import com.sphereon.catalog.impl.command.ListSchemasCommandImpl
import com.sphereon.catalog.impl.command.PublishCatalogCommandImpl
import com.sphereon.catalog.impl.command.UpdateCatalogCommandImpl
import com.sphereon.catalog.impl.command.UpdateSchemaCommandImpl
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
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

/**
 * The attestation catalog write commands carry their own permission check: `catalog.manage` for create, update,
 * delete, link and import, `catalog.publish` for publish and disable. Reads never consult the boundary.
 */
class CatalogAuthorizationTest {
    private class Recording(
        private val granted: Set<String>,
    ) : CatalogAuthorization {
        val asked = mutableListOf<String>()

        override suspend fun authorize(
            execution: SessionExecution,
            permission: String,
            resourceId: String?,
        ): IdkResult<Unit, IdkError> {
            asked += permission
            return if (permission in granted) Ok(Unit) else DenyAllCatalogAuthorization.authorize(execution, permission, resourceId)
        }
    }

    private fun manageOnly() = Recording(setOf(CatalogPermissionIds.MANAGE))

    private fun publishOnly() = Recording(setOf(CatalogPermissionIds.PUBLISH))

    @Test
    fun writesAreDeniedByDefault() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val denied = CreateCatalogCommandImpl(TestExecution, store).execute(CreateCatalogArgs("denied", "Denied"))
            assertTrue(denied.isErr)
            assertTrue(store.listCatalogs("acme", null, null).value!!.isEmpty())
        }

    @Test
    fun manageCoversEveryWriteExceptPublishAndDisable() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val boundary = manageOnly()
            val create = CreateCatalogCommandImpl(TestExecution, store, authorization = boundary)
            val update = UpdateCatalogCommandImpl(TestExecution, store, authorization = boundary)
            val createSchema = CreateSchemaCommandImpl(TestExecution, store, authorization = boundary)
            val updateSchema = UpdateSchemaCommandImpl(TestExecution, store, authorization = boundary)
            val deleteSchema = DeleteSchemaCommandImpl(TestExecution, store, authorization = boundary)
            val link = LinkSchemaCommandImpl(TestExecution, store, NoLinkedTypes, authorization = boundary)
            val importRulebooks = ImportRulebooksCommandImpl(TestExecution, store, authorization = boundary)
            val importRemote =
                ImportRemoteCatalogCommandImpl(
                    TestExecution,
                    store,
                    UnimplementedCatalogRemoteClient(),
                    TrustAuthorityHintResolver(emptySet()),
                    authorization = boundary,
                )

            val catalog = create.execute(CreateCatalogArgs("managed", "Managed"))
            assertTrue(catalog.isOk)
            val id = catalog.value.id
            assertTrue(update.execute(UpdateCatalogArgs(id, "Managed 2", null, true)).isOk)
            val schema = createSchema.execute(CreateSchemaArgs(id, schema(), documents()))
            assertTrue(schema.isOk)
            assertTrue(updateSchema.execute(UpdateSchemaArgs(id, schema.value.id!!, schema(), null)).isOk)
            assertTrue(deleteSchema.execute(SchemaIdArgs(catalogId = id, schemaId = schema.value.id!!)).isOk)
            // These may fail on their own validation; what matters is that each one reached the boundary with catalog.manage.
            link.execute(LinkSchemaArgs(id, version = "1", attestationLoS = "iso_18045_high", bindingType = "key", supportedFormats = emptyList()))
            importRulebooks.execute(ImportRulebooksArgs(id, emptyMap()))
            importRemote.execute(ImportRemoteCatalogArgs(id, "https://registry.test", "domain"))

            assertEquals(setOf(CatalogPermissionIds.MANAGE), boundary.asked.toSet())
            assertEquals(8, boundary.asked.size)
        }

    @Test
    fun everyManageWriteIsRefusedWithoutThePermission() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val seeded = CreateCatalogCommandImpl(TestExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("seed", "Seed")).value
            val schema = CreateSchemaCommandImpl(TestExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateSchemaArgs(seeded.id, schema(), documents())).value

            val boundary = publishOnly()
            assertTrue(UpdateCatalogCommandImpl(TestExecution, store, authorization = boundary).execute(UpdateCatalogArgs(seeded.id, "Changed", null, false)).isErr)
            assertTrue(CreateSchemaCommandImpl(TestExecution, store, authorization = boundary).execute(CreateSchemaArgs(seeded.id, schema(), emptyList())).isErr)
            assertTrue(UpdateSchemaCommandImpl(TestExecution, store, authorization = boundary).execute(UpdateSchemaArgs(seeded.id, schema.id!!, schema(), null)).isErr)
            assertTrue(DeleteSchemaCommandImpl(TestExecution, store, authorization = boundary).execute(SchemaIdArgs(catalogId = seeded.id, schemaId = schema.id!!)).isErr)
            assertTrue(
                LinkSchemaCommandImpl(TestExecution, store, NoLinkedTypes, authorization = boundary)
                    .execute(LinkSchemaArgs(seeded.id, vct = "https://example.test/vct/x", version = "1", attestationLoS = "iso_18045_high", bindingType = "key", supportedFormats = listOf("dc+sd-jwt")))
                    .isErr,
            )
            assertTrue(ImportRulebooksCommandImpl(TestExecution, store, authorization = boundary).execute(ImportRulebooksArgs(seeded.id, emptyMap())).isErr)
            assertTrue(
                ImportRemoteCatalogCommandImpl(TestExecution, store, UnimplementedCatalogRemoteClient(), TrustAuthorityHintResolver(emptySet()), authorization = boundary)
                    .execute(ImportRemoteCatalogArgs(seeded.id, "https://registry.test", "domain"))
                    .isErr,
            )
            assertEquals("Seed", store.findCatalogById("acme", seeded.id).value!!.displayName)
            assertEquals(1, store.listSchemas("acme", seeded.id).value!!.size)
        }

    @Test
    fun publishAndDisableNeedThePublishPermission() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val created = CreateCatalogCommandImpl(TestExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("pub", "Pub")).value

            val manageBoundary = manageOnly()
            assertTrue(PublishCatalogCommandImpl(TestExecution, store, authorization = manageBoundary).execute(CatalogIdArgs(created.id)).isErr)
            assertTrue(DisableCatalogCommandImpl(TestExecution, store, authorization = manageBoundary).execute(CatalogIdArgs(created.id)).isErr)
            assertEquals(AttestationCatalogStatus.DRAFT, store.findCatalogById("acme", created.id).value?.status)

            val publishBoundary = publishOnly()
            val published = PublishCatalogCommandImpl(TestExecution, store, authorization = publishBoundary).execute(CatalogIdArgs(created.id))
            assertTrue(published.isOk)
            assertEquals(AttestationCatalogStatus.PUBLISHED, published.value.status)
            assertTrue(DisableCatalogCommandImpl(TestExecution, store, authorization = publishBoundary).execute(CatalogIdArgs(created.id)).isOk)
            assertEquals(setOf(CatalogPermissionIds.PUBLISH), publishBoundary.asked.toSet())

            // publish alone does not grant authoring
            assertTrue(CreateCatalogCommandImpl(TestExecution, store, authorization = publishBoundary).execute(CreateCatalogArgs("nope", "Nope")).isErr)
        }

    @Test
    fun readsNeverConsultTheBoundary() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val created = CreateCatalogCommandImpl(TestExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("reads", "Reads")).value

            assertTrue(ListCatalogsCommandImpl(TestExecution, store).execute(ListCatalogsArgs()).isOk)
            assertTrue(GetCatalogCommandImpl(TestExecution, store).execute(CatalogIdArgs(created.id)).isOk)
            assertTrue(ListSchemasCommandImpl(TestExecution, store).execute(ListSchemasArgs(catalogId = created.id)).isOk)
            assertTrue(GetSchemaCommandImpl(TestExecution, store).execute(SchemaIdArgs(catalogId = created.id, schemaId = "missing")).isErr)
        }

    private fun documents() =
        listOf(
            AttestationSchemaDocument(kind = CatalogDocumentKind.RULEBOOK, mediaType = "text/markdown", bytes = "# rulebook".encodeToByteArray()),
            AttestationSchemaDocument(
                kind = CatalogDocumentKind.FORMAT,
                formatIdentifier = "dc+sd-jwt",
                mediaType = "application/json",
                bytes = """{"vct":"https://example.test/vct/pid"}""".encodeToByteArray(),
            ),
        )

    private fun schema() =
        SchemaMeta(
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/vct/pid")),
        )

    private object NoLinkedTypes : CatalogLinkedTypeSource {
        override suspend fun resolve(
            designId: String?,
            vctId: String?,
        ): IdkResult<LinkedTypeSnapshot?, IdkError> = Ok(null)
    }

    private object TestExecution : SessionExecution {
        override val tenantId: String = "acme"
        override val principalId: String = "operator"
        override val correlationId: String = "corr-catalog-authorization"
        override val sessionContextManager: SessionContextManager get() = error("unused")
        override val sessionContext: SessionContext = NoOpSessionContext
        override val log: SessionLogService = SilentLog(sessionContext)
        override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
        override val conf: ContextConfig = UnusedConfig
    }

    private object UnusedConfig : ContextConfig {
        override val app: AppConfigService get() = throw IllegalStateException("unused")
        override val tenant: TenantConfigService get() = throw IllegalStateException("unused")
        override val principal: PrincipalConfigService get() = throw IllegalStateException("unused")

        override fun conf(level: ConfigLevel): ConfigService = throw IllegalStateException("unused")
    }

    private class SilentLog(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "catalog-authorization-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw IllegalStateException("unused")
    }
}
