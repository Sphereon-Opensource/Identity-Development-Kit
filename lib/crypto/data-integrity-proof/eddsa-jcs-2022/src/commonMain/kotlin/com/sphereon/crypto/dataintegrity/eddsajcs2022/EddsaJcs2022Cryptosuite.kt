/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.dataintegrity.eddsajcs2022

import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Identifiers and shared algorithms for the W3C Data Integrity EdDSA
 * Cryptosuite `eddsa-jcs-2022`.
 *
 * Spec: https://www.w3.org/TR/vc-di-eddsa/#eddsa-jcs-2022
 */
object EddsaJcs2022Cryptosuite {
    const val ID: String = "eddsa-jcs-2022"
    const val PROOF_VALUE_PREFIX_BASE58BTC: Char = 'z'
    private const val PROOF_VALUE_FIELD = "proofValue"

    private val canonicalJson =
        Json {
            encodeDefaults = false
            explicitNulls = false
        }

    /**
     * Build the proof config object (= the proof object minus `proofValue`)
     * and JCS-canonicalize it. Per spec the proof config is what the
     * signature commits to, alongside the document.
     */
    fun canonicalProofConfigBytes(proof: DataIntegrityProof): ByteArray {
        val proofWithoutValue = proof.copy(proofValue = "")
        val asJson = canonicalJson.encodeToJsonElement(DataIntegrityProof.serializer(), proofWithoutValue).jsonObject
        // Drop proofValue (now empty) for canonical proof config bytes.
        val withoutProofValue = JsonObject(asJson - PROOF_VALUE_FIELD)
        return Jcs.canonicalize(withoutProofValue)
    }

    /**
     * Canonicalize the unsecured document (already without `proof`) per RFC 8785.
     */
    fun canonicalDocumentBytes(unsecuredDocument: JsonObject): ByteArray = Jcs.canonicalize(unsecuredDocument)

    /**
     * Build the spec's hash data:
     * `hashData = SHA-256(canonicalProofConfig) || SHA-256(canonicalDocument)`
     */
    fun hashData(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof
    ): ByteArray {
        val docDigest = hash(canonicalDocumentBytes(unsecuredDocument), DigestAlg.SHA256)
        val proofDigest = hash(canonicalProofConfigBytes(proof), DigestAlg.SHA256)
        val out = ByteArray(proofDigest.size + docDigest.size)
        proofDigest.copyInto(out, 0)
        docDigest.copyInto(out, proofDigest.size)
        return out
    }

    /**
     * Multibase-encode a raw Ed25519 signature with the base58btc prefix `z`,
     * matching the W3C VC-DI proofValue format.
     */
    fun encodeProofValue(rawSignature: ByteArray): String = Multibase.encode(rawSignature, MultibaseEncoding.BASE58BTC)

    /**
     * Decode a multibase-encoded proofValue back into raw signature bytes.
     * Rejects values not prefixed with base58btc since `eddsa-jcs-2022`
     * mandates that encoding.
     */
    fun decodeProofValue(proofValue: String): ByteArray {
        require(proofValue.isNotEmpty() && proofValue[0] == PROOF_VALUE_PREFIX_BASE58BTC) {
            "eddsa-jcs-2022 proofValue must be base58btc-encoded (multibase prefix '$PROOF_VALUE_PREFIX_BASE58BTC')"
        }
        return Multibase.decode(proofValue)
    }
}
