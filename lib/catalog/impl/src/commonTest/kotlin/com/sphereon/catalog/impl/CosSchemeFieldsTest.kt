/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.LinkedTypeSnapshot
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.LinkSchemaArgs
import com.sphereon.catalog.command.UpdateSchemaArgs
import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.model.AttributeReference
import com.sphereon.catalog.eu.model.CataloguePointerRef
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.PostalAddress
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.VersionStatus
import com.sphereon.catalog.impl.command.CreateCatalogCommandImpl
import com.sphereon.catalog.impl.command.CreateSchemaCommandImpl
import com.sphereon.catalog.impl.command.LinkSchemaCommandImpl
import com.sphereon.catalog.impl.command.UpdateSchemaCommandImpl
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CosEaaTypeFields
import com.sphereon.catalog.model.CosSchemeFields
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CosSchemeFieldsTest {
    private fun names(text: String) = InternationalNames(listOf(MultiLangString("en", text)))

    private fun fullFields() =
        CosSchemeFields(
            schemeName = "EU PID scheme",
            schemeIdentifier = "urn:example:scheme:pid",
            owner =
                SchemeOwner(
                    name = names("Owner"),
                    identifier = "owner-1",
                    postalAddresses = listOf(PostalAddress("en", "Main street 1", "Amsterdam", country = "NL")),
                    electronicAddress = ElectronicAddress(listOf("https://owner.example")),
                ),
            registrationIdentifier = "REG-PID-1",
            versionStatus = VersionStatus(EuCatalogueConstants.SCHEME_STATUS_INFORCE),
            eaaType =
                CosEaaTypeFields(
                    identifier = "urn:example:type:pid",
                    name = names("PID"),
                    schemeDefinition = names("Person identification data"),
                    attributeReferences =
                        listOf(
                            AttributeReference(cataloguePointer = CataloguePointerRef("https://coa.example/coa.xml", "coa-1"), namespace = "eu.example", identifier = "family_name"),
                            AttributeReference(definitionPointer = "https://defs.example/given_name", namespace = "eu.example", identifier = "given_name"),
                        ),
                    dataModelReference = "https://example.test/data-model",
                    trustModelTypes = listOf(EuCatalogueConstants.TRUST_MODEL_QEAA, EuCatalogueConstants.TRUST_MODEL_PUB_EAA),
                ),
        )

    @Test
    fun validatorAcceptsAFullAndAnEmptySetOfFields() {
        assertTrue(CosSchemeFieldsValidator.validate(fullFields()).isOk)
        assertTrue(CosSchemeFieldsValidator.validate(CosSchemeFields()).isOk)
    }

    @Test
    fun validatorRejectsMalformedFields() {
        assertTrue(CosSchemeFieldsValidator.validate(CosSchemeFields(schemeName = " ")).isErr)
        val badTrust = fullFields().let { it.copy(eaaType = it.eaaType!!.copy(trustModelTypes = listOf("QEAA"))) }
        assertTrue(CosSchemeFieldsValidator.validate(badTrust).isErr)
        val repeatedTrust =
            fullFields().let { it.copy(eaaType = it.eaaType!!.copy(trustModelTypes = List(2) { EuCatalogueConstants.TRUST_MODEL_QEAA })) }
        assertTrue(CosSchemeFieldsValidator.validate(repeatedTrust).isErr)
        val bothPointers =
            fullFields().let {
                it.copy(
                    eaaType =
                        it.eaaType!!.copy(
                            attributeReferences =
                                listOf(
                                    AttributeReference(
                                        definitionPointer = "https://defs.example/x",
                                        cataloguePointer = CataloguePointerRef("https://coa.example/coa.xml", "coa-1"),
                                        namespace = "n",
                                        identifier = "i",
                                    ),
                                ),
                        ),
                )
            }
        assertTrue(CosSchemeFieldsValidator.validate(bothPointers).isErr)
        val neitherPointer =
            fullFields().let { it.copy(eaaType = it.eaaType!!.copy(attributeReferences = listOf(AttributeReference(namespace = "n", identifier = "i")))) }
        assertTrue(CosSchemeFieldsValidator.validate(neitherPointer).isErr)
        val blankLang = CosSchemeFields(owner = SchemeOwner(InternationalNames(listOf(MultiLangString(" ", "Owner"))), "owner-1"))
        assertTrue(CosSchemeFieldsValidator.validate(blankLang).isErr)
    }

    @Test
    fun createStoresTheCosFieldsAndRejectsMalformedOnes() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val catalog = CreateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("cos-create", "CoS create")).value
            val create = CreateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)

            val rejected = create.execute(CreateSchemaArgs(catalog.id, schema(), documents(), cos = CosSchemeFields(schemeName = "")))
            assertTrue(rejected.isErr)

            val created = create.execute(CreateSchemaArgs(catalog.id, schema(), documents(), cos = fullFields()))
            assertTrue(created.isOk)
            val record = store.findSchema(TestSessionExecution.tenantId, catalog.id, created.value.id!!).value
            assertEquals(fullFields(), record?.cos)
        }

    @Test
    fun updateReplacesTheCosFieldsAndKeepsThemWhenOmitted() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val catalog = CreateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("cos-update", "CoS update")).value
            val created = CreateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateSchemaArgs(catalog.id, schema(), documents(), cos = fullFields())).value
            val update = UpdateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization)

            val kept = update.execute(UpdateSchemaArgs(catalog.id, created.id!!, created.copy(version = "1.1.0")))
            assertTrue(kept.isOk)
            assertEquals(fullFields(), store.findSchema(TestSessionExecution.tenantId, catalog.id, created.id!!).value?.cos)

            val replacement = fullFields().copy(schemeName = "Renamed scheme")
            val replaced = update.execute(UpdateSchemaArgs(catalog.id, created.id!!, created.copy(version = "1.2.0"), cos = replacement))
            assertTrue(replaced.isOk)
            assertEquals(replacement, store.findSchema(TestSessionExecution.tenantId, catalog.id, created.id!!).value?.cos)

            val invalid = update.execute(UpdateSchemaArgs(catalog.id, created.id!!, created, cos = CosSchemeFields(registrationIdentifier = " ")))
            assertTrue(invalid.isErr)
        }

    @Test
    fun linkStoresTheCosFields() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val catalog = CreateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("cos-link", "CoS link")).value
            val link = LinkSchemaCommandImpl(TestSessionExecution, store, NoLinkedTypes, authorization = AllowAllCatalogAuthorization)
            val linked =
                link.execute(
                    LinkSchemaArgs(
                        catalogId = catalog.id,
                        vct = "https://example.test/vct/linked",
                        version = "1.0.0",
                        attestationLoS = "iso_18045_high",
                        bindingType = "key",
                        supportedFormats = listOf("dc+sd-jwt"),
                        schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/vct/linked")),
                        cos = fullFields(),
                    ),
                )
            assertTrue(linked.isOk)
            assertEquals(fullFields(), store.findSchema(TestSessionExecution.tenantId, catalog.id, linked.value.id!!).value?.cos)
        }

    @Test
    fun recordsWithoutCosFieldsStayWithoutThem() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val catalog = CreateCatalogCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateCatalogArgs("cos-none", "CoS none")).value
            val created = CreateSchemaCommandImpl(TestSessionExecution, store, authorization = AllowAllCatalogAuthorization).execute(CreateSchemaArgs(catalog.id, schema(), documents())).value
            assertNull(store.findSchema(TestSessionExecution.tenantId, catalog.id, created.id!!).value?.cos)
        }

    private fun schema() =
        SchemaMeta(
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/vct/pid")),
        )

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

    private object NoLinkedTypes : CatalogLinkedTypeSource {
        override suspend fun resolve(
            designId: String?,
            vctId: String?,
        ): IdkResult<LinkedTypeSnapshot?, IdkError> = Ok(null)
    }

    private object TestSessionExecution : SessionExecution {
        override val tenantId: String = "acme"
        override val principalId: String = "anonymous"
        override val correlationId: String = "corr-cos-fields"
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
        override val id: String = "cos-fields-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw IllegalStateException("unused")
    }
}
