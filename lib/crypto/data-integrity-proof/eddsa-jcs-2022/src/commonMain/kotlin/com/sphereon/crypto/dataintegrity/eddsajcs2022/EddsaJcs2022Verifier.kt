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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.di.session.SessionScope
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.resolver.DereferenceDidArgs
import com.sphereon.did.resolver.DereferenceDidCommand
import com.sphereon.did.resolver.DidDereferenceOptions
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
 * 3. Dereference `proof.verificationMethod` via the IDK [DereferenceDidCommand]
 *    to obtain the verification method object.
 * 4. Extract the Ed25519 public key from `publicKeyJwk` (preferred) or
 *    `publicKeyMultibase` (Multikey with multicodec prefix `0xed01`).
 * 5. Verify the Ed25519 signature against `hashData` via [SimpleSignatureService].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteVerifier>())
class EddsaJcs2022Verifier(
    private val dereferenceDidCommand: DereferenceDidCommand,
    private val signatureService: SimpleSignatureService,
) : DataIntegrityCryptosuiteVerifier {
    override val cryptosuiteId: String = EddsaJcs2022Cryptosuite.ID

    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
    ): IdkResult<CryptosuiteVerification, IdkError> {
        val signatureOrFailure = decodeSignature(proof) ?: return Ok(failed("eddsa-jcs-2022: invalid proofValue encoding"))
        val verificationMethodOrFailure = resolveVerificationMethod(proof.verificationMethod)
        if (verificationMethodOrFailure is VerificationMethodOrFailure.Failure) {
            return Ok(failed(verificationMethodOrFailure.message))
        }
        val verificationMethod = (verificationMethodOrFailure as VerificationMethodOrFailure.Success).vm
        val publicKeyJwk =
            extractEd25519Jwk(verificationMethod)
                ?: return Ok(failed("verification method does not carry an Ed25519 public key"))

        val hashData = EddsaJcs2022Cryptosuite.hashData(unsecuredDocument, proof.copy(proofValue = ""))
        val keyInfo = KeyInfo<Jwk>(key = publicKeyJwk, kid = verificationMethod.id)
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

    private suspend fun resolveVerificationMethod(verificationMethod: String): VerificationMethodOrFailure {
        val dereference =
            dereferenceDidCommand.execute(
                DereferenceDidArgs(didUrl = verificationMethod, options = DidDereferenceOptions()),
            )
        if (dereference.isErr) {
            return VerificationMethodOrFailure.Failure(
                "verification method dereference failed: ${dereference.error.message.defaultMessage}",
            )
        }
        val vm =
            dereference.value.verificationMethod
                ?: return VerificationMethodOrFailure.Failure("dereferenced result has no verificationMethod for '$verificationMethod'")
        return VerificationMethodOrFailure.Success(vm)
    }

    private suspend fun runVerify(
        keyInfo: KeyInfo<Jwk>,
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

    private fun extractEd25519Jwk(vm: VerificationMethod): Jwk? {
        vm.publicKeyJwk?.let { jwk ->
            return if (jwk.kty == JwaKeyType.OKP && jwk.crv == JwaCurve.Ed25519 && jwk.x != null) {
                jwk
            } else {
                null
            }
        }
        val multibase = vm.publicKeyMultibase ?: return null
        val raw = decodeMultibase(multibase) ?: return null
        if (!isEd25519Multikey(raw)) {
            return null
        }
        val rawKey = raw.copyOfRange(MULTICODEC_PREFIX_BYTES, raw.size)
        return Jwk(
            kty = JwaKeyType.OKP,
            crv = JwaCurve.Ed25519,
            x = rawKey.encodeToBase64Url(),
            kid = vm.id,
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

    private fun failed(message: String): CryptosuiteVerification = CryptosuiteVerification(verified = false, errors = listOf(message))

    private sealed class VerificationMethodOrFailure {
        data class Success(
            val vm: VerificationMethod
        ) : VerificationMethodOrFailure()

        data class Failure(
            val message: String
        ) : VerificationMethodOrFailure()
    }

    companion object {
        private const val MULTICODEC_PREFIX_BYTES = 2
        private const val ED25519_RAW_KEY_BYTES = 32
        private const val ED25519_MULTIKEY_LENGTH = MULTICODEC_PREFIX_BYTES + ED25519_RAW_KEY_BYTES
        private const val BYTE_MASK = 0xFF
        private const val ED25519_MULTICODEC_BYTE_0 = 0xED
        private const val ED25519_MULTICODEC_BYTE_1 = 0x01
    }
}
