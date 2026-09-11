/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.crypto.dataintegrity.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataIntegrityProofSerializationTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun unknownProofPropertiesRoundTripAtTheirOriginalWireLevel() {
        val input =
            """{"type":"DataIntegrityProof","cryptosuite":"future-suite","proofPurpose":"assertionMethod","verificationMethod":"did:example:issuer#key-1","proofValue":"zsignature","created":"2026-08-26T12:00:00Z","capabilityChain":{"id":"urn:capability:1"},"suiteParameter":["a",2]}"""

        val decoded = json.decodeFromString<DataIntegrityProof>(input)
        val encoded = json.encodeToString(decoded)
        val roundTripped = json.decodeFromString<DataIntegrityProof>(encoded)

        assertEquals(JsonPrimitive("urn:capability:1"), roundTripped.additionalProofProperties["capabilityChain"]?.jsonObject?.get("id"))
        assertEquals(decoded.additionalProofProperties, roundTripped.additionalProofProperties)
        assertEquals(decoded, roundTripped)
    }

    @Test
    fun extensionCannotShadowTypedProofProperty() {
        assertFailsWith<IllegalArgumentException> {
            DataIntegrityProof(
                cryptosuite = "future-suite",
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                verificationMethod = "did:example:issuer#key-1",
                proofValue = "zsignature",
                additionalProofProperties = buildJsonObject { put("proofValue", JsonPrimitive("conflicting")) },
            )
        }
    }

    @Test
    fun domainSetRoundTripsAsAnArrayWithoutReordering() {
        val input =
            """{"type":"DataIntegrityProof","cryptosuite":"future-suite","proofPurpose":"assertionMethod","verificationMethod":"did:example:issuer#key-1","proofValue":"zsignature","domain":["urn:second","urn:first"]}"""

        val decoded = json.decodeFromString<DataIntegrityProof>(input)
        assertEquals(null, decoded.domain)
        assertEquals(listOf("urn:second", "urn:first"), decoded.domainSet)
        val encoded = json.encodeToString(decoded)
        assertEquals(JsonArray(listOf(JsonPrimitive("urn:second"), JsonPrimitive("urn:first"))), json.parseToJsonElement(encoded).jsonObject["domain"])
    }

    @Test
    fun singleDomainRemainsAString() {
        val proof =
            DataIntegrityProof(
                cryptosuite = "future-suite",
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                verificationMethod = "did:example:issuer#key-1",
                proofValue = "zsignature",
                domain = "urn:single",
            )

        val encoded = json.encodeToString(proof)
        assertEquals(JsonPrimitive("urn:single"), json.parseToJsonElement(encoded).jsonObject["domain"])
    }

    @Test
    fun domainStringAndSetCannotBothBeProvided() {
        assertFailsWith<IllegalArgumentException> {
            DataIntegrityProof(
                cryptosuite = "future-suite",
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                verificationMethod = "did:example:issuer#key-1",
                proofValue = "zsignature",
                domain = "urn:single",
                domainSet = listOf("urn:set"),
            )
        }
    }

    @Test
    fun domainSetRejectsEmptyBlankAndDuplicateValues() {
        listOf(emptyList(), listOf(" "), listOf("urn:a", "urn:a")).forEach { values ->
            assertFailsWith<IllegalArgumentException> {
                DataIntegrityProof(
                    cryptosuite = "future-suite",
                    proofPurpose = ProofPurpose.ASSERTION_METHOD,
                    verificationMethod = "did:example:issuer#key-1",
                    proofValue = "zsignature",
                    domainSet = values,
                )
            }
        }
    }

    @Test
    fun decodedDomainSetRejectsInvalidValues() {
        listOf("[]", "[\" \"]", "[\"urn:a\",\"urn:a\"]").forEach { values ->
            val input =
                """{"type":"DataIntegrityProof","cryptosuite":"future-suite","proofPurpose":"assertionMethod","verificationMethod":"did:example:issuer#key-1","proofValue":"zsignature","domain":$values}"""
            assertFailsWith<IllegalArgumentException> {
                json.decodeFromString<DataIntegrityProof>(input)
            }
        }
    }

    @Test
    fun proofOptionsRejectsConflictingOrInvalidDomainSet() {
        val base =
            ProofOptions(
                cryptosuite = "future-suite",
                verificationMethod = "did:example:issuer#key-1",
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                signingKeyRef = "key-1",
            )
        assertFailsWith<IllegalArgumentException> { base.copy(domain = "urn:single", domainSet = listOf("urn:set")) }
        assertFailsWith<IllegalArgumentException> { base.copy(domainSet = emptyList()) }
        assertFailsWith<IllegalArgumentException> { base.copy(domainSet = listOf("urn:a", "urn:a")) }
    }
}
