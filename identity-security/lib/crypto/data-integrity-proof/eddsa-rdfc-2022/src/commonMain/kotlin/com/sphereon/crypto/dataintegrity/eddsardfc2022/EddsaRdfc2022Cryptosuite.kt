/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.eddsardfc2022

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.jsonld.processor.JsonLdProcessor
import com.sphereon.jsonld.rdfcanon.RdfDatasetCanonicalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** W3C VC-DI-EDDSA `eddsa-rdfc-2022` transformation and hashing primitives. */
object EddsaRdfc2022Cryptosuite {
    const val ID: String = "eddsa-rdfc-2022"
    const val PROOF_VALUE_PREFIX_BASE58BTC: Char = 'z'
    private const val PROOF_VALUE_FIELD = "proofValue"
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    /** Canonical proof configuration bytes after JSON-LD expansion, to-RDF, and RDFC-1.0. */
    suspend fun canonicalProofConfigBytes(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
    ): ByteArray = canonicalize(processor, proofConfigJson(unsecuredDocument, proof))

    /** Canonical unsecured document bytes after JSON-LD expansion, to-RDF, and RDFC-1.0. */
    suspend fun canonicalDocumentBytes(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
    ): ByteArray = canonicalize(processor, unsecuredDocument)

    /** `hashData = SHA-256(canonicalProofConfig) || SHA-256(canonicalDocument)`. */
    suspend fun hashData(
        processor: JsonLdProcessor,
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
    ): ByteArray {
        val proofDigest = hash(canonicalProofConfigBytes(processor, unsecuredDocument, proof), DigestAlg.SHA256)
        val documentDigest = hash(canonicalDocumentBytes(processor, unsecuredDocument), DigestAlg.SHA256)
        return proofDigest + documentDigest
    }

    fun encodeProofValue(rawSignature: ByteArray): String {
        require(rawSignature.size == ED25519_SIGNATURE_BYTES) { "Ed25519 signatures must be 64 bytes" }
        return Multibase.encode(rawSignature, MultibaseEncoding.BASE58BTC)
    }

    fun decodeProofValue(proofValue: String): ByteArray {
        require(proofValue.isNotEmpty() && proofValue[0] == PROOF_VALUE_PREFIX_BASE58BTC) {
            "$ID proofValue must use base58btc multibase ('z')"
        }
        val raw = Multibase.decode(proofValue)
        require(raw.size == ED25519_SIGNATURE_BYTES) { "$ID proofValue must decode to a 64-byte Ed25519 signature" }
        return raw
    }

    /** Require the JOSE key shape permitted by the Ed25519 data-integrity suite. */
    fun requireEd25519Key(key: JwkType): JwkType {
        require(key.kty == JwaKeyType.OKP) { "$ID requires an OKP verification key" }
        require(key.crv == JwaCurve.Ed25519) { "$ID requires an Ed25519 verification key" }
        require(key.alg == null || key.alg == JwaAlgorithm.EdDSA) {
            "$ID key algorithm ${key.alg} must be EdDSA"
        }
        require(!key.x.isNullOrBlank()) { "$ID verification key is missing public key material" }
        return key
    }

    private suspend fun canonicalize(processor: JsonLdProcessor, document: JsonElement): ByteArray =
        RdfDatasetCanonicalizer().canonicalize(processor.toRdf(document)).encodeToByteArray()

    private fun proofConfigJson(unsecuredDocument: JsonObject, proof: DataIntegrityProof): JsonObject {
        val serialized = json.encodeToJsonElement(DataIntegrityProof.serializer(), proof.copy(proofValue = "")).jsonObject
        val withoutValue = JsonObject(serialized - PROOF_VALUE_FIELD)
        // The suite requires the proof configuration to use exactly the
        // unsecured document context. Do not silently inject a context: doing
        // so changes the RDF statements and can make an otherwise malformed or
        // unsupported document appear signable.
        val context = unsecuredDocument["@context"]
        return if (context == null) withoutValue else JsonObject(withoutValue + ("@context" to context))
    }

    private const val ED25519_SIGNATURE_BYTES = 64
}
