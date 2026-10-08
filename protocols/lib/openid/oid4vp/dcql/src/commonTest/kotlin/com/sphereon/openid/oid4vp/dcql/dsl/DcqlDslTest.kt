/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.openid.oid4vp.dcql.dsl

import com.sphereon.openid.oid4vp.dcql.ClaimsPathPointer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DcqlDslTest {
    @Test
    fun buildsFinalSdJwtQuery() {
        val query =
            dcqlQuery {
                credential("pid") {
                    sdJwtVc { vctValues("urn:eudi:pid:1") }
                    claim(listOf("given_name")) { id("given") }
                    claim(ClaimsPathPointer(listOf(JsonPrimitive("address"), JsonNull, JsonPrimitive("street")))) {
                        id("street")
                    }
                    claimSet("given")
                    claimSet("given", "street")
                    requireHolderBinding(false)
                }
            }

        val credential = query.credentials.single()
        assertEquals("dc+sd-jwt", credential.format)
        assertEquals("urn:eudi:pid:1", credential.meta["vct_values"]?.let { it.toString().trim('[', ']', '"') })
        assertEquals(listOf("given"), credential.claim_sets?.first())
        assertEquals(JsonNull, credential.claims?.get(1)?.path?.components?.get(1))
        assertFalse(credential.require_cryptographic_holder_binding)
    }

    @Test
    fun buildsFinalMdocQueryAndNestedCredentialSetOptions() {
        val query =
            dcqlQuery {
                credential("pid") { sdJwtVc { vctValues("urn:eudi:pid:1") } }
                credential("mdl") {
                    mDoc { doctype(MdocDoctypes.MDL) }
                    claim(listOf(MdocNamespaces.ISO_18013_5_1, "family_name")) {
                        intentToRetain(false)
                    }
                }
                credentialSet {
                    optional()
                    option("pid")
                    option("pid", "mdl")
                }
            }

        assertEquals(setOf("vct_values"), query.credentials.first().meta.keys)
        val set = assertNotNull(query.credential_sets).single()
        assertFalse(set.required)
        assertEquals(listOf(listOf("pid"), listOf("pid", "mdl")), set.options)
        assertFalse(query.credentials[1].claims?.single()?.intent_to_retain ?: true)
    }

    @Test
    fun builderRequiresCredentialFormatAndMeta() {
        assertFailsWith<IllegalArgumentException> {
            dcqlQuery { credential("pid") { claim("given_name") } }
        }
    }

    @Test
    fun builderRequiresAtLeastOneCredential() {
        assertFailsWith<IllegalArgumentException> { dcqlQuery {} }
    }

    @Test
    fun configuredClaimSupportsFinalValueTypes() {
        val query =
            dcqlQuery {
                credential("pid") {
                    sdJwtVc { vctValues("urn:eudi:pid:1") }
                    claim(listOf("age")) {
                        id("age")
                        values(18L)
                    }
                    claim(listOf("resident")) { values(true) }
                }
            }

        assertEquals(JsonPrimitive(18L), query.credentials.single().claims?.first()?.values?.single())
        assertTrue(query.credentials.single().claims?.get(1)?.values?.single() == JsonPrimitive(true))
    }
}
