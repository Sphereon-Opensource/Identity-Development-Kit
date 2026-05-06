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

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.models.DidDocument
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Verifies a single `did:webvh` log entry's eddsa-jcs-2022 proof against the
 * active updateKeys. Extracted from `WebvhLogReplayerImpl` to keep the
 * replayer's per-class function count manageable.
 */
@Inject
@SingleIn(SessionScope::class)
class WebvhEntryProofVerifier(
    private val signatureService: SimpleSignatureService,
    private val execution: SessionExecution,
) {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
        }

    /** True if any proof on [entry] is signed by a key in [activeUpdateKeys]. */
    suspend fun anyProofValid(
        entry: WebvhLogEntry,
        activeUpdateKeys: List<String>
    ): Boolean = entry.proof.any { proof -> verifyOneProof(entry, proof, activeUpdateKeys) }

    private suspend fun verifyOneProof(
        entry: WebvhLogEntry,
        proof: DataIntegrityProof,
        activeUpdateKeys: List<String>,
    ): Boolean {
        if (proof.cryptosuite != EddsaJcs2022Cryptosuite.ID) {
            return false
        }
        val multikey = locateVerificationMethodMultikey(entry.state, proof.verificationMethod) ?: return false
        if (multikey !in activeUpdateKeys) {
            return false
        }
        val signature = decodeSignature(proof.proofValue) ?: return false
        val jwk = WebvhMultikeyCodec.toEd25519Jwk(multikey, kid = proof.verificationMethod) ?: return false
        val hashData = computeHashData(entry, proof)
        return runVerify(KeyInfo(key = jwk, kid = proof.verificationMethod), hashData, signature)
    }

    private fun computeHashData(
        entry: WebvhLogEntry,
        proof: DataIntegrityProof
    ): ByteArray {
        val proofWithoutValue = proof.copy(proofValue = "")
        val entryWithoutProof = entry.copy(proof = emptyList())
        val asJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), entryWithoutProof).jsonObject
        val cleanedEntry = JsonObject(asJson - PROOF)
        return EddsaJcs2022Cryptosuite.hashData(cleanedEntry, proofWithoutValue)
    }

    private fun decodeSignature(proofValue: String): ByteArray? =
        try {
            EddsaJcs2022Cryptosuite.decodeProofValue(proofValue)
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun runVerify(
        keyInfo: KeyInfo<Jwk>,
        hashData: ByteArray,
        signature: ByteArray,
    ): Boolean =
        try {
            signatureService.isValidRawSignature(keyInfo, hashData, signature)
        } catch (e: IllegalArgumentException) {
            execution.log.debug { "webvh entry proof rejected (IllegalArgumentException: ${e.message}; kid=${keyInfo.kid})" }
            false
        } catch (e: IllegalStateException) {
            execution.log.debug { "webvh entry proof rejected (IllegalStateException: ${e.message}; kid=${keyInfo.kid})" }
            false
        } catch (e: com.sphereon.crypto.core.PKIException) {
            // Some KMS providers throw PKIException for malformed/wrong-curve signatures
            // (e.g. "Invalid point" from a tampered Ed25519 sig). Treat as "did not verify"
            // but log so a real underlying failure isn't silently lost.
            execution.log.warn { "webvh entry proof rejected (PKIException: ${e.message}; kid=${keyInfo.kid})" }
            false
        }

    /**
     * Look up the verification method by full DID URL inside [doc] and
     * return its public key in multikey form, or null when not found or
     * not encoded as a multikey.
     */
    private fun locateVerificationMethodMultikey(
        doc: DidDocument,
        verificationMethodId: String
    ): String? {
        val vm =
            doc.verificationMethod?.firstOrNull {
                it.id == verificationMethodId || it.id.endsWith("#${verificationMethodId.substringAfter('#')}")
            } ?: return null
        return vm.publicKeyMultibase
    }

    private companion object {
        private const val PROOF = "proof"
    }
}

/**
 * Decodes / encodes Ed25519 Multikeys per the W3C Multikey spec
 * (multicodec varint `0xed 0x01` + 32 raw public-key bytes).
 */
internal object WebvhMultikeyCodec {
    private const val MULTICODEC_PREFIX_BYTES = 2
    private const val ED25519_RAW_KEY_BYTES = 32
    private const val ED25519_MULTIKEY_LENGTH = MULTICODEC_PREFIX_BYTES + ED25519_RAW_KEY_BYTES
    private const val BYTE_MASK = 0xFF
    private const val ED25519_MULTICODEC_BYTE_0 = 0xED
    private const val ED25519_MULTICODEC_BYTE_1 = 0x01

    fun toEd25519Jwk(
        multikey: String,
        kid: String
    ): Jwk? {
        val raw = decodeMultibase(multikey) ?: return null
        if (!isEd25519Multikey(raw)) {
            return null
        }
        val rawKey = raw.copyOfRange(MULTICODEC_PREFIX_BYTES, raw.size)
        return Jwk(
            kty = JwaKeyType.OKP,
            crv = JwaCurve.Ed25519,
            x = rawKey.encodeToBase64Url(),
            kid = kid,
        )
    }

    private fun decodeMultibase(multibase: String): ByteArray? =
        try {
            Multibase.decode(multibase)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun isEd25519Multikey(raw: ByteArray): Boolean {
        if (raw.size != ED25519_MULTIKEY_LENGTH) {
            return false
        }
        return (raw[0].toInt() and BYTE_MASK) == ED25519_MULTICODEC_BYTE_0 &&
            (raw[1].toInt() and BYTE_MASK) == ED25519_MULTICODEC_BYTE_1
    }
}
