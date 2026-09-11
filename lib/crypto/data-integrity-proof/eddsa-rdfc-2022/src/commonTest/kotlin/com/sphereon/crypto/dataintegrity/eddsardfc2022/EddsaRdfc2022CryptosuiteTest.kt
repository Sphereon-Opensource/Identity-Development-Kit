/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.eddsardfc2022

import com.sphereon.core.api.Ok
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.processor.JsonLdProcessor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Focused algorithm tests; the pinned upstream suite is documented in resources. */
class EddsaRdfc2022CryptosuiteTest {
    private val json = Json
    private val processor = JsonLdProcessor(
        LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, json.parseToJsonElement("{}")))
        },
    )

    @Test
    fun hashDataIsProofDigestFollowedByDocumentDigest() = runTest {
        val document = document("Alice")
        val proof = proof("ignored-signature")
        val proofBytes = EddsaRdfc2022Cryptosuite.canonicalProofConfigBytes(processor, document, proof)
        val documentBytes = EddsaRdfc2022Cryptosuite.canonicalDocumentBytes(processor, document)
        val expected = hash(proofBytes, DigestAlg.SHA256) + hash(documentBytes, DigestAlg.SHA256)

        assertContentEquals(expected, EddsaRdfc2022Cryptosuite.hashData(processor, document, proof))
        assertEquals(64, expected.size)
    }

    @Test
    fun proofValueIsNotPartOfCanonicalProofConfiguration() = runTest {
        val document = document("Alice")
        val first = EddsaRdfc2022Cryptosuite.canonicalProofConfigBytes(processor, document, proof("z111"))
        val second = EddsaRdfc2022Cryptosuite.canonicalProofConfigBytes(processor, document, proof("z222"))
        assertContentEquals(first, second)
    }

    @Test
    fun rdfCanonicalizationMakesDocumentPropertyOrderIrrelevant() = runTest {
        val first = json.parseToJsonElement(
            """{"@context":${contextJson()},"@id":"https://example.test/alice","type":"VerifiableCredential","name":"Alice"}""",
        ).jsonObject
        val second = json.parseToJsonElement(
            """{"name":"Alice","type":"VerifiableCredential","@id":"https://example.test/alice","@context":${contextJson()}}""",
        ).jsonObject
        assertContentEquals(
            EddsaRdfc2022Cryptosuite.canonicalDocumentBytes(processor, first),
            EddsaRdfc2022Cryptosuite.canonicalDocumentBytes(processor, second),
        )
    }

    @Test
    fun proofValueRequiresBase58BtcAndExactly64Bytes() {
        val raw = ByteArray(64) { it.toByte() }
        val encoded = EddsaRdfc2022Cryptosuite.encodeProofValue(raw)
        assertContentEquals(raw, EddsaRdfc2022Cryptosuite.decodeProofValue(encoded))
        assertFailsWith<IllegalArgumentException> { EddsaRdfc2022Cryptosuite.decodeProofValue("u${encoded.substring(1)}") }
        assertFailsWith<IllegalArgumentException> { EddsaRdfc2022Cryptosuite.encodeProofValue(ByteArray(63)) }
    }

    @Test
    fun changingDocumentChangesDocumentDigest() = runTest {
        val first = EddsaRdfc2022Cryptosuite.canonicalDocumentBytes(processor, document("Alice"))
        val second = EddsaRdfc2022Cryptosuite.canonicalDocumentBytes(processor, document("Bob"))
        assertNotEquals(first.decodeToString(), second.decodeToString())
    }

    @Test
    fun verificationKeyMustBeEd25519() {
        EddsaRdfc2022Cryptosuite.requireEd25519Key(
            Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, alg = JwaAlgorithm.EdDSA, x = "AQ"),
        )
        assertFailsWith<IllegalArgumentException> {
            EddsaRdfc2022Cryptosuite.requireEd25519Key(
                Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, alg = JwaAlgorithm.ES256, x = "AQ", y = "AQ"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EddsaRdfc2022Cryptosuite.requireEd25519Key(
                Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.X25519, x = "AQ"),
            )
        }
    }

    private fun proof(value: String) = DataIntegrityProof(
        cryptosuite = EddsaRdfc2022Cryptosuite.ID,
        proofPurpose = ProofPurpose.ASSERTION_METHOD,
        verificationMethod = "https://example.test/keys/alice#key-1",
        proofValue = value,
        created = "2023-02-24T23:36:38Z",
    )

    private fun document(name: String) = json.parseToJsonElement(
        """{"@context":${contextJson()},"@id":"https://example.test/alice","type":"VerifiableCredential","name":"$name"}""",
    ).jsonObject

    private fun contextJson() = """{
        "type":"@type",
        "id":"@id",
        "name":"https://schema.org/name",
        "VerifiableCredential":"https://www.w3.org/2018/credentials#VerifiableCredential",
        "cryptosuite":{"@id":"https://w3id.org/security#cryptosuite","@type":"https://w3id.org/security#cryptosuiteString"},
        "created":{"@id":"http://purl.org/dc/terms/created","@type":"http://www.w3.org/2001/XMLSchema#dateTime"},
        "proofPurpose":{"@id":"https://w3id.org/security#proofPurpose","@type":"@vocab"},
        "assertionMethod":"https://w3id.org/security#assertionMethod",
        "verificationMethod":"https://w3id.org/security#verificationMethod",
        "DataIntegrityProof":"https://w3id.org/security#DataIntegrityProof"
    }""".trimIndent()
}
