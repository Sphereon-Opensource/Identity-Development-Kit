/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.crypto.secdsa

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProvider
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
enum class SecdsaCurve {
    P_256,
}

@JsExportCompat
@Serializable
enum class SecdsaPointFormat {
    COMPRESSED,
    UNCOMPRESSED,
}

@JsExportCompat
@Serializable
class SecdsaScalar
    @JvmOverloads
    constructor(
        val value: ByteArray,
        val curve: SecdsaCurve = SecdsaCurve.P_256,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is SecdsaScalar &&
                        curve == other.curve &&
                        value.contentEquals(other.value)
                )

        override fun hashCode(): Int = 31 * curve.hashCode() + value.contentHashCode()
    }

@JsExportCompat
@Serializable
class SecdsaPoint
    @JvmOverloads
    constructor(
        val x: ByteArray,
        val y: ByteArray,
        val curve: SecdsaCurve = SecdsaCurve.P_256,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is SecdsaPoint &&
                        curve == other.curve &&
                        x.contentEquals(other.x) &&
                        y.contentEquals(other.y)
                )

        override fun hashCode(): Int {
            var result = curve.hashCode()
            result = 31 * result + x.contentHashCode()
            result = 31 * result + y.contentHashCode()
            return result
        }
    }

@JsExportCompat
@Serializable
class SecdsaRawEcdsaSignature
    @JvmOverloads
    constructor(
        val r: ByteArray,
        val s: ByteArray,
        val curve: SecdsaCurve = SecdsaCurve.P_256,
    ) {
        fun toRawBytes(): ByteArray = r + s

        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is SecdsaRawEcdsaSignature &&
                        curve == other.curve &&
                        r.contentEquals(other.r) &&
                        s.contentEquals(other.s)
                )

        override fun hashCode(): Int {
            var result = curve.hashCode()
            result = 31 * result + r.contentHashCode()
            result = 31 * result + s.contentHashCode()
            return result
        }
    }

@JsExportCompat
@Serializable
data class SecdsaFullEcdsaSignature(
    val ephemeralPoint: SecdsaPoint,
    val s: SecdsaScalar,
)

@JsExportCompat
@Serializable
class SecdsaEqDlogProof
    @JvmOverloads
    constructor(
        val challenge: ByteArray,
        val response: ByteArray,
        val curve: SecdsaCurve = SecdsaCurve.P_256,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is SecdsaEqDlogProof &&
                        curve == other.curve &&
                        challenge.contentEquals(other.challenge) &&
                        response.contentEquals(other.response)
                )

        override fun hashCode(): Int {
            var result = curve.hashCode()
            result = 31 * result + challenge.contentHashCode()
            result = 31 * result + response.contentHashCode()
            return result
        }
    }

@JsExportCompat
@Serializable
data class SecdsaEqDlogPair(
    val generator: SecdsaPoint,
    val publicKey: SecdsaPoint,
)

@JsExportCompat
@Serializable
class SecdsaTrustedChallenge(
    val ephemeralPublicKey: SecdsaPoint,
    val secondaryHash: ByteArray,
    val aad: ByteArray,
    val signature: SecdsaRawEcdsaSignature,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SecdsaTrustedChallenge &&
                    ephemeralPublicKey == other.ephemeralPublicKey &&
                    secondaryHash.contentEquals(other.secondaryHash) &&
                    aad.contentEquals(other.aad) &&
                    signature == other.signature
            )

    override fun hashCode(): Int {
        var result = ephemeralPublicKey.hashCode()
        result = 31 * result + secondaryHash.contentHashCode()
        result = 31 * result + aad.contentHashCode()
        result = 31 * result + signature.hashCode()
        return result
    }
}

@JsExportCompat
@Serializable
class SecdsaEncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SecdsaEncryptedData &&
                    iv.contentEquals(other.iv) &&
                    ciphertext.contentEquals(other.ciphertext) &&
                    authTag.contentEquals(other.authTag)
            )

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}

@JsExportCompat
@Serializable
data class SecdsaTrustedChallengeBundle(
    val challenge: SecdsaTrustedChallenge,
    val encryptedData: SecdsaEncryptedData,
)

@JsExportCompat
@Serializable
class SecdsaTrustedChallengeResponse(
    val primaryHash: ByteArray,
    val additionalSecretData: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SecdsaTrustedChallengeResponse &&
                    primaryHash.contentEquals(other.primaryHash) &&
                    additionalSecretData.contentEquals(other.additionalSecretData)
            )

    override fun hashCode(): Int = 31 * primaryHash.contentHashCode() + additionalSecretData.contentHashCode()
}

@JsExportCompat
@Serializable
data class SecdsaBlindIssuanceRequest(
    val nchPublicKey: SecdsaPoint,
    val blindedSecdsaPublicKey: SecdsaPoint,
    val pinPossessionProof: SecdsaEqDlogProof,
)

@JsExportCompat
@Serializable
data class SecdsaBlindIssuanceResponse(
    val blindingPublicKey: SecdsaPoint,
    val blindedInternalPublicKey: SecdsaPoint,
    val blindingProof: SecdsaEqDlogProof? = null,
)

@JsExportCompat
@Serializable
data class SecdsaInternalCertificateMaterial(
    val identifier: String? = null,
    val blindingPublicKey: SecdsaPoint,
    val blindSecdsaPublicKey: SecdsaPoint,
    val nchPublicKey: SecdsaPoint? = null,
)

@JsExportCompat
@Serializable
class SecdsaInstructionPayload(
    val instruction: ByteArray,
    val sequenceNumber: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SecdsaInstructionPayload &&
                    sequenceNumber == other.sequenceNumber &&
                    instruction.contentEquals(other.instruction)
            )

    override fun hashCode(): Int = 31 * sequenceNumber.hashCode() + instruction.contentHashCode()
}

@JsExportCompat
@Serializable
data class SecdsaEncryptedInstruction(
    val ephemeralPoint: SecdsaPoint,
    val encryptedData: SecdsaEncryptedData,
    val sequenceNumber: Long,
)

@JsExportCompat
@Serializable
data class SecdsaInstructionExecutionState(
    val lastSequenceNumber: Long = 0,
    val pinCounter: Int = 0,
    val pinCounterLimit: Int = 5,
)

@JsExportCompat
@Serializable
class SecdsaExecutedInstruction(
    val payload: SecdsaInstructionPayload,
    val originalSignaturePoint: SecdsaPoint,
    val adjustedBlindingPublicKey: SecdsaPoint,
    val adjustedBlindSecdsaPublicKey: SecdsaPoint,
    val proof: SecdsaEqDlogProof,
    val extraData: ByteArray,
    val newState: SecdsaInstructionExecutionState,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SecdsaExecutedInstruction &&
                    payload == other.payload &&
                    originalSignaturePoint == other.originalSignaturePoint &&
                    adjustedBlindingPublicKey == other.adjustedBlindingPublicKey &&
                    adjustedBlindSecdsaPublicKey == other.adjustedBlindSecdsaPublicKey &&
                    proof == other.proof &&
                    extraData.contentEquals(other.extraData) &&
                    newState == other.newState
            )

    override fun hashCode(): Int {
        var result = payload.hashCode()
        result = 31 * result + originalSignaturePoint.hashCode()
        result = 31 * result + adjustedBlindingPublicKey.hashCode()
        result = 31 * result + adjustedBlindSecdsaPublicKey.hashCode()
        result = 31 * result + proof.hashCode()
        result = 31 * result + extraData.contentHashCode()
        result = 31 * result + newState.hashCode()
        return result
    }
}

interface SecdsaDigestSigner {
    suspend fun signDigest(digest: ByteArray): SecdsaRawEcdsaSignature
}

interface SecdsaPrimitives {
    val curve: SecdsaCurve

    fun generator(): SecdsaPoint

    fun scalar(value: ByteArray): SecdsaScalar

    fun point(
        x: ByteArray,
        y: ByteArray,
    ): SecdsaPoint

    fun pointFromJwk(jwk: Jwk): SecdsaPoint

    fun pointToJwk(
        point: SecdsaPoint,
        kid: String? = null,
    ): Jwk

    fun encodePoint(
        point: SecdsaPoint,
        format: SecdsaPointFormat = SecdsaPointFormat.UNCOMPRESSED,
    ): ByteArray

    fun decodePoint(encoded: ByteArray): SecdsaPoint

    fun validatePoint(point: SecdsaPoint): Boolean

    fun add(
        left: SecdsaPoint,
        right: SecdsaPoint,
    ): SecdsaPoint

    fun subtract(
        left: SecdsaPoint,
        right: SecdsaPoint,
    ): SecdsaPoint

    fun multiply(
        scalar: SecdsaScalar,
        point: SecdsaPoint = generator(),
    ): SecdsaPoint

    fun inverse(scalar: SecdsaScalar): SecdsaScalar

    fun multiplyScalars(
        left: SecdsaScalar,
        right: SecdsaScalar,
    ): SecdsaScalar

    fun addScalars(
        left: SecdsaScalar,
        right: SecdsaScalar,
    ): SecdsaScalar

    fun digestToScalar(digest: ByteArray): SecdsaScalar

    fun parseRawEcdsaSignature(rawSignature: ByteArray): SecdsaRawEcdsaSignature

    fun verifyDigestSignature(
        digest: ByteArray,
        publicKey: SecdsaPoint,
        signature: SecdsaRawEcdsaSignature,
    ): Boolean

    fun toFullEcdsaSignature(
        digest: ByteArray,
        publicKey: SecdsaPoint,
        signature: SecdsaRawEcdsaSignature,
    ): SecdsaFullEcdsaSignature

    suspend fun splitSign(
        digest: ByteArray,
        pinScalar: SecdsaScalar,
        nchKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
    ): SecdsaRawEcdsaSignature

    fun proveEqualDiscreteLog(
        privateScalar: SecdsaScalar,
        pairs: List<SecdsaEqDlogPair>,
        nonceScalar: SecdsaScalar,
        aad: ByteArray = ByteArray(0),
    ): SecdsaEqDlogProof

    fun verifyEqualDiscreteLog(
        pairs: List<SecdsaEqDlogPair>,
        proof: SecdsaEqDlogProof,
        aad: ByteArray = ByteArray(0),
    ): Boolean

    suspend fun createTrustedChallenge(
        pairs: List<SecdsaEqDlogPair>,
        aad: ByteArray,
        additionalSecretData: ByteArray,
        signer: SecdsaDigestSigner,
        seed: ByteArray,
        iv: ByteArray,
    ): SecdsaTrustedChallengeBundle

    suspend fun respondToTrustedChallenge(
        bundle: SecdsaTrustedChallengeBundle,
        pairs: List<SecdsaEqDlogPair>,
        commonPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        challengeSignerPublicKey: SecdsaPoint,
    ): SecdsaTrustedChallengeResponse

    fun verifyTrustedChallengeResponse(
        challenge: SecdsaTrustedChallenge,
        response: SecdsaTrustedChallengeResponse,
        pairs: List<SecdsaEqDlogPair>,
        challengeSignerPublicKey: SecdsaPoint,
    ): Boolean

    fun createBlindIssuanceRequest(
        nchPublicKey: SecdsaPoint,
        pinScalar: SecdsaScalar,
        walletBlindScalar: SecdsaScalar,
        proofNonceScalar: SecdsaScalar,
        challenge: ByteArray = ByteArray(0),
    ): SecdsaBlindIssuanceRequest

    suspend fun createBlindIssuanceResponse(
        blindedSecdsaPublicKey: SecdsaPoint,
        blindingPublicKey: SecdsaPoint,
        blindingPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        proofPrivateScalar: SecdsaScalar? = null,
        proofNonceScalar: SecdsaScalar? = null,
        challenge: ByteArray = ByteArray(0),
    ): SecdsaBlindIssuanceResponse

    fun unblindInternalPublicKey(
        blindedInternalPublicKey: SecdsaPoint,
        walletBlindScalar: SecdsaScalar,
    ): SecdsaPoint

    suspend fun recoverProviderMultipliedPoint(
        privateKeyInfo: KeyInfoType<*>,
        publicPoint: SecdsaPoint,
        blindingPublicKey: SecdsaPoint,
        kmsProvider: KmsProvider,
    ): SecdsaPoint

    suspend fun createEncryptedSignedInstruction(
        instruction: ByteArray,
        extraData: ByteArray,
        sequenceNumber: Long,
        internalCertificate: SecdsaInternalCertificateMaterial,
        pinScalar: SecdsaScalar,
        nchKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        instructionBlindScalar: SecdsaScalar,
        proofNonceScalar: SecdsaScalar,
        iv: ByteArray,
    ): SecdsaEncryptedInstruction

    suspend fun executeEncryptedSignedInstruction(
        envelope: SecdsaEncryptedInstruction,
        internalCertificate: SecdsaInternalCertificateMaterial,
        blindingPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        state: SecdsaInstructionExecutionState = SecdsaInstructionExecutionState(),
    ): SecdsaExecutedInstruction
}
