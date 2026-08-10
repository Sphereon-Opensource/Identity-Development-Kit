/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.openid.oid4vp.dcql

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Wire-contract tests for OpenID4VP 1.0 Final Sections 6 and 7. */
class DcqlQueryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun decodesFinalCredentialAndClaimSetShapes() {
        val query =
            json.decodeFromString<DcqlQuery>(
                """
                {
                  "credentials": [{
                    "id": "pid",
                    "format": "dc+sd-jwt",
                    "meta": {"vct_values": ["urn:eudi:pid:1"]},
                    "claims": [
                      {"id": "given", "path": ["given_name"]},
                      {"id": "street", "path": ["address", null, 0, "street"]}
                    ],
                    "claim_sets": [["given"], ["given", "street"]]
                  }, {
                    "id": "mdl",
                    "format": "mso_mdoc",
                    "meta": {"doctype_value": "org.iso.18013.5.1.mDL"}
                  }],
                  "credential_sets": [{"options": [["pid"], ["mdl"]]}]
                }
                """.trimIndent(),
            )

        assertEquals(listOf("given"), query.credentials.first().claim_sets?.first())
        assertEquals(
            listOf(JsonPrimitive("address"), JsonNull, JsonPrimitive(0), JsonPrimitive("street")),
            query.credentials.first().claims?.get(1)?.path?.components,
        )
        assertEquals(listOf("pid"), query.credential_sets?.single()?.options?.first())
        assertTrue(query.credential_sets?.single()?.required == true)
    }

    @Test
    fun encodesCredentialSetOptionsAsArraysOfArrays() {
        val encoded =
            json.encodeToString(
                DcqlQuery(
                    credentials =
                        listOf(
                            credential("pid"),
                            credential("mdl", format = "mso_mdoc"),
                        ),
                    credential_sets = listOf(DcqlCredentialSetQuery(options = listOf(listOf("pid"), listOf("mdl")))),
                ),
            )

        assertTrue(encoded.contains("\"options\":[[\"pid\"],[\"mdl\"]]"))
    }

    @Test
    fun formatAndMetaAreRequiredOnTheWire() {
        assertFailsWith<MissingFieldException> {
            json.decodeFromString<DcqlQuery>("""{"credentials":[{"id":"pid","meta":{}}]}""")
        }
        assertFailsWith<MissingFieldException> {
            json.decodeFromString<DcqlQuery>("""{"credentials":[{"id":"pid","format":"dc+sd-jwt"}]}""")
        }
    }

    @Test
    fun credentialsAreRequiredAndNonEmpty() {
        assertFailsWith<MissingFieldException> { json.decodeFromString<DcqlQuery>("{}") }
        assertFailsWith<IllegalArgumentException> { DcqlQuery(emptyList()) }
    }

    @Test
    fun cryptographicHolderBindingIsFinalAndDefaultsToTrue() {
        val decoded =
            json.decodeFromString<DcqlQuery>(
                """{"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["urn:eudi:pid:1"]},"require_cryptographic_holder_binding":false}]}""",
            )
        val reencoded = json.encodeToString(decoded)
        assertFalse(decoded.credentials.single().require_cryptographic_holder_binding)
        assertTrue(reencoded.contains("\"require_cryptographic_holder_binding\":false"))
        assertTrue(credential("pid").require_cryptographic_holder_binding)
    }

    @Test
    fun claimsPathPointerRejectsInvalidComponents() {
        assertFailsWith<IllegalArgumentException> { ClaimsPathPointer(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ClaimsPathPointer(listOf(JsonPrimitive(-1))) }
        assertFailsWith<IllegalArgumentException> { ClaimsPathPointer(listOf(JsonPrimitive(1.5))) }
        assertFailsWith<IllegalArgumentException> { ClaimsPathPointer(listOf(JsonObject(emptyMap()))) }
    }

    private fun credential(
        id: String,
        format: String = "dc+sd-jwt",
    ) = DcqlCredentialQuery(
        id = id,
        format = format,
        meta = if (format == "mso_mdoc") mdocMeta("org.iso.18013.5.1.mDL") else sdJwtVcMeta("urn:eudi:pid:1"),
    )
}
