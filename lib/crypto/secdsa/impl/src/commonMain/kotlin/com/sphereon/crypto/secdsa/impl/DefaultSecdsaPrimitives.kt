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

package com.sphereon.crypto.secdsa.impl

import at.asitplus.signum.ecmath.minus
import at.asitplus.signum.ecmath.plus
import at.asitplus.signum.ecmath.times
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.ECPoint
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.command.EcdhDeriveMode
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.secdsa.SecdsaBlindIssuanceRequest
import com.sphereon.crypto.secdsa.SecdsaBlindIssuanceResponse
import com.sphereon.crypto.secdsa.SecdsaCurve
import com.sphereon.crypto.secdsa.SecdsaDigestSigner
import com.sphereon.crypto.secdsa.SecdsaEncryptedData
import com.sphereon.crypto.secdsa.SecdsaEncryptedInstruction
import com.sphereon.crypto.secdsa.SecdsaEqDlogPair
import com.sphereon.crypto.secdsa.SecdsaEqDlogProof
import com.sphereon.crypto.secdsa.SecdsaExecutedInstruction
import com.sphereon.crypto.secdsa.SecdsaFullEcdsaSignature
import com.sphereon.crypto.secdsa.SecdsaInstructionExecutionState
import com.sphereon.crypto.secdsa.SecdsaInstructionPayload
import com.sphereon.crypto.secdsa.SecdsaInternalCertificateMaterial
import com.sphereon.crypto.secdsa.SecdsaPoint
import com.sphereon.crypto.secdsa.SecdsaPointFormat
import com.sphereon.crypto.secdsa.SecdsaPrimitives
import com.sphereon.crypto.secdsa.SecdsaRawEcdsaSignature
import com.sphereon.crypto.secdsa.SecdsaScalar
import com.sphereon.crypto.secdsa.SecdsaTrustedChallenge
import com.sphereon.crypto.secdsa.SecdsaTrustedChallengeBundle
import com.sphereon.crypto.secdsa.SecdsaTrustedChallengeResponse
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

private const val DOMAIN_EQ_DLOG = "VDX-SECDSA-EQDLOG-v1"
private const val DOMAIN_TRUSTED_CHALLENGE_SIGNATURE = "VDX-SECDSA-CHAL-SIG-v1"
private const val DOMAIN_TRUSTED_CHALLENGE_KDF = "VDX-SECDSA-CHAL-KDF-v1"
private const val DOMAIN_INSTRUCTION_PAYLOAD = "VDX-SECDSA-INSTR-PAYLOAD-v1"
private const val DOMAIN_INSTRUCTION_PLAINTEXT = "VDX-SECDSA-INSTR-PLAINTEXT-v1"
private const val AES_GCM_IV_SIZE = 12
private const val AES_GCM_TAG_SIZE = 16
private const val MAX_SEQUENCE_NUMBER = 0xFFFF_FFFFL

class DefaultSecdsaPrimitives : SecdsaPrimitives {
    override val curve: SecdsaCurve = SecdsaCurve.P_256

    private val cryptoProvider = CryptographyProvider.Default
    private val signumCurve = ECCurve.SECP_256_R_1
    private val scalarLength = signumCurve.scalarLength.bytes.toInt()
    private val coordinateLength = signumCurve.coordinateLength.bytes.toInt()
    private val order = signumCurve.order
    private val orderMinusOne = order - BigInteger.ONE

    override fun generator(): SecdsaPoint = signumCurve.generator.toSecdsaPoint()

    override fun scalar(value: ByteArray): SecdsaScalar = SecdsaScalar(toScalarBytes(value.toPositiveBigInteger().modOrder()))

    override fun point(
        x: ByteArray,
        y: ByteArray,
    ): SecdsaPoint = ECPoint.fromUncompressed(signumCurve, x, y).toSecdsaPoint()

    override fun pointFromJwk(jwk: Jwk): SecdsaPoint {
        require(jwk.kty == JwaKeyType.EC) { "SECDSA P-256 point requires an EC JWK" }
        require(jwk.crv == null || jwk.crv == JwaCurve.P_256) { "SECDSA Phase 2 supports P-256 only, got: ${jwk.crv}" }
        val x = requireNotNull(jwk.x) { "EC JWK x-coordinate is required" }.decodeFrom(Encoding.BASE64URL)
        val y = requireNotNull(jwk.y) { "EC JWK y-coordinate is required" }.decodeFrom(Encoding.BASE64URL)
        return point(x, y)
    }

    override fun pointToJwk(
        point: SecdsaPoint,
        kid: String?,
    ): Jwk =
        Jwk
            .Builder()
            .withKty(JwaKeyType.EC)
            .withCrv(JwaCurve.P_256)
            .withAlg(JwaAlgorithm.ES256)
            .withX(point.x.encodeToBase64Url())
            .withY(point.y.encodeToBase64Url())
            .withKid(kid)
            .build()

    override fun encodePoint(
        point: SecdsaPoint,
        format: SecdsaPointFormat,
    ): ByteArray {
        val normalized = point.toSignumPoint()
        return when (format) {
            SecdsaPointFormat.UNCOMPRESSED -> {
                byteArrayOf(0x04) + normalized.xBytes + normalized.yBytes
            }

            SecdsaPointFormat.COMPRESSED -> {
                val prefix = if (normalized.y.residue.bitAt(0)) 0x03.toByte() else 0x02.toByte()
                byteArrayOf(prefix) + normalized.xBytes
            }
        }
    }

    override fun decodePoint(encoded: ByteArray): SecdsaPoint {
        require(encoded.isNotEmpty()) { "Encoded point is required" }
        return when (encoded.first()) {
            0x04.toByte() -> {
                require(encoded.size == 1 + coordinateLength * 2) { "Invalid uncompressed P-256 point length: ${encoded.size}" }
                point(
                    encoded.copyOfRange(1, 1 + coordinateLength),
                    encoded.copyOfRange(1 + coordinateLength, encoded.size),
                )
            }

            0x02.toByte(), 0x03.toByte() -> {
                require(encoded.size == 1 + coordinateLength) { "Invalid compressed P-256 point length: ${encoded.size}" }
                ECPoint
                    .fromCompressed(signumCurve, encoded.copyOfRange(1, encoded.size), encoded.first() == 0x03.toByte())
                    .toSecdsaPoint()
            }

            else -> {
                throw IllegalArgumentException("Unsupported EC point prefix: ${encoded.first()}")
            }
        }
    }

    override fun validatePoint(point: SecdsaPoint): Boolean = runCatching { point.toSignumPoint() }.isSuccess

    override fun add(
        left: SecdsaPoint,
        right: SecdsaPoint,
    ): SecdsaPoint = (left.toSignumPoint() + right.toSignumPoint()).toSecdsaPoint()

    override fun subtract(
        left: SecdsaPoint,
        right: SecdsaPoint,
    ): SecdsaPoint = (left.toSignumPoint() - right.toSignumPoint()).toSecdsaPoint()

    override fun multiply(
        scalar: SecdsaScalar,
        point: SecdsaPoint,
    ): SecdsaPoint = (scalar.toBigInteger().requireNonZero("scalar") * point.toSignumPoint()).toSecdsaPoint()

    override fun inverse(scalar: SecdsaScalar): SecdsaScalar = SecdsaScalar(toScalarBytes(scalar.toBigInteger().requireNonZero("scalar").modInverse(order)))

    override fun multiplyScalars(
        left: SecdsaScalar,
        right: SecdsaScalar,
    ): SecdsaScalar = SecdsaScalar(toScalarBytes((left.toBigInteger() * right.toBigInteger()).modOrder()))

    override fun addScalars(
        left: SecdsaScalar,
        right: SecdsaScalar,
    ): SecdsaScalar = SecdsaScalar(toScalarBytes((left.toBigInteger() + right.toBigInteger()).modOrder()))

    override fun digestToScalar(digest: ByteArray): SecdsaScalar = scalar(digest)

    override fun parseRawEcdsaSignature(rawSignature: ByteArray): SecdsaRawEcdsaSignature {
        require(rawSignature.size == scalarLength * 2) { "Raw P-256 ECDSA signature must be ${scalarLength * 2} bytes" }
        return SecdsaRawEcdsaSignature(
            r = rawSignature.copyOfRange(0, scalarLength),
            s = rawSignature.copyOfRange(scalarLength, rawSignature.size),
        )
    }

    override fun verifyDigestSignature(
        digest: ByteArray,
        publicKey: SecdsaPoint,
        signature: SecdsaRawEcdsaSignature,
    ): Boolean =
        if (digest.size != scalarLength) {
            false
        } else {
            runCatching {
                val r = signature.r.toPositiveBigInteger()
                val s = signature.s.toPositiveBigInteger()
                if (!r.isValidEcdsaScalar() || !s.isValidEcdsaScalar()) return false

                val e = digest.toPositiveBigInteger().modOrder()
                val w = s.modInverse(order)
                val u1 = (e * w).modOrder()
                val u2 = (r * w).modOrder()
                val verificationPoint = (u1 * signumCurve.generator) + (u2 * publicKey.toSignumPoint())
                val normalized = verificationPoint.tryNormalize() ?: return false
                normalized.x.residue.modOrder() == r
            }.getOrDefault(false)
        }

    override fun toFullEcdsaSignature(
        digest: ByteArray,
        publicKey: SecdsaPoint,
        signature: SecdsaRawEcdsaSignature,
    ): SecdsaFullEcdsaSignature {
        requireDigestScalar(digest)
        require(verifyDigestSignature(digest, publicKey, signature)) { "Cannot lift invalid ECDSA signature to full (R,s) representation" }
        val e = digest.toPositiveBigInteger().modOrder()
        val r = signature.r.toPositiveBigInteger()
        val s = signature.s.toPositiveBigInteger().requireNonZero("s")
        val sInverse = s.modInverse(order)
        val ephemeralPoint = (sInverse * ((e * signumCurve.generator) + (r * publicKey.toSignumPoint()))).toSecdsaPoint()
        return SecdsaFullEcdsaSignature(
            ephemeralPoint = ephemeralPoint,
            s = SecdsaScalar(signature.s.copyOf()),
        )
    }

    override suspend fun splitSign(
        digest: ByteArray,
        pinScalar: SecdsaScalar,
        nchKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
    ): SecdsaRawEcdsaSignature {
        requireDigestScalar(digest)
        val pin = pinScalar.toBigInteger().requireNonZero("pinScalar")
        val transformedDigest = (pin.modInverse(order) * digest.toPositiveBigInteger()).modOrder()
        val baseSignature =
            parseRawEcdsaSignature(
                kmsProvider.signDigest(
                    keyInfo = nchKeyInfo,
                    digest = toScalarBytes(transformedDigest),
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                ),
            )
        val liftedS = (pin * baseSignature.s.toPositiveBigInteger()).modOrder()
        return SecdsaRawEcdsaSignature(r = baseSignature.r.copyOf(), s = toScalarBytes(liftedS))
    }

    override fun proveEqualDiscreteLog(
        privateScalar: SecdsaScalar,
        pairs: List<SecdsaEqDlogPair>,
        nonceScalar: SecdsaScalar,
        aad: ByteArray,
    ): SecdsaEqDlogProof {
        require(pairs.isNotEmpty()) { "At least one generator/public-key pair is required" }
        val x = privateScalar.toBigInteger().requireNonZero("privateScalar")
        val nonce = nonceScalar.toBigInteger().requireNonZero("nonceScalar")
        pairs.forEachIndexed { index, pair ->
            require(multiply(privateScalar, pair.generator) == pair.publicKey) {
                "publicKey[$index] is not privateScalar * generator[$index]"
            }
        }

        val commitments = pairs.map { pair -> (nonce * pair.generator.toSignumPoint()).toSecdsaPoint() }
        val challenge = eqDlogChallenge(pairs, commitments, aad)
        require(challenge.isCanonicalNonZeroScalarBytes()) { "EqDLog challenge derived to zero; retry with a new nonce" }
        val response = (nonce + challenge.toPositiveBigInteger() * x).modOrder()
        require(response != BigInteger.ZERO) { "EqDLog response derived to zero; retry with a new nonce" }
        return SecdsaEqDlogProof(challenge = challenge, response = toScalarBytes(response))
    }

    override fun verifyEqualDiscreteLog(
        pairs: List<SecdsaEqDlogPair>,
        proof: SecdsaEqDlogProof,
        aad: ByteArray,
    ): Boolean =
        runCatching {
            if (pairs.isEmpty()) return false
            if (!proof.challenge.isCanonicalNonZeroScalarBytes() || !proof.response.isCanonicalNonZeroScalarBytes()) return false
            val c = proof.challenge.toPositiveBigInteger().modOrder()
            val z = proof.response.toPositiveBigInteger().modOrder()

            val commitments =
                pairs.map { pair ->
                    ((z * pair.generator.toSignumPoint()) - (c * pair.publicKey.toSignumPoint())).toSecdsaPoint()
                }
            val expected = eqDlogChallenge(pairs, commitments, aad)
            expected.contentEquals(proof.challenge)
        }.getOrDefault(false)

    override suspend fun createTrustedChallenge(
        pairs: List<SecdsaEqDlogPair>,
        aad: ByteArray,
        additionalSecretData: ByteArray,
        signer: SecdsaDigestSigner,
        seed: ByteArray,
        iv: ByteArray,
    ): SecdsaTrustedChallengeBundle {
        require(pairs.isNotEmpty()) { "At least one generator/public-key pair is required" }
        require(seed.size == 32) { "Trusted challenge seed must be 32 bytes" }
        require(iv.size == AES_GCM_IV_SIZE) { "AES-GCM IV must be $AES_GCM_IV_SIZE bytes" }

        val generators = pairs.map { it.generator }
        val publicKeys = pairs.map { it.publicKey }
        val derivedScalars = deriveTrustedChallengeScalars(seed, pairs, aad)
        val ephemeralPublicKey = sumScalarPointProducts(derivedScalars, generators).toSecdsaPoint()
        val primaryHash = trustedChallengePrimaryHash(derivedScalars)
        val secondaryHash = hash(primaryHash, DigestAlg.SHA256)
        val signaturePayload = trustedChallengeSignaturePayload(ephemeralPublicKey, pairs, secondaryHash, aad)
        val signature = signer.signDigest(hash(signaturePayload, DigestAlg.SHA256))
        val sharedPoint = sumScalarPointProducts(derivedScalars, publicKeys).tryNormalize() ?: throw IllegalStateException("Trusted challenge shared point is infinity")
        val dhKey = hkdfSha256(sharedPoint.xBytes, aad, 32)
        val encryptedData = aesGcmEncrypt(dhKey, seed + additionalSecretData, aad, iv)

        return SecdsaTrustedChallengeBundle(
            challenge =
                SecdsaTrustedChallenge(
                    ephemeralPublicKey = ephemeralPublicKey,
                    secondaryHash = secondaryHash,
                    aad = aad.copyOf(),
                    signature = signature,
                ),
            encryptedData = encryptedData,
        )
    }

    override suspend fun respondToTrustedChallenge(
        bundle: SecdsaTrustedChallengeBundle,
        pairs: List<SecdsaEqDlogPair>,
        commonPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        challengeSignerPublicKey: SecdsaPoint,
    ): SecdsaTrustedChallengeResponse {
        require(pairs.isNotEmpty()) { "At least one generator/public-key pair is required" }
        requireTrustedChallengeSignature(bundle.challenge, pairs, challengeSignerPublicKey)
        val sharedX =
            deriveRawX(
                privateKeyInfo = commonPrivateKeyInfo,
                publicPoint = bundle.challenge.ephemeralPublicKey,
                kmsProvider = kmsProvider,
            )
        val dhKey = hkdfSha256(sharedX, bundle.challenge.aad, 32)
        val plaintext = aesGcmDecrypt(dhKey, bundle.encryptedData, bundle.challenge.aad)
        require(plaintext.size >= 32) { "Trusted challenge plaintext must contain a 32-byte seed" }
        val seed = plaintext.copyOfRange(0, 32)
        val additionalSecretData = plaintext.copyOfRange(32, plaintext.size)
        val generators = pairs.map { it.generator }
        val publicKeys = pairs.map { it.publicKey }
        val derivedScalars = deriveTrustedChallengeScalars(seed, pairs, bundle.challenge.aad)
        require(sumScalarPointProducts(derivedScalars, generators).toSecdsaPoint() == bundle.challenge.ephemeralPublicKey) {
            "Trusted challenge ephemeral key does not match derived scalars"
        }
        val expectedSharedPoint = sumScalarPointProducts(derivedScalars, publicKeys).tryNormalize() ?: throw IllegalArgumentException("Trusted challenge shared point is infinity")
        require(expectedSharedPoint.xBytes.contentEquals(sharedX)) { "Trusted challenge DH key mismatch" }
        val primaryHash = trustedChallengePrimaryHash(derivedScalars)
        require(hash(primaryHash, DigestAlg.SHA256).contentEquals(bundle.challenge.secondaryHash)) { "Trusted challenge secondary hash mismatch" }

        return SecdsaTrustedChallengeResponse(primaryHash = primaryHash, additionalSecretData = additionalSecretData)
    }

    override fun verifyTrustedChallengeResponse(
        challenge: SecdsaTrustedChallenge,
        response: SecdsaTrustedChallengeResponse,
        pairs: List<SecdsaEqDlogPair>,
        challengeSignerPublicKey: SecdsaPoint,
    ): Boolean =
        runCatching {
            requireTrustedChallengeSignature(challenge, pairs, challengeSignerPublicKey)
            hash(response.primaryHash, DigestAlg.SHA256).contentEquals(challenge.secondaryHash)
        }.getOrDefault(false)

    override fun createBlindIssuanceRequest(
        nchPublicKey: SecdsaPoint,
        pinScalar: SecdsaScalar,
        walletBlindScalar: SecdsaScalar,
        proofNonceScalar: SecdsaScalar,
        challenge: ByteArray,
    ): SecdsaBlindIssuanceRequest {
        val pin = pinScalar.toBigInteger().requireNonZero("pinScalar")
        val walletBlind = walletBlindScalar.toBigInteger().requireNonZero("walletBlindScalar")
        val blindedPrivateScalar = SecdsaScalar(toScalarBytes((pin * walletBlind).modOrder()))
        val blindedSecdsaPublicKey = multiply(blindedPrivateScalar, nchPublicKey)
        val proof =
            proveEqualDiscreteLog(
                privateScalar = blindedPrivateScalar,
                pairs = listOf(SecdsaEqDlogPair(generator = nchPublicKey, publicKey = blindedSecdsaPublicKey)),
                nonceScalar = proofNonceScalar,
                aad = challenge,
            )
        return SecdsaBlindIssuanceRequest(
            nchPublicKey = nchPublicKey,
            blindedSecdsaPublicKey = blindedSecdsaPublicKey,
            pinPossessionProof = proof,
        )
    }

    override suspend fun createBlindIssuanceResponse(
        blindedSecdsaPublicKey: SecdsaPoint,
        blindingPublicKey: SecdsaPoint,
        blindingPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        proofPrivateScalar: SecdsaScalar?,
        proofNonceScalar: SecdsaScalar?,
        challenge: ByteArray,
    ): SecdsaBlindIssuanceResponse {
        val blindedInternalPublicKey =
            recoverProviderMultipliedPoint(
                privateKeyInfo = blindingPrivateKeyInfo,
                publicPoint = blindedSecdsaPublicKey,
                blindingPublicKey = blindingPublicKey,
                kmsProvider = kmsProvider,
            )
        val proof =
            if (proofPrivateScalar != null && proofNonceScalar != null) {
                proveEqualDiscreteLog(
                    privateScalar = proofPrivateScalar,
                    pairs =
                        listOf(
                            SecdsaEqDlogPair(generator = generator(), publicKey = blindingPublicKey),
                            SecdsaEqDlogPair(generator = blindedSecdsaPublicKey, publicKey = blindedInternalPublicKey),
                        ),
                    nonceScalar = proofNonceScalar,
                    aad = challenge,
                )
            } else {
                null
            }
        return SecdsaBlindIssuanceResponse(
            blindingPublicKey = blindingPublicKey,
            blindedInternalPublicKey = blindedInternalPublicKey,
            blindingProof = proof,
        )
    }

    override fun unblindInternalPublicKey(
        blindedInternalPublicKey: SecdsaPoint,
        walletBlindScalar: SecdsaScalar,
    ): SecdsaPoint = multiply(inverse(walletBlindScalar), blindedInternalPublicKey)

    override suspend fun recoverProviderMultipliedPoint(
        privateKeyInfo: KeyInfoType<*>,
        publicPoint: SecdsaPoint,
        blindingPublicKey: SecdsaPoint,
        kmsProvider: KmsProvider,
    ): SecdsaPoint {
        require(validatePoint(publicPoint)) { "publicPoint is not a valid SECDSA point" }
        require(validatePoint(blindingPublicKey)) { "blindingPublicKey is not a valid SECDSA point" }
        if (publicPoint == generator()) return blindingPublicKey

        val rCandidates = pointsFromX(deriveRawX(privateKeyInfo, publicPoint, kmsProvider))
        val publicPointMinusGenerator = subtract(publicPoint, generator())
        val sCandidates = pointsFromX(deriveRawX(privateKeyInfo, publicPointMinusGenerator, kmsProvider))
        val negativeBlindingPublicKey = negate(blindingPublicKey)

        for (r in rCandidates) {
            for (s in sCandidates) {
                if (subtract(r, s) == blindingPublicKey) return r
                if (subtract(r, s) == negativeBlindingPublicKey) return negate(r)
                if (add(r, s) == blindingPublicKey) return r
                if (add(r, s) == negativeBlindingPublicKey) return negate(r)
            }
        }
        throw IllegalStateException("Could not recover full provider-multiplied point from raw-X ECDH outputs")
    }

    override suspend fun createEncryptedSignedInstruction(
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
    ): SecdsaEncryptedInstruction {
        requireSequenceNumber(sequenceNumber)
        require(iv.size == AES_GCM_IV_SIZE) { "AES-GCM IV must be $AES_GCM_IV_SIZE bytes" }
        val nchPublicKey = requireNotNull(internalCertificate.nchPublicKey) { "Internal certificate material must include the NCH public key" }
        val payload = SecdsaInstructionPayload(instruction = instruction.copyOf(), sequenceNumber = sequenceNumber)
        val payloadBytes = instructionPayloadBytes(payload)
        val digest = hash(payloadBytes, DigestAlg.SHA256)
        val secdsaPublicKey = multiply(pinScalar, nchPublicKey)
        val rawSignature = splitSign(digest, pinScalar, nchKeyInfo, kmsProvider)
        val fullSignature = toFullEcdsaSignature(digest, secdsaPublicKey, rawSignature)

        val sInverse = inverse(fullSignature.s)
        val adjustedBlindingPublicKey = multiply(sInverse, internalCertificate.blindingPublicKey)
        val adjustedBlindSecdsaPublicKey = multiply(sInverse, internalCertificate.blindSecdsaPublicKey)
        val proof =
            proveEqualDiscreteLog(
                privateScalar = sInverse,
                pairs =
                    listOf(
                        SecdsaEqDlogPair(internalCertificate.blindingPublicKey, adjustedBlindingPublicKey),
                        SecdsaEqDlogPair(internalCertificate.blindSecdsaPublicKey, adjustedBlindSecdsaPublicKey),
                    ),
                nonceScalar = proofNonceScalar,
            )
        val encryptionPoint =
            add(
                multiply(digestToScalar(digest), adjustedBlindingPublicKey),
                multiply(scalar(rawSignature.r), adjustedBlindSecdsaPublicKey),
            )
        val blind = instructionBlindScalar.toBigInteger().requireNonZero("instructionBlindScalar")
        val maskedSignaturePoint = multiply(instructionBlindScalar, fullSignature.ephemeralPoint)
        val maskedEncryptionPoint = (blind * encryptionPoint.toSignumPoint()).toSecdsaPoint()
        val key = hkdfSha256(maskedEncryptionPoint.x, ByteArray(0), 32)
        val plaintext =
            encodeInstructionPlaintext(
                payload = payload,
                adjustedBlindingPublicKey = adjustedBlindingPublicKey,
                adjustedBlindSecdsaPublicKey = adjustedBlindSecdsaPublicKey,
                proof = proof,
                instructionBlindScalar = instructionBlindScalar,
                extraData = extraData,
            )
        val encryptedData = aesGcmEncrypt(key, plaintext, sequenceNumberAad(sequenceNumber), iv)
        return SecdsaEncryptedInstruction(
            ephemeralPoint = maskedSignaturePoint,
            encryptedData = encryptedData,
            sequenceNumber = sequenceNumber,
        )
    }

    override suspend fun executeEncryptedSignedInstruction(
        envelope: SecdsaEncryptedInstruction,
        internalCertificate: SecdsaInternalCertificateMaterial,
        blindingPrivateKeyInfo: KeyInfoType<*>,
        kmsProvider: KmsProvider,
        state: SecdsaInstructionExecutionState,
    ): SecdsaExecutedInstruction {
        requireSequenceNumber(envelope.sequenceNumber)
        require(envelope.sequenceNumber > state.lastSequenceNumber) { "Instruction sequence number is not newer than the stored sequence number" }
        require(state.pinCounter < state.pinCounterLimit) { "PIN counter limit reached" }
        require(validatePoint(envelope.ephemeralPoint)) { "Instruction ephemeral point is not a valid SECDSA point" }

        val sharedX = deriveRawX(blindingPrivateKeyInfo, envelope.ephemeralPoint, kmsProvider)
        val key = hkdfSha256(sharedX, ByteArray(0), 32)
        val plaintext = aesGcmDecrypt(key, envelope.encryptedData, sequenceNumberAad(envelope.sequenceNumber))
        val decoded = decodeInstructionPlaintext(plaintext)
        require(decoded.payload.sequenceNumber == envelope.sequenceNumber) { "Inner and outer instruction sequence numbers do not match" }
        require(validatePoint(decoded.adjustedBlindingPublicKey)) { "Adjusted blinding public key is not a valid SECDSA point" }
        require(validatePoint(decoded.adjustedBlindSecdsaPublicKey)) { "Adjusted blind SECDSA public key is not a valid SECDSA point" }
        require(
            verifyEqualDiscreteLog(
                pairs =
                    listOf(
                        SecdsaEqDlogPair(internalCertificate.blindingPublicKey, decoded.adjustedBlindingPublicKey),
                        SecdsaEqDlogPair(internalCertificate.blindSecdsaPublicKey, decoded.adjustedBlindSecdsaPublicKey),
                    ),
                proof = decoded.proof,
            ),
        ) { "Instruction proof does not verify" }

        val originalSignaturePoint = multiply(inverse(decoded.instructionBlindScalar), envelope.ephemeralPoint)
        val payloadDigest = hash(instructionPayloadBytes(decoded.payload), DigestAlg.SHA256)
        val r = scalar(originalSignaturePoint.x)
        val encryptionPoint =
            add(
                multiply(digestToScalar(payloadDigest), decoded.adjustedBlindingPublicKey),
                multiply(r, decoded.adjustedBlindSecdsaPublicKey),
            )
        val maskedEncryptionPoint = multiply(decoded.instructionBlindScalar, encryptionPoint)
        val verificationKey = hkdfSha256(maskedEncryptionPoint.x, ByteArray(0), 32)
        require(verificationKey.contentEquals(key)) { "Instruction encryption key does not match the blind signature relation" }

        return SecdsaExecutedInstruction(
            payload = decoded.payload,
            originalSignaturePoint = originalSignaturePoint,
            adjustedBlindingPublicKey = decoded.adjustedBlindingPublicKey,
            adjustedBlindSecdsaPublicKey = decoded.adjustedBlindSecdsaPublicKey,
            proof = decoded.proof,
            extraData = decoded.extraData,
            newState = state.copy(lastSequenceNumber = envelope.sequenceNumber, pinCounter = 0),
        )
    }

    private fun requireTrustedChallengeSignature(
        challenge: SecdsaTrustedChallenge,
        pairs: List<SecdsaEqDlogPair>,
        challengeSignerPublicKey: SecdsaPoint,
    ) {
        require(pairs.isNotEmpty()) { "At least one generator/public-key pair is required" }
        val payload = trustedChallengeSignaturePayload(challenge.ephemeralPublicKey, pairs, challenge.secondaryHash, challenge.aad)
        require(verifyDigestSignature(hash(payload, DigestAlg.SHA256), challengeSignerPublicKey, challenge.signature)) {
            "Trusted challenge signature is invalid"
        }
    }

    private fun eqDlogChallenge(
        pairs: List<SecdsaEqDlogPair>,
        commitments: List<SecdsaPoint>,
        aad: ByteArray,
    ): ByteArray {
        require(pairs.size == commitments.size) { "Pair and commitment counts must match" }
        return toScalarBytes(
            hash(
                transcript(DOMAIN_EQ_DLOG) {
                    appendPointList(pairs.map { it.generator })
                    appendPointList(commitments)
                    appendPointList(pairs.map { it.publicKey })
                    appendBytes(aad)
                },
                DigestAlg.SHA256,
            ).toPositiveBigInteger().modOrder(),
        )
    }

    private fun trustedChallengeSignaturePayload(
        ephemeralPublicKey: SecdsaPoint,
        pairs: List<SecdsaEqDlogPair>,
        secondaryHash: ByteArray,
        aad: ByteArray,
    ): ByteArray =
        transcript(DOMAIN_TRUSTED_CHALLENGE_SIGNATURE) {
            appendPoint(ephemeralPublicKey)
            appendPointList(pairs.map { it.generator })
            appendPointList(pairs.map { it.publicKey })
            appendBytes(secondaryHash)
            appendBytes(aad)
        }

    private suspend fun deriveTrustedChallengeScalars(
        seed: ByteArray,
        pairs: List<SecdsaEqDlogPair>,
        aad: ByteArray,
    ): List<BigInteger> {
        val context =
            transcript(DOMAIN_TRUSTED_CHALLENGE_KDF) {
                appendPointList(pairs.map { it.generator })
                appendPointList(pairs.map { it.publicKey })
                appendBytes(aad)
            }
        return pairs.indices.map { index ->
            val info =
                transcript(DOMAIN_TRUSTED_CHALLENGE_KDF) {
                    appendInt(index)
                    appendBytes(context)
                }
            val derived = hkdfSha256(seed, info, scalarLength + 8)
            BigInteger.ONE + (derived.toPositiveBigInteger() mod orderMinusOne)
        }
    }

    private fun trustedChallengePrimaryHash(derivedScalars: List<BigInteger>): ByteArray = hash(derivedScalars.fold(ByteArray(0)) { acc, scalar -> acc + toScalarBytes(scalar) }, DigestAlg.SHA256)

    private fun sumScalarPointProducts(
        scalars: List<BigInteger>,
        points: List<SecdsaPoint>,
    ): ECPoint {
        require(scalars.size == points.size) { "Scalar and point counts must match" }
        return scalars.zip(points).fold(signumCurve.IDENTITY) { acc, (scalar, point) ->
            acc + (scalar * point.toSignumPoint())
        }
    }

    private suspend fun deriveRawX(
        privateKeyInfo: KeyInfoType<*>,
        publicPoint: SecdsaPoint,
        kmsProvider: KmsProvider,
    ): ByteArray =
        normalizeCoordinate(
            kmsProvider
                .ecdhDerive(
                    privateKeyInfo = privateKeyInfo,
                    publicKeyInfo = keyInfoFromPoint(publicPoint),
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                    keyDataLen = null,
                    algorithmId = null,
                    partyUInfo = null,
                    partyVInfo = null,
                ).derivedSecret,
        )

    private fun keyInfoFromPoint(point: SecdsaPoint): KeyInfo<Jwk> = KeyInfo(key = pointToJwk(point))

    private fun pointsFromX(rawX: ByteArray): List<SecdsaPoint> {
        val x = normalizeCoordinate(rawX)
        val points =
            listOf(false, true)
                .mapNotNull { yOdd ->
                    runCatching { ECPoint.fromCompressed(signumCurve, x, yOdd).toSecdsaPoint() }.getOrNull()
                }.distinct()
        require(points.isNotEmpty()) { "No P-256 point exists for the supplied x-coordinate" }
        return points
    }

    private fun normalizeCoordinate(value: ByteArray): ByteArray {
        require(value.isNotEmpty()) { "Coordinate is required" }
        require(value.size <= coordinateLength) { "Coordinate does not fit P-256" }
        return if (value.size == coordinateLength) value.copyOf() else ByteArray(coordinateLength - value.size) + value
    }

    private fun negate(point: SecdsaPoint): SecdsaPoint = (signumCurve.IDENTITY - point.toSignumPoint()).toSecdsaPoint()

    private fun requireSequenceNumber(sequenceNumber: Long) {
        require(sequenceNumber in 1..MAX_SEQUENCE_NUMBER) { "Instruction sequence number must be in [1, 2^32 - 1]" }
    }

    private fun requireDigestScalar(digest: ByteArray) {
        require(digest.size == scalarLength) { "SECDSA P-256 digest/scalar must be $scalarLength bytes, got ${digest.size}" }
    }

    private fun sequenceNumberAad(sequenceNumber: Long): ByteArray {
        requireSequenceNumber(sequenceNumber)
        return byteArrayOf(
            ((sequenceNumber ushr 24) and 0xFF).toByte(),
            ((sequenceNumber ushr 16) and 0xFF).toByte(),
            ((sequenceNumber ushr 8) and 0xFF).toByte(),
            (sequenceNumber and 0xFF).toByte(),
        )
    }

    private fun instructionPayloadBytes(payload: SecdsaInstructionPayload): ByteArray =
        transcript(DOMAIN_INSTRUCTION_PAYLOAD) {
            appendLong(payload.sequenceNumber)
            appendBytes(payload.instruction)
        }

    private fun encodeInstructionPlaintext(
        payload: SecdsaInstructionPayload,
        adjustedBlindingPublicKey: SecdsaPoint,
        adjustedBlindSecdsaPublicKey: SecdsaPoint,
        proof: SecdsaEqDlogProof,
        instructionBlindScalar: SecdsaScalar,
        extraData: ByteArray,
    ): ByteArray =
        BinaryWriter()
            .apply {
                appendBytes(DOMAIN_INSTRUCTION_PLAINTEXT.encodeToByteArray())
                appendLong(payload.sequenceNumber)
                appendBytes(payload.instruction)
                appendPoint(adjustedBlindingPublicKey)
                appendPoint(adjustedBlindSecdsaPublicKey)
                appendBytes(proof.challenge)
                appendBytes(proof.response)
                appendBytes(instructionBlindScalar.value)
                appendBytes(extraData)
            }.toByteArray()

    private fun decodeInstructionPlaintext(plaintext: ByteArray): InstructionPlaintextParts {
        val reader = BinaryReader(plaintext)
        require(reader.readBytes().contentEquals(DOMAIN_INSTRUCTION_PLAINTEXT.encodeToByteArray())) { "Unsupported instruction plaintext domain" }
        val sequenceNumber = reader.readLong()
        requireSequenceNumber(sequenceNumber)
        val instruction = reader.readBytes()
        val adjustedBlindingPublicKey = reader.readPoint()
        val adjustedBlindSecdsaPublicKey = reader.readPoint()
        val challenge = reader.readBytes()
        val response = reader.readBytes()
        val blindScalarBytes = reader.readBytes()
        require(blindScalarBytes.isCanonicalNonZeroScalarBytes()) { "Instruction blind scalar must be a non-zero canonical scalar" }
        val extraData = reader.readBytes()
        reader.requireEof()
        return InstructionPlaintextParts(
            payload = SecdsaInstructionPayload(instruction = instruction, sequenceNumber = sequenceNumber),
            adjustedBlindingPublicKey = adjustedBlindingPublicKey,
            adjustedBlindSecdsaPublicKey = adjustedBlindSecdsaPublicKey,
            proof = SecdsaEqDlogProof(challenge = challenge, response = response),
            instructionBlindScalar = SecdsaScalar(blindScalarBytes),
            extraData = extraData,
        )
    }

    private data class InstructionPlaintextParts(
        val payload: SecdsaInstructionPayload,
        val adjustedBlindingPublicKey: SecdsaPoint,
        val adjustedBlindSecdsaPublicKey: SecdsaPoint,
        val proof: SecdsaEqDlogProof,
        val instructionBlindScalar: SecdsaScalar,
        val extraData: ByteArray,
    )

    private suspend fun hkdfSha256(
        inputKeyingMaterial: ByteArray,
        info: ByteArray,
        outputSize: Int,
        salt: ByteArray? = null,
    ): ByteArray =
        cryptoProvider
            .get(HKDF)
            .secretDerivation(digest = SHA256, outputSize = outputSize.bytes, salt = salt, info = info)
            .deriveSecretToByteArray(inputKeyingMaterial)

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun aesGcmEncrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        iv: ByteArray,
    ): SecdsaEncryptedData {
        val aesKey = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        val combined = aesKey.cipher(tagSize = (AES_GCM_TAG_SIZE * 8).bits).encryptWithIv(iv, plaintext, aad)
        return SecdsaEncryptedData(
            iv = iv.copyOf(),
            ciphertext = combined.copyOfRange(0, combined.size - AES_GCM_TAG_SIZE),
            authTag = combined.copyOfRange(combined.size - AES_GCM_TAG_SIZE, combined.size),
        )
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun aesGcmDecrypt(
        key: ByteArray,
        encryptedData: SecdsaEncryptedData,
        aad: ByteArray,
    ): ByteArray {
        val aesKey = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        return aesKey.cipher(tagSize = (AES_GCM_TAG_SIZE * 8).bits).decryptWithIv(encryptedData.iv, encryptedData.ciphertext + encryptedData.authTag, aad)
    }

    private fun SecdsaPoint.toSignumPoint(): ECPoint.Normalized {
        require(curve == SecdsaCurve.P_256) { "SECDSA Phase 2 supports P-256 only" }
        return ECPoint.fromUncompressed(signumCurve, x, y)
    }

    private fun ECPoint.toSecdsaPoint(): SecdsaPoint {
        val normalized = normalize()
        return SecdsaPoint(x = normalized.xBytes, y = normalized.yBytes)
    }

    private fun SecdsaScalar.toBigInteger(): BigInteger {
        require(curve == SecdsaCurve.P_256) { "SECDSA Phase 2 supports P-256 only" }
        return value.toPositiveBigInteger().modOrder()
    }

    private fun ByteArray.toPositiveBigInteger(): BigInteger = BigInteger.fromByteArray(this, Sign.POSITIVE)

    private fun BigInteger.modOrder(): BigInteger = this mod order

    private fun BigInteger.isValidEcdsaScalar(): Boolean = this > BigInteger.ZERO && this < order

    private fun ByteArray.isCanonicalScalarBytes(): Boolean = size == scalarLength && toPositiveBigInteger() < order

    private fun ByteArray.isCanonicalNonZeroScalarBytes(): Boolean = isCanonicalScalarBytes() && toPositiveBigInteger() != BigInteger.ZERO

    private fun BigInteger.requireNonZero(name: String): BigInteger {
        require(this != BigInteger.ZERO) { "$name must not be zero" }
        return this
    }

    private fun toScalarBytes(value: BigInteger): ByteArray = value.modOrder().toFixedUnsigned(scalarLength)

    private fun BigInteger.toFixedUnsigned(length: Int): ByteArray {
        val unsigned = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        require(unsigned.size <= length) { "Integer does not fit in $length bytes" }
        return ByteArray(length - unsigned.size) + unsigned
    }

    private fun transcript(
        domain: String,
        build: TranscriptBuilder.() -> Unit,
    ): ByteArray =
        TranscriptBuilder()
            .apply {
                appendBytes(domain.encodeToByteArray())
                build()
            }.toByteArray()

    private inner class TranscriptBuilder {
        private var bytes = ByteArray(0)

        fun appendInt(value: Int) {
            bytes +=
                byteArrayOf(
                    ((value ushr 24) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    (value and 0xFF).toByte(),
                )
        }

        fun appendLong(value: Long) {
            bytes +=
                byteArrayOf(
                    ((value ushr 56) and 0xFF).toByte(),
                    ((value ushr 48) and 0xFF).toByte(),
                    ((value ushr 40) and 0xFF).toByte(),
                    ((value ushr 32) and 0xFF).toByte(),
                    ((value ushr 24) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    (value and 0xFF).toByte(),
                )
        }

        fun appendBytes(value: ByteArray) {
            appendInt(value.size)
            bytes += value
        }

        fun appendPoint(point: SecdsaPoint) {
            appendBytes(encodePoint(point, SecdsaPointFormat.COMPRESSED))
        }

        fun appendPointList(points: List<SecdsaPoint>) {
            appendInt(points.size)
            points.forEach { appendPoint(it) }
        }

        fun toByteArray(): ByteArray = bytes
    }

    private inner class BinaryWriter {
        private var bytes = ByteArray(0)

        fun appendInt(value: Int) {
            bytes +=
                byteArrayOf(
                    ((value ushr 24) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    (value and 0xFF).toByte(),
                )
        }

        fun appendLong(value: Long) {
            bytes +=
                byteArrayOf(
                    ((value ushr 56) and 0xFF).toByte(),
                    ((value ushr 48) and 0xFF).toByte(),
                    ((value ushr 40) and 0xFF).toByte(),
                    ((value ushr 32) and 0xFF).toByte(),
                    ((value ushr 24) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    (value and 0xFF).toByte(),
                )
        }

        fun appendBytes(value: ByteArray) {
            appendInt(value.size)
            bytes += value
        }

        fun appendPoint(point: SecdsaPoint) {
            appendBytes(encodePoint(point, SecdsaPointFormat.COMPRESSED))
        }

        fun toByteArray(): ByteArray = bytes
    }

    private inner class BinaryReader(
        private val bytes: ByteArray,
    ) {
        private var offset = 0

        fun readLong(): Long {
            requireRemaining(8)
            var value = 0L
            repeat(8) {
                value = (value shl 8) or (bytes[offset++].toLong() and 0xFF)
            }
            return value
        }

        fun readBytes(): ByteArray {
            val length = readInt()
            require(length >= 0) { "Negative length encountered" }
            requireRemaining(length)
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun readPoint(): SecdsaPoint = decodePoint(readBytes())

        fun requireEof() {
            require(offset == bytes.size) { "Trailing bytes in instruction plaintext" }
        }

        private fun readInt(): Int {
            requireRemaining(4)
            var value = 0
            repeat(4) {
                value = (value shl 8) or (bytes[offset++].toInt() and 0xFF)
            }
            return value
        }

        private fun requireRemaining(length: Int) {
            require(length <= bytes.size - offset) { "Truncated instruction plaintext" }
        }
    }
}
