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
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Verifies a witness's Data Integrity proof over a `did:webvh` versionId.
 *
 * Per spec §3.4, a witness signs the JSON document `{"versionId":"<value>"}`
 * with `eddsa-jcs-2022` using the keypair backing its `did:key`. The
 * `proof.verificationMethod` is `did:key:<multikey>#<multikey>`; the public
 * key is therefore embedded in the DID itself — no external resolution
 * needed.
 *
 * Without this verifier the replayer would only count distinct witness DIDs
 * (which a malicious controller could trivially fabricate), missing the
 * core property that witness threshold means M *valid signatures* from M
 * distinct authorized witnesses.
 */
@Inject
@SingleIn(SessionScope::class)
class WebvhWitnessProofVerifier(
    private val signatureService: SimpleSignatureService,
    private val execution: SessionExecution,
) {
    /**
     * Returns true iff [proof] is a valid `eddsa-jcs-2022` signature over
     * `{"versionId": "<versionId>"}` produced by the keypair behind the
     * `did:key` extracted from [proof.verificationMethod].
     */
    suspend fun isValidWitnessProof(
        versionId: String,
        proof: DataIntegrityProof,
    ): Boolean {
        if (proof.cryptosuite != EddsaJcs2022Cryptosuite.ID) {
            return false
        }
        val witnessDid = proof.verificationMethod.substringBefore('#')
        if (!witnessDid.startsWith(DID_KEY_PREFIX)) {
            return false
        }
        val multikey = witnessDid.removePrefix(DID_KEY_PREFIX)
        val jwk = WebvhMultikeyCodec.toEd25519Jwk(multikey, kid = proof.verificationMethod) ?: return false
        val signature = decodeSignature(proof.proofValue) ?: return false
        val document: JsonObject = buildJsonObject { put("versionId", JsonPrimitive(versionId)) }
        val hashData = EddsaJcs2022Cryptosuite.hashData(document, proof.copy(proofValue = ""))
        return runVerify(KeyInfo(key = jwk, kid = proof.verificationMethod), hashData, signature)
    }

    /**
     * Returns the witness DID (`did:key:...`) the proof was made by IFF the
     * proof verifies, otherwise null. Used by [WebvhLogReplayerImpl] to
     * collect distinct *validated* witness DIDs against the threshold.
     */
    suspend fun verifiedWitnessDidOrNull(
        versionId: String,
        proof: DataIntegrityProof,
    ): String? {
        if (!isValidWitnessProof(versionId, proof)) {
            return null
        }
        return proof.verificationMethod.substringBefore('#')
    }

    private fun decodeSignature(proofValue: String): ByteArray? =
        try {
            EddsaJcs2022Cryptosuite.decodeProofValue(proofValue)
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun runVerify(
        keyInfo: KeyInfo<com.sphereon.crypto.core.jose.Jwk>,
        hashData: ByteArray,
        signature: ByteArray,
    ): Boolean =
        try {
            signatureService.isValidRawSignature(keyInfo, hashData, signature)
        } catch (e: IllegalArgumentException) {
            execution.log.debug { "webvh witness proof rejected (IllegalArgumentException: ${e.message}; kid=${keyInfo.kid})" }
            false
        } catch (e: IllegalStateException) {
            execution.log.debug { "webvh witness proof rejected (IllegalStateException: ${e.message}; kid=${keyInfo.kid})" }
            false
        } catch (e: com.sphereon.crypto.core.PKIException) {
            // Wrong-length signature or other key-mismatch noise from the underlying KMS
            // surfaces as PKIException in some providers; treat as "did not verify" but log
            // at warn so a real underlying failure isn't silently lost.
            execution.log.warn { "webvh witness proof rejected (PKIException: ${e.message}; kid=${keyInfo.kid})" }
            false
        }

    private companion object {
        private const val DID_KEY_PREFIX = "did:key:"
    }
}
