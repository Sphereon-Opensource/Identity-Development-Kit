/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatDocumentValidatorTest {
    @Test
    fun sdJwtRequiresVctNotBareJsonSchema() {
        val jsonSchema = """{"properties":{"vct":{"const":"urn:eudi:pid:1"}}}""".encodeToByteArray()
        val vctm = """{"vct":"urn:eudi:pid:1","name":"PID"}""".encodeToByteArray()
        assertTrue(FormatDocumentValidator.validate("dc+sd-jwt", jsonSchema).isErr)
        assertTrue(FormatDocumentValidator.validate("dc+sd-jwt", vctm).isOk)
    }

    @Test
    fun mdocAcceptsTopLevelOrNestedDocType() {
        val nested = """{"properties":{"docType":{"const":"eu.europa.ec.eudi.pid.1"}}}""".encodeToByteArray()
        val top = """{"docType":"org.iso.18013.5.1.mDL"}""".encodeToByteArray()
        assertTrue(FormatDocumentValidator.validate("mso_mdoc", nested).isOk)
        assertTrue(FormatDocumentValidator.validate("mso_mdoc", top).isOk)
        assertTrue(FormatDocumentValidator.validate("mso_mdoc", """{"title":"no doctype"}""".encodeToByteArray()).isErr)
        assertEquals("eu.europa.ec.eudi.pid.1", FormatDocumentValidator.docTypeValue(nested))
        assertEquals("org.iso.18013.5.1.mDL", FormatDocumentValidator.docTypeValue(top))
    }

    @Test
    fun liveRegistryVctmShapeIsAccepted() {
        val live =
            """
            {
              "vct": "urn:eudi:pid:1",
              "name": "Person Identification Data (PID)",
              "display": [{"locale":"en-US","name":"PID"}],
              "claims": [{"path":["given_name"],"mandatory":true}]
            }
            """.trimIndent().encodeToByteArray()
        assertTrue(FormatDocumentValidator.isSdJwtVct(live))
        assertTrue(FormatDocumentValidator.validate("dc+sd-jwt", live).isOk)
    }
}
