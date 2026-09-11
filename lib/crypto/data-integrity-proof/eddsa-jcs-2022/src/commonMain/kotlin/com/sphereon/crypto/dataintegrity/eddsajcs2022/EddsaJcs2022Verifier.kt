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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

/**
 * W3C VC-DI 1.0 + VC-DI-EDDSA `eddsa-jcs-2022` cryptosuite "Verify Proof"
 * implementation.
 *
 * Spec: https://www.w3.org/TR/vc-di-eddsa/#eddsa-jcs-2022
 *
 * Pipeline:
 * 1. Decode `proof.proofValue` from multibase base58btc back into raw signature bytes.
 * 2. Recompute `hashData = SHA-256(JCS(proofConfig)) || SHA-256(JCS(unsecuredDocument))`.
 * 3. Resolve `proof.verificationMethod` through the identifier-neutral
 *    [VerificationMethodResolver] abstraction.
 * 4. Verify the Ed25519 signature against `hashData` via [SimpleSignatureService].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteVerifier>())
class EddsaJcs2022Verifier(
    private val verificationMethodResolver: VerificationMethodResolver,
    private val signatureService: SimpleSignatureService,
) : DataIntegrityCryptosuiteVerifier {
    override val cryptosuiteId: String = EddsaJcs2022Cryptosuite.ID

    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
    ): IdkResult<CryptosuiteVerification, IdkError> {
        if (proof.cryptosuite != EddsaJcs2022Cryptosuite.ID) return Ok(failed("${EddsaJcs2022Cryptosuite.ID}: cryptosuite mismatch"))
        if (proof.type != DataIntegrityProof.TYPE_DATA_INTEGRITY) return Ok(failed("${EddsaJcs2022Cryptosuite.ID}: proof type mismatch"))
        val signatureOrFailure = decodeSignature(proof) ?: return Ok(failed("eddsa-jcs-2022: invalid proofValue encoding"))
        val resolution = try {
            verificationMethodResolver.resolve(
                proof.verificationMethod,
                verificationMethodResolutionPolicy,
            ).getOrNull()
        } catch (_: Exception) {
            null
        } ?: return Ok(failed("verification method resolution failed"))
        if (resolution.reference != proof.verificationMethod) {
            return Ok(failed("resolved verification method does not match proof.verificationMethod"))
        }
        if (resolution.controller == null || proof.proofPurpose !in resolution.authorizedProofPurposes) {
            return Ok(failed("verification method is not authorized for proofPurpose '${proof.proofPurpose.value}'"))
        }
        val key = resolution.key as? JwkType
            ?: return Ok(failed("resolved verification method is not a JOSE Ed25519 key"))
        try {
            EddsaJcs2022Cryptosuite.requireEd25519Key(key)
        } catch (expected: IllegalArgumentException) {
            return Ok(failed(expected.message ?: "invalid Ed25519 verification key"))
        }
        val hashData = EddsaJcs2022Cryptosuite.hashData(unsecuredDocument, proof.copy(proofValue = ""))
        val keyInfo = KeyInfo(key = key, kid = proof.verificationMethod)
        val valid =
            runVerify(keyInfo, hashData, signatureOrFailure)
                ?: return Ok(failed("signature verification raised an unexpected exception"))

        return Ok(
            CryptosuiteVerification(
                verified = valid,
                verifiedDocument =
                    if (valid) {
                        unsecuredDocument
                    } else {
                        null
                    },
                errors =
                    if (valid) {
                        emptyList()
                    } else {
                        listOf("signature mismatch")
                    },
            ),
        )
    }

    private fun decodeSignature(proof: DataIntegrityProof): ByteArray? {
        if (proof.cryptosuite != EddsaJcs2022Cryptosuite.ID) {
            return null
        }
        return try {
            EddsaJcs2022Cryptosuite.decodeProofValue(proof.proofValue)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun runVerify(
        keyInfo: KeyInfoType<*>,
        hashData: ByteArray,
        signature: ByteArray
    ): Boolean? =
        try {
            signatureService.isValidRawSignature(keyInfo, hashData, signature)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

    private fun failed(message: String): CryptosuiteVerification = CryptosuiteVerification(verified = false, errors = listOf(message))
}
