/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.openid.oid4vp.dcql

import io.konform.validation.Valid
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DcqlValidatorTest {
    @Test
    fun acceptsFinalQuery() {
        val query =
            DcqlQuery(
                credentials =
                    listOf(
                        credential(
                            id = "pid",
                            claims =
                                listOf(
                                    claim("given", JsonPrimitive("given_name")),
                                    claim("age", JsonPrimitive("age_equal_or_over"), JsonPrimitive("18")),
                                ),
                            claimSets = listOf(listOf("given"), listOf("given", "age")),
                        ),
                        credential("mdl", format = "mso_mdoc"),
                    ),
                credential_sets =
                    listOf(
                        DcqlCredentialSetQuery(options = listOf(listOf("pid"), listOf("mdl"))),
                    ),
            )

        assertTrue(validateDcqlQuery(query) is Valid)
        query.credentials.forEach { assertTrue(validateDcqlCredentialQuery(it) is Valid) }
        assertTrue(validateDcqlCredentialSetQuery(query.credential_sets!!.single()) is Valid)
    }

    @Test
    fun rejectsDuplicateCredentialIds() {
        assertFailsWith<IllegalArgumentException> {
            DcqlQuery(listOf(credential("pid"), credential("pid")))
        }
    }

    @Test
    fun rejectsUnknownCredentialSetReferences() {
        assertFailsWith<IllegalArgumentException> {
            DcqlQuery(
                credentials = listOf(credential("pid")),
                credential_sets = listOf(DcqlCredentialSetQuery(options = listOf(listOf("unknown")))),
            )
        }
    }

    @Test
    fun validatesCredentialIdentifiersAndRequiredFormat() {
        assertFailsWith<IllegalArgumentException> { credential("bad id") }
        assertFailsWith<IllegalArgumentException> { credential("pid", format = "") }
    }

    @Test
    fun claimSetsRequireUniqueClaimIdsAndLocalReferences() {
        assertFailsWith<IllegalArgumentException> {
            credential(
                "pid",
                claims = listOf(DcqlClaimQuery(path = path("given_name"))),
                claimSets = listOf(listOf("given")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            credential(
                "pid",
                claims = listOf(claim("given", JsonPrimitive("given_name")), claim("given", JsonPrimitive("family_name"))),
                claimSets = listOf(listOf("given")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            credential(
                "pid",
                claims = listOf(claim("given", JsonPrimitive("given_name"))),
                claimSets = listOf(listOf("family")),
            )
        }
    }

    @Test
    fun validatesClaimValuesAndFinalPathComponents() {
        val valid =
            DcqlClaimQuery(
                id = "street",
                path = ClaimsPathPointer(listOf(JsonPrimitive("addresses"), JsonNull, JsonPrimitive(0), JsonPrimitive("street"))),
                values = listOf(JsonPrimitive("Main Street"), JsonPrimitive(7), JsonPrimitive(true)),
            )
        assertTrue(validateDcqlClaimQuery(valid) is Valid)
        assertFailsWith<IllegalArgumentException> { valid.copy(values = emptyList()) }
    }

    @Test
    fun credentialSetOptionsMustBeNonEmptyAndUseValidIds() {
        assertFailsWith<IllegalArgumentException> { DcqlCredentialSetQuery(options = emptyList()) }
        assertFailsWith<IllegalArgumentException> { DcqlCredentialSetQuery(options = listOf(emptyList())) }
        assertFailsWith<IllegalArgumentException> { DcqlCredentialSetQuery(options = listOf(listOf("bad id"))) }
    }

    private fun credential(
        id: String,
        format: String = "dc+sd-jwt",
        claims: List<DcqlClaimQuery>? = null,
        claimSets: List<List<String>>? = null,
    ) = DcqlCredentialQuery(
        id = id,
        format = format,
        meta =
            when (format) {
                "dc+sd-jwt" -> sdJwtVcMeta("urn:eudi:pid:1")
                "mso_mdoc" -> mdocMeta("org.iso.18013.5.1.mDL")
                else -> JsonObject(emptyMap())
            },
        claims = claims,
        claim_sets = claimSets,
    )

    private fun claim(
        id: String,
        vararg components: JsonPrimitive,
    ) = DcqlClaimQuery(id = id, path = ClaimsPathPointer(components.toList()))

    private fun path(vararg components: String) = ClaimsPathPointer(components.map(::JsonPrimitive))
}
