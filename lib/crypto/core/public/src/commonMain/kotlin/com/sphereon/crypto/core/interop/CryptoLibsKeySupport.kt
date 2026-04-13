/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.interop

import at.asitplus.signum.indispensable.CryptoPrivateKey
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import at.asitplus.signum.indispensable.asn1.encoding.readAsn1Element
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1Integer
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.toBigInteger
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.pki.X509Certificate
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import kotlinx.datetime.Instant
import org.kotlincrypto.hash.sha1.SHA1
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.Jwk.Builder
import com.sphereon.crypto.core.x509.BEGIN_CERTIFICATE_PEM_HEADER
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.END_CERTIFICATE_PEM_FOOTER
import com.sphereon.crypto.core.x509.KeyUsage
import com.sphereon.crypto.core.x509.getKeyUsageContent
import com.sphereon.crypto.core.x509.getSubjectAlternativeName
import com.sphereon.crypto.core.x509.toX500
import at.asitplus.signum.indispensable.Digest as SignumDigest
import at.asitplus.signum.indispensable.SignatureAlgorithm as SignumSignatureAlgorithm


/**
 * These functions mainly serve as conversions and interop between our crypto implementation and 2 external projects being used for the actual low level crypto:
 * - A-sit plus signum
 * - kmp-crypto
 */


/**
 * Converts the provided `KeyInfo` instance to a `KeyInfo` instance containing a `Jwk`.
 *
 * @param keyInfo The original `KeyInfo` instance to be converted to `KeyInfo<Jwk>`.
 * @return A `KeyInfo` instance containing a `Jwk` generated from the provided `KeyInfo`.
 * @throws IllegalArgumentException If the key is not present in the resulting `KeyInfo<Jwk>`.
 */
fun toKeyInfoJwk(keyInfo: KeyInfoType<*>): KeyInfoType<Jwk> {
    val keyInfoJwk = CoseJoseKeyMappingService.toJwkKeyInfo(keyInfo)
    val key =
        keyInfoJwk.key
            ?: throw IllegalArgumentException("Looking up keys by kid is not supported yet. Please provide a public or private key")
    if (key.kty == JwaKeyType.EC) {
        assertValidECJwk(key)
    } else if (key.kty == JwaKeyType.RSA) {
        assertValidRSAJwk(key)
    }
    return keyInfoJwk
}


/**
 * Represents the context information needed for key operations in ECDSA cryptography. Mainly usable internally when accessing crypto libs
 *
 * @property key The JSON Web Key (JWK) representation of the cryptographic key.
 * @property publicKeyBytes The byte-array representation of the cryptographic key.
 * @property curveImpl The elliptic curve implementation used for cryptographic operations.
 * @property algImpl The cryptography algorithm identifier tied to a specific digest.
 */
@Suppress("NON_EXPORTABLE_TYPE")
data class DerKmpKeyInfoContext(
    val key: Jwk,
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray?,
    val curveImpl: EC.Curve? = null,
    val algImpl: CryptographyAlgorithmId<Digest>
)


fun SignatureAlgorithm.toSignumAlgorithm(): SignumSignatureAlgorithm {
    return when (this) {
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
}

fun SignumSignatureAlgorithm.toSignatureAlgorithm(): SignatureAlgorithm {
    return when (this) {
        SignumSignatureAlgorithm.RSAwithSHA256andPKCS1Padding -> SignatureAlgorithm.RSA_SHA256
        SignumSignatureAlgorithm.RSAwithSHA384andPKCS1Padding -> SignatureAlgorithm.RSA_SHA384
        SignumSignatureAlgorithm.RSAwithSHA512andPKCS1Padding -> SignatureAlgorithm.RSA_SHA512
        SignumSignatureAlgorithm.RSAwithSHA256andPSSPadding -> SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1
        SignumSignatureAlgorithm.RSAwithSHA384andPSSPadding -> SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1
        SignumSignatureAlgorithm.RSAwithSHA512andPSSPadding -> SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1
        SignumSignatureAlgorithm.ECDSAwithSHA256 -> SignatureAlgorithm.ECDSA_SHA256
        SignumSignatureAlgorithm.ECDSAwithSHA384 -> SignatureAlgorithm.ECDSA_SHA384
        SignumSignatureAlgorithm.ECDSAwithSHA512 -> SignatureAlgorithm.ECDSA_SHA512
        else -> throw IllegalArgumentException("Algorithm $this not supported")
    }
}


fun SignatureAlgorithm.toJwaAlgorithm(): JwaAlgorithm {
    return when (this) {
        SignatureAlgorithm.RSA_SHA256 -> JwaAlgorithm.RS256
        SignatureAlgorithm.RSA_SHA384 -> JwaAlgorithm.RS384
        SignatureAlgorithm.RSA_SHA512 -> JwaAlgorithm.RS512
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> JwaAlgorithm.PS256
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> JwaAlgorithm.PS384
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> JwaAlgorithm.PS512
        SignatureAlgorithm.ECDSA_SHA256 -> JwaAlgorithm.ES256
        SignatureAlgorithm.ECDSA_SHA384 -> JwaAlgorithm.ES384
        SignatureAlgorithm.ECDSA_SHA512 -> JwaAlgorithm.ES512
        else -> throw IllegalArgumentException("Algorithm $this cannot be mapped to JwaAlgorithm")
    }
}

fun DigestAlg.toSignumAlgorithm(): SignumDigest {
    return when (this) {
        DigestAlg.SHA256 -> SignumDigest.SHA256
        DigestAlg.SHA384 -> SignumDigest.SHA384
        DigestAlg.SHA512 -> SignumDigest.SHA512
        else -> throw IllegalArgumentException("Algorithm $this not supported by signum library")
    }
}

fun SignumDigest.toDigestAlg(): DigestAlg {
    return when (this) {
        SignumDigest.SHA256 -> DigestAlg.SHA256
        SignumDigest.SHA384 -> DigestAlg.SHA384
        SignumDigest.SHA512 -> DigestAlg.SHA512
        else -> throw IllegalArgumentException("Algorithm $this not supported")
    }
}

fun X509Certificate.getPublicKeyJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false
): JwkType {
    return publicKey.toJwk(x5c, alg, generateKid)
}

fun X509Certificate.getPublicKeyBytes(): ByteArray {
    return publicKey.encodeToDer()
}


fun X509Certificate.toCertificateDto() = certificateFromSignumX509Certificate(this, this.encodeToDer())

fun Certificate.getPublicKeyJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false
): JwkType {
    return toSignumX509Certificate().getPublicKeyJwk(x5c = x5c, alg = alg, generateKid = generateKid)
}

fun Certificate.toSignumX509Certificate(): X509Certificate =
    signumX509CertificateFromDer(der)

fun signumX509CertificateFromBase64(input: String): X509Certificate =
    signumX509CertificateFromDer(input.decodeFromBase64())

/**
 * Parse DER-encoded X509Certificate bytes using a safe kotlinx.io Buffer path.
 *
 * Signum 3.16.x uses UnsafeBufferOperations.moveToTail() in its decodeFromByteArray(),
 * which is broken on JS due to Int8Array/Uint8Array confusion in kotlinx-io.
 * This function uses Buffer.write() (the safe path) to avoid the issue.
 */
fun signumX509CertificateFromDer(der: ByteArray): X509Certificate {
    // Try signum's native parser first (returns null for invalid certs, works on JVM/native).
    // Wrap in try-catch because some invalid inputs cause it to throw instead of returning null.
    try {
        X509Certificate.decodeFromByteArray(der)?.let { return it }
    } catch (_: Throwable) { /* fall through to safe path */ }

    // Fallback: use safe kotlinx.io Buffer.write() path.
    // Signum's decodeFromByteArray uses UnsafeBufferOperations.moveToTail()
    // which is broken on JS with Int8Array. The safe Buffer.write() works.
    try {
        val source = kotlinx.io.Buffer().apply { write(der) }
        val element = source.readAsn1Element().first
        return X509Certificate.decodeFromTlv(element as Asn1Sequence)
    } catch (t: Throwable) {
        throw IllegalArgumentException("Invalid certificate data", t)
    }
}

/** Encode a Signum's X509Certificate into a Base64 string (no PEM headers). */
fun signumX509CertificateToDerAsBase64(cert: X509Certificate): String =
    cert.encodeToDer()
        .encodeToBase64()


fun signumX509CertificateToPem(cert: X509Certificate): String =
    cert.encodeToPEM().getOrThrow()


fun signumX509CertificateFromPem(input: String): X509Certificate =
    X509Certificate.decodeFromPem(input)
        .getOrThrow()


/** Encode a Signum's X509Certificate list into a Base64 string list (no PEM headers). */
fun signumX509CertificateChainToX5c(chain: List<X509Certificate>): Array<String> =
    chain.map { cert -> signumX509CertificateToDerAsBase64(cert) }.toTypedArray()


internal fun certificateFromSignumX509Certificate(
    x509: X509Certificate,
    derBytes: ByteArray = x509.encodeToDer()
): Certificate =
    with(x509.tbsCertificate) {
        Certificate(
            der = derBytes,
            fingerPrint = SHA1().digest(derBytes).encodeTo(Encoding.HEX).uppercase(),
            serialNumber = serialNumber.encodeTo(Encoding.HEX).uppercase(),
            issuerDN = issuerName.toX500(),
            subjectDN = subjectName.toX500(),
            notBefore = Instant.fromEpochSeconds(validFrom.instant.epochSeconds, validFrom.instant.nanosecondsOfSecond),
            notAfter = Instant.fromEpochSeconds(validUntil.instant.epochSeconds, validUntil.instant.nanosecondsOfSecond),
            keyUsage = getKeyUsageContent(extensions)?.let { KeyUsage.fromDerBitString(it) },
            subjectAlternativeNames = getSubjectAlternativeName(extensions)
        )
    }

/** Decode a PEM-encoded certificate chain into Signum's X509Certificate list. */
fun signumX509CertificateChainFromPem(input: String): List<X509Certificate> =
    Regex("$BEGIN_CERTIFICATE_PEM_HEADER[\\s\\S]*?$END_CERTIFICATE_PEM_FOOTER")
        .findAll(input)
        .map { signumX509CertificateFromPem(it.value) }
        .toList()

fun signumX509CertificateToDer(cert: X509Certificate) = cert.encodeToDer()

fun derToSignumX509Certificate(input: ByteArray) = signumX509CertificateFromDer(input)

fun derPrivateKeyToJwk(privateKeyDer: ByteArray): Jwk = CryptoPrivateKey.decodeFromDer(privateKeyDer).toJwk()
fun derPublicKeyToJwk(publicKeyDer: ByteArray): Jwk = CryptoPublicKey.decodeFromDer(publicKeyDer).toJwk()


/**
 * Constructs an RSA public key from its ASN.1 integer components.
 *
 * @param n the RSA modulus as an ASN.1 integer
 * @param e the RSA public exponent as an ASN.1 integer
 * @return a [at.asitplus.signum.indispensable.CryptoPublicKey] representing the RSA public key
 */
fun publicKeyRSAFrom(n: Asn1Integer, e: Asn1Integer): CryptoPublicKey {
    val pkcs1Bytes = Asn1.Sequence {
        +Asn1.Int(n)
        +Asn1.Int(e)
    }.derEncoded

    return CryptoPublicKey.RSA.fromPKCS1encoded(pkcs1Bytes)
}

/**
 * Constructs an RSA public key from its ASN.1 integer components and returns it
 * encoded in PEM format.
 *
 * @param n the RSA modulus as an ASN.1 integer
 * @param e the RSA public exponent as an ASN.1 integer
 * @return the PEM-encoded RSA public key string
 */
fun publicKeyRSAPemFrom(n: ByteArray, e: ByteArray): String {
    val nInt = Asn1Integer.fromByteArray(n, Asn1Integer.Sign.POSITIVE)
    val eInt = Asn1Integer.fromByteArray(e, Asn1Integer.Sign.POSITIVE)
    val publicKey: CryptoPublicKey = publicKeyRSAFrom(nInt, eInt)

    return publicKey
        .encodeToPEM()
        .getOrThrow()
}

/**
 * Constructs an EC public key from its uncompressed point coordinates.
 *
 * @param crv the elliptic curve identifier
 * @param xBytes the X coordinate of the EC point, in big-endian byte array form
 * @param yBytes the Y coordinate of the EC point, in big-endian byte array form
 * @return a [CryptoPublicKey] representing the EC public key
 */
fun publicKeyECFrom(crv: ECCurve, xBytes: ByteArray, yBytes: ByteArray): CryptoPublicKey {
    return CryptoPublicKey.EC.fromUncompressed(crv, xBytes, yBytes)
}

/**
 * Constructs an EC public key from its uncompressed point coordinates and returns it
 * encoded in PEM format.
 *
 * @param crv the elliptic curve identifier
 * @param xBytes the X coordinate of the EC point, in big-endian byte array form
 * @param yBytes the Y coordinate of the EC point, in big-endian byte array form
 * @return the PEM-encoded EC public key string
 * @throws CryptoException if PEM encoding fails
 */
fun publicKeyECPemFrom(curveName: String, xBytes: ByteArray, yBytes: ByteArray): String {
    val publicKey = publicKeyECFrom(ECCurve.fromJwkName(curveName), xBytes, yBytes)

    return publicKey
        .encodeToPEM()
        .getOrThrow()
}

/**
 * Factory function that looks up an ECCurve by its JSON Web Key (JWK) name.
 *
 * @param jwkName the standard JWK identifier for the elliptic curve ("P-256", "P-384" and "P-521")
 * @return the matching ECCurve enum entry
 * @throws IllegalArgumentException if no curve with the given JWK name exists
 */
fun ECCurve.Companion.fromJwkName(jwkName: String): ECCurve {
    return ECCurve.entries.firstOrNull { it.jwkName == jwkName }
        ?: throw IllegalArgumentException("Unknown EC curve name: $jwkName")
}


private fun BigInteger.toJwkProp(): String = toByteArray().encodeToBase64Url()
private fun ModularBigInteger.toJwkProp(): String = toByteArray().encodeToBase64Url()
private fun ByteArray.toJwkProp(): String = encodeToBase64Url()

fun CryptoPrivateKey.toJwk(x5c: Array<String>? = null): Jwk =
    when (this) {
        is CryptoPrivateKey.RSA -> Builder()
            .withKty(JwaKeyType.RSA)
            .withD(privateKey.toJwkProp())
            .withP(prime1.toJwkProp())
            .withQ(prime2.toJwkProp())
            .withDP(prime1exponent.toJwkProp())
            .withDQ(prime2exponent.toJwkProp())
            .withN(publicKey.n.magnitude.toJwkProp())
            .withE(publicKey.e.magnitude.toJwkProp())
            .withQInv(crtCoefficient.toJwkProp())
            .withX5c(x5c)
            .build()

        is CryptoPrivateKey.EC.WithPublicKey -> Builder()
            .withKty(JwaKeyType.EC)
            .withD(privateKey.toJwkProp())
            .withX(publicKey.x.toJwkProp())
            .withY(publicKey.y.toJwkProp())
            .withCrv(publicKey.curve.fromSignum())
            .withX5c(x5c)
            .build()

        is CryptoPrivateKey.EC.WithoutPublicKey -> Builder()
            .withKty(JwaKeyType.EC)
            .withD(privateKey.toJwkProp())
            // TODO Crv
            .withX5c(x5c)
            .build()
    }


fun Jwk.toSignumPublicKey(): CryptoPublicKey =
    JsonWebKey.deserialize(toJsonString()).getOrThrow().toCryptoPublicKey().getOrThrow()


fun Jwk.toSignumPrivateKey(): CryptoPrivateKey {
    require(d != null) { "Private key is missing" }
    val signumPubKey = toSignumPublicKey()
    fun toSignumKeyProp(prop: String? = null): BigInteger {
        return Asn1Integer.fromUnsignedByteArray(prop?.decodeFromBase64Url() ?: byteArrayOf()).toBigInteger()
    }
    return when (kty) {
        JwaKeyType.RSA -> {
            CryptoPrivateKey.RSA(
                publicKey = signumPubKey as CryptoPublicKey.RSA,
                privateKey = toSignumKeyProp(d),
                prime1 = toSignumKeyProp(p),
                prime2 = toSignumKeyProp(q),
                prime1exponent = toSignumKeyProp(dP),
                prime2exponent = toSignumKeyProp(dQ),
                crtCoefficient = toSignumKeyProp(qInv),
                otherPrimeInfos = null
            )
        }

        JwaKeyType.EC -> {
            CryptoPrivateKey.EC.WithPublicKey(
                publicKey = signumPubKey as CryptoPublicKey.EC, privateKey = BigInteger.fromByteArray(
                    d.decodeFromBase64Url(),
                    Sign.POSITIVE
                ),
                encodeCurve = false,
                encodePublicKey = false
            )
        }

        else -> {
            throw IllegalArgumentException("Unsupported key type: $kty")
        }
    }
}

fun ECCurve.fromSignum(): JwaCurve =
    JwaCurve.fromValue(jwkName) ?: throw IllegalArgumentException("Unknown curve: $jwkName")

fun JwaCurve.toSignum(): ECCurve = ECCurve.fromJwkName(this.value)


fun CryptoPublicKey.toJwk(x5c: Array<String>? = null, alg: JwaAlgorithm? = null, generateKid: Boolean? = false): Jwk =
    when (this) {
        is CryptoPublicKey.RSA -> {
            val size = CryptoPublicKey.RSA.Size.of(this.bits.number)
            Builder()
                .withGenerateKid(generateKid)
                .withKty(JwaKeyType.RSA)
                .withAlg(
                    alg ?: when (size) {
                        CryptoPublicKey.RSA.Size.RSA_2048 -> JwaAlgorithm.PS256
                        CryptoPublicKey.RSA.Size.RSA_3027 -> JwaAlgorithm.PS384
                        CryptoPublicKey.RSA.Size.RSA_4096 -> JwaAlgorithm.PS512
                        else -> null
                    }
                )
                .withE(e.magnitude.toJwkProp())
                .withN(n.magnitude.toJwkProp())
                .withX5c(x5c)
                .build()
        }

        is CryptoPublicKey.EC -> Builder()
            .withGenerateKid(generateKid)
            .withKty(JwaKeyType.EC)
            .withAlg(
                when (curve.fromSignum()) {
                    JwaCurve.P_256 -> JwaAlgorithm.ES256
                    JwaCurve.P_384 -> JwaAlgorithm.ES384
                    JwaCurve.P_521 -> JwaAlgorithm.ES512
                    else -> null
                }
            )
            .withX(x.toJwkProp())
            .withY(y.toJwkProp())
            .withCrv(curve.fromSignum())
            .withX5c(x5c)
            .build()

    }

fun X509SignatureAlgorithm.toSignatureAlgorithm(): SignatureAlgorithm {
    return when (this) {
        X509SignatureAlgorithm.RS256 -> SignatureAlgorithm.RSA_SHA256
        X509SignatureAlgorithm.RS384 -> SignatureAlgorithm.RSA_SHA384
        X509SignatureAlgorithm.RS512 -> SignatureAlgorithm.RSA_SHA512
        X509SignatureAlgorithm.PS256 -> SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1
        X509SignatureAlgorithm.PS384 -> SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1
        X509SignatureAlgorithm.PS512 -> SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1
        X509SignatureAlgorithm.ES256 -> SignatureAlgorithm.ECDSA_SHA256
        X509SignatureAlgorithm.ES384 -> SignatureAlgorithm.ECDSA_SHA384
        X509SignatureAlgorithm.ES512 -> SignatureAlgorithm.ECDSA_SHA512
        X509SignatureAlgorithm.RS1 -> throw IllegalArgumentException("SHA1 signature algorithm (RS1) is not supported")
    }
}
