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
 *
 * Bridge functions for signum/supreme integration in the mobile KMS provider.
 * These were previously in crypto-core-public but moved here when signum was
 * replaced with awesn1 in the core modules. The mobile module keeps signum
 * for OS-level key management via supreme.
 */

package com.sphereon.crypto.kms.provider.mobile

import at.asitplus.awesn1.serialization.DER
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.encodeToByteArray
import at.asitplus.signum.indispensable.Digest as SignumDigest
import at.asitplus.signum.indispensable.SignatureAlgorithm as SignumSignatureAlgorithm

internal fun resolveEcdsaSignumCurve(curve: Curve): ECCurve =
    when (curve) {
        Curve.P_256 -> ECCurve.SECP_256_R_1
        Curve.P_384 -> ECCurve.SECP_384_R_1
        Curve.P_521 -> ECCurve.SECP_521_R_1
        else -> throw IllegalArgumentException("Curve $curve not supported")
    }

internal fun JwaCurve.toSignum(): ECCurve =
    when (this) {
        JwaCurve.P_256 -> ECCurve.SECP_256_R_1
        JwaCurve.P_384 -> ECCurve.SECP_384_R_1
        JwaCurve.P_521 -> ECCurve.SECP_521_R_1
        else -> throw IllegalArgumentException("Unknown curve: $this")
    }

internal fun SignatureAlgorithm.toSignumAlgorithm(): SignumSignatureAlgorithm =
    when (this) {
        SignatureAlgorithm.RSA_SHA256 -> SignumSignatureAlgorithm.RSAwithSHA256andPKCS1Padding
        SignatureAlgorithm.RSA_SHA384 -> SignumSignatureAlgorithm.RSAwithSHA384andPKCS1Padding
        SignatureAlgorithm.RSA_SHA512 -> SignumSignatureAlgorithm.RSAwithSHA512andPKCS1Padding
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> SignumSignatureAlgorithm.RSAwithSHA256andPSSPadding
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> SignumSignatureAlgorithm.RSAwithSHA384andPSSPadding
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> SignumSignatureAlgorithm.RSAwithSHA512andPSSPadding
        SignatureAlgorithm.ECDSA_SHA256 -> SignumSignatureAlgorithm.ECDSAwithSHA256
        SignatureAlgorithm.ECDSA_SHA384 -> SignumSignatureAlgorithm.ECDSAwithSHA384
        SignatureAlgorithm.ECDSA_SHA512 -> SignumSignatureAlgorithm.ECDSAwithSHA512
        SignatureAlgorithm.RSA_RAW -> SignumSignatureAlgorithm.RSAwithSHA256andPKCS1Padding
        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1 -> SignumSignatureAlgorithm.RSAwithSHA256andPSSPadding
        else -> throw IllegalArgumentException("Algorithm $this not supported by signum library")
    }

internal fun DigestAlg.toSignumAlgorithm(): SignumDigest =
    when (this) {
        DigestAlg.SHA256 -> SignumDigest.SHA256
        DigestAlg.SHA384 -> SignumDigest.SHA384
        DigestAlg.SHA512 -> SignumDigest.SHA512
        else -> throw IllegalArgumentException("Algorithm $this not supported by signum library")
    }

internal fun Jwk.toSignumPublicKey(): CryptoPublicKey = CryptoPublicKey.decodeFromDer(DER.encodeToByteArray(toSubjectPublicKeyInfo()))

internal fun CryptoPublicKey.toJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false,
): Jwk =
    when (this) {
        is CryptoPublicKey.RSA -> {
            val size = CryptoPublicKey.RSA.Size.of(this.bits.number)
            Jwk
                .Builder()
                .withGenerateKid(generateKid)
                .withKty(JwaKeyType.RSA)
                .withAlg(
                    alg ?: when (size) {
                        CryptoPublicKey.RSA.Size.RSA_2048 -> JwaAlgorithm.PS256
                        CryptoPublicKey.RSA.Size.RSA_3027 -> JwaAlgorithm.PS384
                        CryptoPublicKey.RSA.Size.RSA_4096 -> JwaAlgorithm.PS512
                        else -> null
                    },
                ).withE(e.magnitude.encodeToBase64Url())
                .withN(n.magnitude.encodeToBase64Url())
                .withX5c(x5c)
                .build()
        }

        is CryptoPublicKey.EC -> {
            Jwk
                .Builder()
                .withGenerateKid(generateKid)
                .withKty(JwaKeyType.EC)
                .withAlg(
                    alg ?: when (curve.jwkName) {
                        "P-256" -> JwaAlgorithm.ES256
                        "P-384" -> JwaAlgorithm.ES384
                        "P-521" -> JwaAlgorithm.ES512
                        else -> null
                    },
                ).withX(x.toByteArray().encodeToBase64Url())
                .withY(y.toByteArray().encodeToBase64Url())
                .withCrv(JwaCurve.fromValue(curve.jwkName))
                .withX5c(x5c)
                .build()
        }
    }
