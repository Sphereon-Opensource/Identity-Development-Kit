/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.mdoc.vical

import at.asitplus.awesn1.crypto.pki.X509Certificate
import at.asitplus.awesn1.serialization.DER
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.resolveEcdsaKmpDigest
import com.sphereon.crypto.core.interop.toEcdsaPublicKey
import com.sphereon.crypto.core.interop.toSignatureAlgorithm
import com.sphereon.crypto.core.kms.command.SignatureEncodingCodec
import com.sphereon.crypto.core.x509.X509ExtensionOids
import com.sphereon.crypto.core.x509.certificateFromDer
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA1
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Instant

/**
 * Security-minimum authentication for an IACA carried by a signer-authenticated VICAL.
 * This intentionally does not claim complete ISO/IEC 18013-5 certificate-profile validation.
 */
internal object AuthenticatedVicalIacaValidator {
    private val understoodCriticalExtensions =
        setOf(X509ExtensionOids.BASIC_CONSTRAINTS, X509ExtensionOids.KEY_USAGE)

    @OptIn(DelicateCryptographyApi::class)
    suspend fun validate(
        certificateDer: ByteArray,
        verificationTimeEpochSeconds: Long,
    ) {
        val x509 = DER.decodeFromByteArray<X509Certificate>(certificateDer)
        require(DER.encodeToByteArray(x509).contentEquals(certificateDer)) { "VICAL IACA certificate is not canonical DER" }
        require(x509.signatureAlgorithm == x509.tbsCertificate.signatureAlgorithm) {
            "VICAL IACA inner and outer signature algorithms differ"
        }
        require(x509.tbsCertificate.issuerName == x509.tbsCertificate.subjectName) { "VICAL IACA must be self-issued" }

        val certificate = certificateFromDer(certificateDer)
        val verificationTime = Instant.fromEpochSeconds(verificationTimeEpochSeconds)
        require(certificate.notBefore <= verificationTime && verificationTime < certificate.notAfter) {
            "VICAL IACA is not valid at the policy verification time"
        }

        val extensions = x509.tbsCertificate.extensions.orEmpty()
        val grouped = extensions.groupBy { it.oid.toString() }
        require(grouped.values.none { it.size > 1 }) { "VICAL IACA contains duplicate certificate extensions" }
        val unsupportedCritical = extensions.filter { it.critical && it.oid.toString() !in understoodCriticalExtensions }
        require(unsupportedCritical.isEmpty()) { "VICAL IACA contains an unsupported critical extension" }

        val basicConstraints = requireNotNull(grouped[X509ExtensionOids.BASIC_CONSTRAINTS]?.singleOrNull()) {
            "VICAL IACA requires Basic Constraints"
        }
        require(basicConstraints.critical) { "VICAL IACA Basic Constraints must be critical" }
        require(basicConstraints.value.contentEquals(byteArrayOf(0x30, 0x06, 0x01, 0x01, 0xff.toByte(), 0x02, 0x01, 0x00))) {
            "VICAL IACA Basic Constraints must canonically encode CA=true and pathLenConstraint=0"
        }

        val keyUsage = requireNotNull(grouped[X509ExtensionOids.KEY_USAGE]?.singleOrNull()) {
            "VICAL IACA requires Key Usage"
        }
        require(keyUsage.critical) { "VICAL IACA Key Usage must be critical" }
        require(keyUsage.value.contentEquals(byteArrayOf(0x03, 0x02, 0x01, 0x06))) {
            "VICAL IACA Key Usage must contain exactly keyCertSign and cRLSign"
        }

        val ski = requireNotNull(grouped[X509ExtensionOids.SUBJECT_KEY_IDENTIFIER]?.singleOrNull()) {
            "VICAL IACA requires Subject Key Identifier"
        }
        require(!ski.critical && ski.value.size == 22 && ski.value[0] == 0x04.toByte() && ski.value[1] == 0x14.toByte()) {
            "VICAL IACA Subject Key Identifier must be a non-critical 20-byte DER OCTET STRING"
        }
        val expectedSki = CryptographyProvider.Default.get(SHA1).hasher()
            .hash(x509.tbsCertificate.subjectPublicKeyInfo.subjectPublicKey.bitCarryingBytes)
        require(ski.value.copyOfRange(2, 22).contentEquals(expectedSki)) {
            "VICAL IACA Subject Key Identifier does not match its subjectPublicKey BIT STRING"
        }

        val algorithm = x509.signatureAlgorithm.toSignatureAlgorithm()
        require(algorithm in supportedEcdsaAlgorithms) { "VICAL IACA signature algorithm is not a supported ECDSA algorithm" }
        val publicJwk = x509.getPublicKeyJwk()
        val curve = Curve.fromJose(requireNotNull(publicJwk.crv) { "VICAL IACA EC curve is missing" })
        val scalarLength = when (curve) {
            Curve.P_256 -> 32
            Curve.P_384 -> 48
            Curve.P_521 -> 66
            else -> error("VICAL IACA EC curve is not supported")
        }
        val rawSignature = SignatureEncodingCodec.derToRaw(x509.signatureValue.rawBytes, scalarLength)
        val publicKey = publicJwk.toEcdsaPublicKey(
            curve = resolveEcdsaKmpCurve(curve),
        )
        require(
            publicKey.signatureVerifier(
                digest = resolveEcdsaKmpDigest(algorithm),
                format = ECDSA.SignatureFormat.RAW,
            ).tryVerifySignature(DER.encodeToByteArray(x509.tbsCertificate), rawSignature),
        ) { "VICAL IACA self-signature is invalid" }
    }

    private val supportedEcdsaAlgorithms =
        setOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.ECDSA_SHA512,
        )
}
