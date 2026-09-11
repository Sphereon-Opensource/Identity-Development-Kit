/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.catalog.model.TrustFrameworkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogVerificationEvaluatorTest {
    private val schema =
        SchemaMeta(
            id = "11111111-1111-1111-1111-111111111111",
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            trustedAuthorities = listOf(TrustAuthority(TrustFrameworkType.etsi_tl, "https://tl.example.test")),
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://issuer.example/public/schema/vct/EuPid")),
        )

    @Test
    fun discoveryAlwaysAllows() {
        val decision =
            CatalogVerificationEvaluator.evaluate(
                CatalogVerificationMode.DISCOVERY,
                AttestationTypeKey(AttestationTypeKeyKind.VCT, "unknown"),
                emptyList(),
            )
        assertEquals(CatalogVerificationOutcome.ALLOW, decision.outcome)
    }

    @Test
    fun typeMustExistDeniesUnknown() {
        val decision =
            CatalogVerificationEvaluator.evaluate(
                CatalogVerificationMode.TYPE_MUST_EXIST,
                AttestationTypeKey(AttestationTypeKeyKind.VCT, "missing"),
                emptyList(),
            )
        assertEquals(CatalogVerificationOutcome.DENY, decision.outcome)
    }

    @Test
    fun matchesHostedVctLeaf() {
        assertTrue(
            CatalogVerificationEvaluator.matches(
                schema,
                AttestationTypeKey(AttestationTypeKeyKind.VCT, "EuPid"),
            ),
        )
    }

    @Test
    fun typeAndAuthoritiesReturnsHints() {
        val decision =
            CatalogVerificationEvaluator.evaluate(
                CatalogVerificationMode.TYPE_AND_TRUSTED_AUTHORITIES,
                AttestationTypeKey(AttestationTypeKeyKind.VCT, "EuPid"),
                listOf(schema),
            )
        assertEquals(CatalogVerificationOutcome.ALLOW, decision.outcome)
        assertEquals(1, decision.selectedAuthorities.size)
    }

    @Test
    fun matchesDoctypeAndSchemaUri() {
        val mdoc =
            schema.copy(
                supportedFormats = listOf("mso_mdoc"),
                schemaURIs = listOf(SchemaUriRef("mso_mdoc", "org.iso.18013.5.1.mDL")),
            )
        assertTrue(
            CatalogVerificationEvaluator.matches(
                mdoc,
                AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, "org.iso.18013.5.1.mDL"),
            ),
        )
        assertTrue(
            CatalogVerificationEvaluator.matches(
                schema,
                AttestationTypeKey(
                    AttestationTypeKeyKind.SCHEMA_URI,
                    "https://issuer.example/public/schema/vct/EuPid",
                ),
            ),
        )
    }
}
