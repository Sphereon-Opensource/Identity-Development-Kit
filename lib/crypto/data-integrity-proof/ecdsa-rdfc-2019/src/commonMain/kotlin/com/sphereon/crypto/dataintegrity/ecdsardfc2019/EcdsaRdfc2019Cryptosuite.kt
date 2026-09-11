/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.ecdsardfc2019

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.jsonld.processor.JsonLdProcessor
import com.sphereon.jsonld.rdfcanon.RdfDatasetCanonicalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** W3C VC-DI ECDSA `ecdsa-rdfc-2019` transformation and hashing primitives. */
object EcdsaRdfc2019Cryptosuite {
    const val ID: String = "ecdsa-rdfc-2019"
    const val PROOF_VALUE_PREFIX_BASE58BTC: Char = 'z'
    private const val PROOF_VALUE_FIELD = "proofValue"
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    /** Canonical proof configuration after setting the unsecured document context and RDFC-1.0. */
    suspend fun canonicalProofConfigBytes(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        curve: JwaCurve,
    ): ByteArray = canonicalize(processor, proofConfigJson(unsecuredDocument, proof), curve)

    /** Canonical unsecured document after JSON-LD expansion, to-RDF, and RDFC-1.0. */
    suspend fun canonicalDocumentBytes(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
        curve: JwaCurve,
    ): ByteArray = canonicalize(processor, unsecuredDocument, curve)

    /** `hashData = H(canonicalProofConfig) || H(canonicalDocument)` per curve. */
    suspend fun hashData(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        curve: JwaCurve,
    ): ByteArray {
        val digest = digestForCurve(curve)
        val proofDigest = hash(canonicalProofConfigBytes(processor, unsecuredDocument, proof, curve), digest)
        val documentDigest = hash(canonicalDocumentBytes(processor, unsecuredDocument, curve), digest)
        return proofDigest + documentDigest
    }

    fun digestForCurve(curve: JwaCurve): DigestAlg = when (curve) {
        JwaCurve.P_256 -> DigestAlg.SHA256
        JwaCurve.P_384 -> DigestAlg.SHA384
        else -> throw IllegalArgumentException("$ID supports only P-256 and P-384, got $curve")
    }

    fun signatureScalarBytes(curve: JwaCurve): Int = when (curve) {
        JwaCurve.P_256 -> 32
        JwaCurve.P_384 -> 48
        else -> throw IllegalArgumentException("$ID supports only P-256 and P-384, got $curve")
    }

    /** Require an EC verification key with a curve supported by this cryptosuite. */
    fun curveForKey(key: JwkType): JwaCurve {
        require(key.kty == JwaKeyType.EC) { "$ID requires an EC verification key" }
        val curve = key.crv ?: throw IllegalArgumentException("$ID verification key is missing crv")
        require(curve == JwaCurve.P_256 || curve == JwaCurve.P_384) {
            "$ID supports only P-256 and P-384, got $curve"
        }
        val expectedAlg = when (curve) {
            JwaCurve.P_256 -> "ES256"
            JwaCurve.P_384 -> "ES384"
            else -> error("unreachable")
        }
        val algorithm = key.alg?.value
        require(algorithm == null || algorithm == expectedAlg) {
            "$ID key algorithm ${key.alg} does not match curve $curve"
        }
        return curve
    }

    fun encodeProofValue(rawSignature: ByteArray, curve: JwaCurve): String {
        val expected = signatureScalarBytes(curve) * 2
        require(rawSignature.size == expected) {
            "$ID $curve signatures must be exactly $expected raw P1363 bytes"
        }
        return Multibase.encode(rawSignature, MultibaseEncoding.BASE58BTC)
    }

    fun decodeProofValue(proofValue: String, curve: JwaCurve): ByteArray {
        require(proofValue.isNotEmpty() && proofValue[0] == PROOF_VALUE_PREFIX_BASE58BTC) {
            "$ID proofValue must use base58btc multibase ('z')"
        }
        val raw = Multibase.decode(proofValue)
        val expected = signatureScalarBytes(curve) * 2
        require(raw.size == expected) {
            "$ID $curve proofValue must decode to exactly $expected raw P1363 bytes"
        }
        return raw
    }

    private suspend fun canonicalize(
        processor: JsonLdProcessor,
        document: JsonElement,
        curve: JwaCurve,
    ): ByteArray = RdfDatasetCanonicalizer()
        .canonicalize(processor.toRdf(document)).encodeToByteArray()

    private fun proofConfigJson(unsecuredDocument: JsonObject, proof: DataIntegrityProof): JsonObject {
        val serialized = json.encodeToJsonElement(DataIntegrityProof.serializer(), proof.copy(proofValue = "")).jsonObject
        val withoutValue = JsonObject(serialized - PROOF_VALUE_FIELD)
        // VC-DI requires the proof configuration to carry exactly the unsecured
        // document's context; injecting a context would sign a different document.
        val context = unsecuredDocument["@context"]
        return if (context == null) withoutValue else JsonObject(withoutValue + ("@context" to context))
    }
}
