/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.catalog.model.TrustFrameworkType
import kotlin.test.Test
import kotlin.test.assertTrue

class SchemaMetaValidatorTest {
    private val valid =
        SchemaMeta(
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/pid.vctm.json")),
        )

    @Test
    fun acceptsValidSchemaMeta() {
        assertTrue(SchemaMetaValidator.validate(valid).isOk)
    }

    @Test
    fun acceptsTwoPartAndThreePartVersions() {
        assertTrue(SchemaMetaValidator.validate(valid.copy(version = "1.5")).isOk)
        assertTrue(SchemaMetaValidator.validate(valid.copy(version = "1.0")).isOk)
        assertTrue(SchemaMetaValidator.validate(valid.copy(version = "0.1.0")).isOk)
        assertTrue(SchemaMetaValidator.validate(valid.copy(version = "v1")).isErr)
    }

    @Test
    fun rejectsUnknownLoS() {
        assertTrue(SchemaMetaValidator.validate(valid.copy(attestationLoS = "high")).isErr)
    }

    @Test
    fun acceptsEnhancedBasicHyphen() {
        assertTrue(SchemaMetaValidator.validate(valid.copy(attestationLoS = "iso_18045_enhanced-basic")).isOk)
    }

    @Test
    fun rejectsFormatNotInSupported() {
        val schema =
            valid.copy(
                schemaURIs = listOf(SchemaUriRef("mso_mdoc", "https://example.test/pid.mdoc.json")),
            )
        assertTrue(SchemaMetaValidator.validate(schema).isErr)
    }

    @Test
    fun rejectsIsLoteOnNonTl() {
        val schema =
            valid.copy(
                trustedAuthorities =
                    listOf(TrustAuthority(frameworkType = TrustFrameworkType.aki, value = "abc", isLOTE = true)),
            )
        assertTrue(SchemaMetaValidator.validate(schema).isErr)
    }

    @Test
    fun rejectsBadSlug() {
        assertTrue(SchemaMetaValidator.validateSlug("EU DI").isErr)
        assertTrue(SchemaMetaValidator.validateSlug("webuild-pid").isOk)
    }

    @Test
    fun rejectsNonUriRulebook() {
        assertTrue(SchemaMetaValidator.validate(valid.copy(rulebookURI = "not a uri")).isErr)
    }

    @Test
    fun acceptsHostedRelativeAndAboutBlankUris() {
        assertTrue(SchemaMetaValidator.validate(valid.copy(rulebookURI = "about:blank")).isOk)
        assertTrue(
            SchemaMetaValidator
                .validate(
                    valid.copy(rulebookURI = "/public/catalogs/pid/api/v1/schemas/1/rulebook"),
                ).isOk,
        )
    }

    @Test
    fun rejectsUnknownJsonProperties() {
        val raw =
            """
            {
              "version":"1.0.0",
              "rulebookURI":"https://example.test/rulebook",
              "attestationLoS":"iso_18045_high",
              "bindingType":"key",
              "supportedFormats":["dc+sd-jwt"],
              "schemaURIs":[{"formatIdentifier":"dc+sd-jwt","uri":"https://example.test/pid.vctm.json"}],
              "issuers":[{"id":"should-not-be-here"}]
            }
            """.trimIndent()
        assertTrue(SchemaMetaValidator.validateEncoded(raw).isErr)
    }
}
