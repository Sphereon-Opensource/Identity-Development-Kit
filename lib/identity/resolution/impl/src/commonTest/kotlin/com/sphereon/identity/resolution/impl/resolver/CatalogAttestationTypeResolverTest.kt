/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.identity.resolution.impl.resolver

import com.sphereon.catalog.command.EvaluateCatalogVerificationArgs
import com.sphereon.catalog.command.EvaluateCatalogVerificationCommand
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.ResolveAttestationTypeCommand
import com.sphereon.catalog.model.CatalogVerificationDecision
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.identity.resolution.model.ResolverConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogAttestationTypeResolverTest {
    @Test
    fun discoveryModeIsNotSupported() = runTest {
        val resolver = CatalogAttestationTypeResolver(EmptyCommandRegistry)
        assertFalse(
            resolver.supports(
                "tenant-a",
                ResolverConfig(
                    enabled = true,
                    properties = mapOf("catalog-verification-mode" to "DISCOVERY"),
                ),
            ),
        )
    }

    @Test
    fun typeMissFailsClosed() = runTest {
        val resolver = CatalogAttestationTypeResolver(
            FakeCommandRegistry(
                mapOf(
                    ResolveAttestationTypeCommand.COMMAND_ID to FixedResolveCommand(emptyList()),
                    EvaluateCatalogVerificationCommand.COMMAND_ID to
                        FixedEvaluateCommand(
                            CatalogVerificationDecision(
                                outcome = CatalogVerificationOutcome.DENY,
                                mode = CatalogVerificationMode.TYPE_MUST_EXIST,
                                reasons = listOf("type-not-in-enabled-catalog"),
                            ),
                        ),
                ),
            ),
        )
        val result = resolver.resolve(
            identifier = "urn:eudi:pid:1",
            tenantId = "tenant-a",
            resolverConfig = ResolverConfig(
                enabled = true,
                properties = mapOf(
                    "catalog-verification-mode" to "TYPE_MUST_EXIST",
                    "attestation-type-kind" to "VCT",
                ),
            ),
        )
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("type-not-in-enabled-catalog"))
    }

    @Test
    fun typeAllowDoesNotImplyIssuerTrust() = runTest {
        val schema = SchemaMeta(
            id = "schema-1",
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "urn:eudi:pid:1")),
        )
        val resolver = CatalogAttestationTypeResolver(
            FakeCommandRegistry(
                mapOf(
                    ResolveAttestationTypeCommand.COMMAND_ID to FixedResolveCommand(listOf(schema)),
                    EvaluateCatalogVerificationCommand.COMMAND_ID to
                        FixedEvaluateCommand(
                            CatalogVerificationDecision(
                                outcome = CatalogVerificationOutcome.ALLOW,
                                mode = CatalogVerificationMode.TYPE_MUST_EXIST,
                                reasons = listOf("type-exists"),
                                schema = schema,
                            ),
                        ),
                ),
            ),
        )
        val result = resolver.resolve(
            identifier = "urn:eudi:pid:1",
            tenantId = "tenant-a",
            resolverConfig = ResolverConfig(
                enabled = true,
                properties = mapOf(
                    "catalog-verification-mode" to "TYPE_MUST_EXIST",
                    "attestation-type-kind" to "VCT",
                ),
            ),
        )
        assertTrue(result.isOk)
        assertEquals("schema-1", result.value.internalIdentityId)
        assertEquals("catalog-attestation-type", result.value.resolverId)
    }

    private object EmptyCommandRegistry : SessionScopedCommandRegistry {
        override fun get(commandId: String): ServiceCommand<*, *, *>? = null
        override fun listCommandIds(): List<String> = emptyList()
    }

    private class FakeCommandRegistry(
        private val byId: Map<String, ServiceCommand<*, *, *>>,
    ) : SessionScopedCommandRegistry {
        override fun get(commandId: String): ServiceCommand<*, *, *>? = byId[commandId]
        override fun listCommandIds(): List<String> = byId.keys.toList()
    }

    private class FixedResolveCommand(
        private val matches: List<SchemaMeta>,
    ) : ResolveAttestationTypeCommand {
        override val commandId: String = ResolveAttestationTypeCommand.COMMAND_ID
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<ResolveAttestationTypeArgs> = typeToken()
        override val outputTypeToken: TypeToken<PaginatedSchemaList> = typeToken()
        override suspend fun execute(args: ResolveAttestationTypeArgs): IdkResult<PaginatedSchemaList, IdkError> =
            Ok(PaginatedSchemaList(total = matches.size, limit = matches.size, offset = 0, data = matches))
    }

    private class FixedEvaluateCommand(
        private val decision: CatalogVerificationDecision,
    ) : EvaluateCatalogVerificationCommand {
        override val commandId: String = EvaluateCatalogVerificationCommand.COMMAND_ID
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<EvaluateCatalogVerificationArgs> = typeToken()
        override val outputTypeToken: TypeToken<CatalogVerificationDecision> = typeToken()
        override suspend fun execute(args: EvaluateCatalogVerificationArgs): IdkResult<CatalogVerificationDecision, IdkError> =
            Ok(decision)
    }
}
