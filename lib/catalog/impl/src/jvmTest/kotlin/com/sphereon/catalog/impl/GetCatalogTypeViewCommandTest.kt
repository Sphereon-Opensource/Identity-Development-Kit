/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.command.CatalogIdArgs
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.impl.client.NoOpIssuerBindingLookup
import com.sphereon.catalog.impl.command.CreateCatalogCommandImpl
import com.sphereon.catalog.impl.command.CreateSchemaCommandImpl
import com.sphereon.catalog.impl.command.GetCatalogTypeViewCommandImpl
import com.sphereon.catalog.impl.command.PublishCatalogCommandImpl
import com.sphereon.catalog.impl.command.ResolveAttestationTypeCommandImpl
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogCardSource
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetCatalogTypeViewCommandTest {
    @Test
    fun viewCommandReturnsCardForVctFixtureAndNoneForMdocOnly() =
        runTest {
            val env = Env()
            val created = env.createCatalog.execute(CreateCatalogArgs("wallet-pid", "Wallet PID"))
            assertTrue(created.isOk, created.toString())
            val catalog = created.value
            val vct =
                env.createSchema.execute(
                    CreateSchemaArgs(
                        catalog.id,
                        schema(listOf("dc+sd-jwt"), "https://example.test/urn:eudi:pid:1"),
                        documents("dc+sd-jwt", fixture("webuild-consortium/pid.vctm.json")),
                    ),
                )
            assertTrue(vct.isOk, vct.toString())
            val mdoc =
                env.createSchema.execute(
                    CreateSchemaArgs(
                        catalog.id,
                        schema(listOf("mso_mdoc"), "https://example.test/eu.europa.ec.eudi.pid.1"),
                        documents("mso_mdoc", fixture("webuild-consortium/pid.mdoc.json")),
                    ),
                )
            assertTrue(mdoc.isOk, mdoc.toString())
            val published = env.publish.execute(CatalogIdArgs(catalog.id))
            assertTrue(published.isOk, published.toString())

            val vctViewResult =
                env.viewType.execute(
                    SchemaIdArgs(catalogId = catalog.id, schemaId = requireNotNull(vct.value.id), publishedOnly = true),
                )
            assertTrue(vctViewResult.isOk, vctViewResult.toString())
            val mdocViewResult =
                env.viewType.execute(
                    SchemaIdArgs(catalogId = catalog.id, schemaId = requireNotNull(mdoc.value.id), publishedOnly = true),
                )
            assertTrue(mdocViewResult.isOk, mdocViewResult.toString())
            val vctView = vctViewResult.value
            val mdocView = mdocViewResult.value

            val card = assertNotNull(vctView.card)
            assertEquals(CatalogCardSource.VCT_METADATA, card.source)
            assertEquals("Person Identification Data (PID)", vctView.title)
            assertEquals(AttestationTypeKeyKind.VCT, vctView.typeKey.kind)
            assertEquals("urn:eudi:pid:1", vctView.typeKey.value)
            assertNull(mdocView.card)
            assertEquals(AttestationTypeKeyKind.DOCTYPE, mdocView.typeKey.kind)
            val family = mdocView.claims.single { it.path == "family_name" }
            assertEquals("Current last name(s) or surname(s) of the user.", family.label)
        }

    @Test
    fun resolveStillDoesNotImplyTrust() =
        runTest {
            val env = Env()
            val created =
                env.createCatalog.execute(CreateCatalogArgs("wallet-resolve", "Wallet resolve", verificationEnabled = true))
            assertTrue(created.isOk, created.toString())
            val catalog = created.value
            val schema =
                env.createSchema.execute(
                    CreateSchemaArgs(
                        catalog.id,
                        schema(listOf("dc+sd-jwt"), "https://example.test/urn:eudi:pid:1"),
                        documents("dc+sd-jwt", fixture("webuild-consortium/pid.vctm.json")),
                    ),
                )
            assertTrue(schema.isOk, schema.toString())
            val published = env.publish.execute(CatalogIdArgs(catalog.id))
            assertTrue(published.isOk, published.toString())

            val resolvedResult =
                env.resolve.execute(
                    ResolveAttestationTypeArgs(AttestationTypeKey(AttestationTypeKeyKind.VCT, "urn:eudi:pid:1")),
                )
            assertTrue(resolvedResult.isOk, resolvedResult.toString())
            val resolved = resolvedResult.value
            assertEquals(1, resolved.total)
            assertTrue(
                resolved.data
                    .single()
                    .trustedAuthorities
                    .isEmpty()
            )
            assertNull(
                resolved.data
                    .single()
                    .id
                    ?.takeIf { it.contains("trust", ignoreCase = true) }
            )
        }

    private class Env {
        val store = InMemoryAttestationCatalogStore()
        val createCatalog = CreateCatalogCommandImpl(TestSessionExecution, store)
        val createSchema = CreateSchemaCommandImpl(TestSessionExecution, store)
        val publish = PublishCatalogCommandImpl(TestSessionExecution, store)
        val viewType = GetCatalogTypeViewCommandImpl(TestSessionExecution, store, NoOpIssuerBindingLookup())
        val resolve = ResolveAttestationTypeCommandImpl(TestSessionExecution, store)
    }

    private fun schema(
        formats: List<String>,
        uri: String
    ) = SchemaMeta(
        id = null,
        version = "1.0.0",
        rulebookURI = "https://example.test/rulebook",
        attestationLoS = "iso_18045_high",
        bindingType = "key",
        supportedFormats = formats,
        schemaURIs = listOf(SchemaUriRef(formats.single(), uri)),
    )

    private fun documents(
        format: String,
        bytes: ByteArray
    ) = listOf(
        AttestationSchemaDocument(
            kind = CatalogDocumentKind.RULEBOOK,
            mediaType = "text/markdown",
            bytes = "# rulebook".encodeToByteArray(),
        ),
        AttestationSchemaDocument(
            kind = CatalogDocumentKind.FORMAT,
            formatIdentifier = format,
            mediaType = "application/json",
            bytes = bytes,
        ),
    )

    private fun fixture(path: String): ByteArray {
        val resource = "ts11-public-registry/$path"
        val stream =
            requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
                "missing fixture $resource"
            }
        return stream.use { it.readBytes() }
    }

    private object TestSessionExecution : SessionExecution {
        override val tenantId: String = "acme"
        override val principalId: String = "operator"
        override val correlationId: String = "corr-wallet-view"
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
        override val id: String = "wallet-view-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw IllegalStateException("unused")
    }
}
