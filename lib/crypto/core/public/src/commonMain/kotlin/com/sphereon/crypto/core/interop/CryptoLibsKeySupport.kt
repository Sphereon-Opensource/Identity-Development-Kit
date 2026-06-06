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

package com.sphereon.crypto.core.interop

import at.asitplus.awesn1.Asn1BitString
import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Integer
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.ObjectIdentifier
import at.asitplus.awesn1.crypto.Pkcs1RsaPrivateKeyInfo
import at.asitplus.awesn1.crypto.Pkcs8PrivateKeyInfo
import at.asitplus.awesn1.crypto.Sec1EcPrivateKeyInfo
import at.asitplus.awesn1.crypto.SubjectPublicKeyInfo
import at.asitplus.awesn1.crypto.X509AlgorithmIdentifier
import at.asitplus.awesn1.crypto.X509SignatureValue
import at.asitplus.awesn1.crypto.pki.X509Certificate
import at.asitplus.awesn1.encoding.Asn1
import at.asitplus.awesn1.encoding.parse
import at.asitplus.awesn1.serialization.DER
import at.asitplus.awesn1.serialization.encodeToPem
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.Jwk.Builder
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.x509.BEGIN_CERTIFICATE_PEM_HEADER
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.END_CERTIFICATE_PEM_FOOTER
import com.sphereon.crypto.core.x509.KeyUsage
import com.sphereon.crypto.core.x509.getKeyUsageContent
import com.sphereon.crypto.core.x509.getSubjectAlternativeName
import com.sphereon.crypto.core.x509.toX500
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha1.SHA1
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

// ============================================================================
// OID Constants
// ============================================================================

private val RSA_ENCRYPTION_OID = ObjectIdentifier("1.2.840.113549.1.1.1")
private val EC_PUBLIC_KEY_OID = ObjectIdentifier("1.2.840.10045.2.1")

// Single-source EC curve mapping — both directions derived from one list
private val EC_CURVES =
    listOf(
        Triple("P-256", ObjectIdentifier("1.2.840.10045.3.1.7"), JwaCurve.P_256),
        Triple("P-384", ObjectIdentifier("1.3.132.0.34"), JwaCurve.P_384),
        Triple("P-521", ObjectIdentifier("1.3.132.0.35"), JwaCurve.P_521),
    )
private val EC_CURVE_OIDS = EC_CURVES.associate { (name, oid, _) -> name to oid }

// EC coordinate byte sizes per curve (field element size in bytes)
private val EC_COORD_SIZES = mapOf("P-256" to 32, "P-384" to 48, "P-521" to 66)

/**
 * Pads an EC coordinate to the required byte length for the curve.
 * JWK base64url decoding can strip leading zero bytes, but the uncompressed
 * point format requires fixed-size coordinates.
 */
private fun padCoordinate(
    bytes: ByteArray,
    requiredSize: Int,
): ByteArray =
    if (bytes.size >= requiredSize) {
        bytes
    } else {
        ByteArray(requiredSize - bytes.size) + bytes
    }

private fun ecUncompressedPoint(
    curveName: String,
    xBytes: ByteArray,
    yBytes: ByteArray,
): ByteArray {
    val coordSize = EC_COORD_SIZES[curveName]
    return if (coordSize != null) {
        byteArrayOf(0x04) + padCoordinate(xBytes, coordSize) + padCoordinate(yBytes, coordSize)
    } else {
        byteArrayOf(0x04) + xBytes + yBytes
    }
}

private val EC_OID_TO_CURVE = EC_CURVES.associate { (_, oid, curve) -> oid to curve }

// Single-source signature algorithm mapping — both directions derived from one list

// RSA-PSS uses a single OID (1.2.840.113549.1.1.10) with ASN.1 parameters specifying
// hash algorithm, mask generation function, and salt length (RFC 4055).
private val RSASSA_PSS_OID = ObjectIdentifier("1.2.840.113549.1.1.10")
private val SHA256_OID = ObjectIdentifier("2.16.840.1.101.3.4.2.1")
private val SHA384_OID = ObjectIdentifier("2.16.840.1.101.3.4.2.2")
private val SHA512_OID = ObjectIdentifier("2.16.840.1.101.3.4.2.3")
private val MGF1_OID = ObjectIdentifier("1.2.840.113549.1.1.8")

private fun rsaPssParams(
    hashOid: ObjectIdentifier,
    saltLength: Int,
): List<Asn1Element> =
    listOf(
        Asn1.Sequence {
            +Asn1.ExplicitlyTagged(0u) { +Asn1.Sequence { +hashOid } }
            +Asn1.ExplicitlyTagged(1u) {
                +Asn1.Sequence {
                    +MGF1_OID
                    +Asn1.Sequence { +hashOid }
                }
            }
            +Asn1.ExplicitlyTagged(2u) { +Asn1.Int(saltLength) }
        },
    )

private val SIGNATURE_ALGS =
    listOf(
        Triple(SignatureAlgorithm.RSA_SHA256, ObjectIdentifier("1.2.840.113549.1.1.11"), listOf(Asn1.Null())),
        Triple(SignatureAlgorithm.RSA_SHA384, ObjectIdentifier("1.2.840.113549.1.1.12"), listOf(Asn1.Null())),
        Triple(SignatureAlgorithm.RSA_SHA512, ObjectIdentifier("1.2.840.113549.1.1.13"), listOf(Asn1.Null())),
        Triple(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1, RSASSA_PSS_OID, rsaPssParams(SHA256_OID, 32)),
        Triple(SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1, RSASSA_PSS_OID, rsaPssParams(SHA384_OID, 48)),
        Triple(SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1, RSASSA_PSS_OID, rsaPssParams(SHA512_OID, 64)),
        Triple(SignatureAlgorithm.ECDSA_SHA256, ObjectIdentifier("1.2.840.10045.4.3.2"), emptyList()),
        Triple(SignatureAlgorithm.ECDSA_SHA384, ObjectIdentifier("1.2.840.10045.4.3.3"), emptyList()),
        Triple(SignatureAlgorithm.ECDSA_SHA512, ObjectIdentifier("1.2.840.10045.4.3.4"), emptyList()),
    )
private val SIGNATURE_ALG_OIDS = SIGNATURE_ALGS.associate { (alg, oid, params) -> alg to Pair(oid, params) }
private val OID_TO_SIGNATURE_ALG =
    SIGNATURE_ALGS.associate { (alg, oid, _) -> oid to alg } +
        mapOf(
            // RSA-PSS uses a single OID; default reverse mapping to SHA-256 variant
            RSASSA_PSS_OID to SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
            // SHA-1 with RSA (legacy, map to SHA-256)
            ObjectIdentifier("1.2.840.113549.1.1.5") to SignatureAlgorithm.RSA_SHA256,
        )

/*
 * These functions serve as conversions and interop between our crypto implementation and external libraries:
 * - A-SIT Plus awesn1 (ASN.1 structural types, X.509, PKI)
 * - cryptography-kotlin (key generation, signing, encryption)
 */

/**
 * Converts the provided `KeyInfo` instance to a `KeyInfo` instance containing a `Jwk`.
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
 * Represents the context information needed for key operations in cryptography. Used internally when accessing crypto libs.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExportCompat
data class
DerKmpKeyInfoContext
    @JvmOverloads
    constructor(
        val key: Jwk,
        val publicKeyBytes: ByteArray,
        val privateKeyBytes: ByteArray?,
        val curveImpl: EC.Curve? = null,
        val algImpl: CryptographyAlgorithmId<Digest>,
    )

// ============================================================================
// Signature Algorithm Mapping
// ============================================================================

fun SignatureAlgorithm.toSignatureAlgorithmIdentifier(): X509AlgorithmIdentifier {
    val (oid, params) =
        SIGNATURE_ALG_OIDS[this]
            ?: throw IllegalArgumentException("Algorithm $this not supported for X.509 signature")
    return X509AlgorithmIdentifier(oid, params)
}

fun X509AlgorithmIdentifier.toSignatureAlgorithm(): SignatureAlgorithm =
    OID_TO_SIGNATURE_ALG[oid]
        ?: throw IllegalArgumentException("Unknown signature algorithm OID: $oid")

fun SignatureAlgorithm.toJwaAlgorithm(): JwaAlgorithm =
    when (this) {
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

// ============================================================================
// X.509 Certificate ↔ JWK
// ============================================================================

fun X509Certificate.getPublicKeyJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false,
): JwkType = tbsCertificate.subjectPublicKeyInfo.toJwk(x5c, alg, generateKid)

fun X509Certificate.getPublicKeyBytes(): ByteArray = DER.encodeToByteArray(tbsCertificate.subjectPublicKeyInfo)

fun X509Certificate.toCertificateDto() = certificateFromX509Certificate(this, DER.encodeToByteArray(this))

fun Certificate.getPublicKeyJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false,
): JwkType = toX509Certificate().getPublicKeyJwk(x5c = x5c, alg = alg, generateKid = generateKid)

fun Certificate.toX509Certificate(): X509Certificate = x509CertificateFromDer(der)

// ============================================================================
// X.509 Certificate Parsing/Encoding
// ============================================================================

fun x509CertificateFromBase64(input: String): X509Certificate = x509CertificateFromDer(input.decodeFromBase64())

/**
 * Parse DER-encoded X509Certificate bytes using awesn1.
 */
fun x509CertificateFromDer(der: ByteArray): X509Certificate {
    try {
        return DER.decodeFromByteArray<X509Certificate>(der)
    } catch (expected: Throwable) {
        throw IllegalArgumentException("Invalid certificate data", expected)
    }
}

fun x509CertificateToDerAsBase64(cert: X509Certificate): String = DER.encodeToByteArray(cert).encodeToBase64()

fun x509CertificateToPem(cert: X509Certificate): String = cert.encodeToPem()

fun x509CertificateFromPem(input: String): X509Certificate {
    val pem = input.trim()
    val base64 =
        pem
            .removePrefix(BEGIN_CERTIFICATE_PEM_HEADER)
            .removeSuffix(END_CERTIFICATE_PEM_FOOTER)
            .replace("\\s".toRegex(), "")
    return x509CertificateFromDer(base64.decodeFromBase64())
}

fun x509CertificateChainToX5c(chain: List<X509Certificate>): Array<String> = chain.map { cert -> x509CertificateToDerAsBase64(cert) }.toTypedArray()

internal fun certificateFromX509Certificate(
    x509: X509Certificate,
    derBytes: ByteArray = DER.encodeToByteArray(x509),
): Certificate =
    with(x509.tbsCertificate) {
        Certificate(
            der = derBytes,
            fingerPrint = SHA1().digest(derBytes).encodeTo(Encoding.HEX).uppercase(),
            serialNumber = serialNumber.twosComplement().encodeTo(Encoding.HEX).uppercase(),
            issuerDN = issuerName.toX500(),
            subjectDN = subjectName.toX500(),
            notBefore = Instant.fromEpochSeconds(validity.validFrom.instant.epochSeconds, validity.validFrom.instant.nanosecondsOfSecond),
            notAfter = Instant.fromEpochSeconds(validity.validUntil.instant.epochSeconds, validity.validUntil.instant.nanosecondsOfSecond),
            keyUsage = getKeyUsageContent(extensions)?.let { KeyUsage.fromDerBitString(it) },
            subjectAlternativeNames = getSubjectAlternativeName(extensions),
        )
    }

fun x509CertificateChainFromPem(input: String): List<X509Certificate> =
    Regex("$BEGIN_CERTIFICATE_PEM_HEADER[\\s\\S]*?$END_CERTIFICATE_PEM_FOOTER")
        .findAll(input)
        .map { x509CertificateFromPem(it.value) }
        .toList()

fun x509CertificateToDer(cert: X509Certificate) = DER.encodeToByteArray(cert)

fun derToX509Certificate(input: ByteArray) = x509CertificateFromDer(input)

// ============================================================================
// DER Key ↔ JWK Conversion
// ============================================================================

fun derPrivateKeyToJwk(privateKeyDer: ByteArray): Jwk {
    val pkcs8 = DER.decodeFromByteArray<Pkcs8PrivateKeyInfo>(privateKeyDer)
    return pkcs8.toJwk()
}

fun derPublicKeyToJwk(publicKeyDer: ByteArray): Jwk {
    val spki = DER.decodeFromByteArray<SubjectPublicKeyInfo>(publicKeyDer)
    return spki.toJwk()
}

// ============================================================================
// Public Key Construction
// ============================================================================

/**
 * Constructs an RSA SubjectPublicKeyInfo from its ASN.1 integer components.
 */
fun publicKeyRSAFrom(
    n: Asn1Integer,
    e: Asn1Integer,
): SubjectPublicKeyInfo = SubjectPublicKeyInfo.rsa(n, e)

/**
 * Constructs an RSA public key from its raw byte components and returns PEM.
 */
fun publicKeyRSAPemFrom(
    n: ByteArray,
    e: ByteArray,
): String {
    val nInt = Asn1Integer.fromUnsignedByteArray(n)
    val eInt = Asn1Integer.fromUnsignedByteArray(e)
    val spki = publicKeyRSAFrom(nInt, eInt)
    return spki.encodeToPem()
}

/**
 * Constructs an EC SubjectPublicKeyInfo from its uncompressed point coordinates.
 */
fun publicKeyECFrom(
    curveName: String,
    xBytes: ByteArray,
    yBytes: ByteArray,
): SubjectPublicKeyInfo {
    val curveOid =
        EC_CURVE_OIDS[curveName]
            ?: throw IllegalArgumentException("Unknown EC curve name: $curveName")
    val uncompressedPoint = ecUncompressedPoint(curveName, xBytes, yBytes)
    return SubjectPublicKeyInfo.ec(curveOid, uncompressedPoint)
}

/**
 * Constructs an EC public key from its uncompressed point coordinates and returns PEM.
 */
fun publicKeyECPemFrom(
    curveName: String,
    xBytes: ByteArray,
    yBytes: ByteArray,
): String = publicKeyECFrom(curveName, xBytes, yBytes).encodeToPem()

// ============================================================================
// EC Signature Helpers
// ============================================================================

/**
 * Converts raw EC signature bytes (r || s concatenated) to an X.509 [X509SignatureValue]
 * holding the DER-encoded `ECDSA-Sig-Value` (for the X509Certificate constructor).
 */
fun ecSignatureToX509SignatureValue(rawBytes: ByteArray): X509SignatureValue {
    val halfLen = rawBytes.size / 2
    val r = Asn1Integer.fromUnsignedByteArray(rawBytes.copyOfRange(0, halfLen))
    val s = Asn1Integer.fromUnsignedByteArray(rawBytes.copyOfRange(halfLen, rawBytes.size))
    return X509SignatureValue.fromRS(r, s)
}

// ============================================================================
// SubjectPublicKeyInfo ↔ JWK
// ============================================================================

private fun ByteArray.toJwkProp(): String = encodeToBase64Url()

fun SubjectPublicKeyInfo.toJwk(
    x5c: Array<String>? = null,
    alg: JwaAlgorithm? = null,
    generateKid: Boolean? = false,
): Jwk =
    when (algorithmOid) {
        EC_PUBLIC_KEY_OID -> {
            val curveOid =
                algorithmParameters?.let {
                    ObjectIdentifier.decodeFromTlv(it.asPrimitive())
                }
            val curve =
                EC_OID_TO_CURVE[curveOid]
                    ?: throw IllegalArgumentException("Unknown EC curve OID: $curveOid")
            val point = subjectPublicKey.bitCarryingBytes
            require(point.isNotEmpty() && point[0] == 0x04.toByte()) {
                "Expected uncompressed EC point (0x04 prefix)"
            }
            val coordSize = (point.size - 1) / 2
            val x = point.copyOfRange(1, 1 + coordSize)
            val y = point.copyOfRange(1 + coordSize, point.size)
            Builder()
                .withGenerateKid(generateKid)
                .withKty(JwaKeyType.EC)
                .withAlg(
                    alg ?: when (curve) {
                        JwaCurve.P_256 -> JwaAlgorithm.ES256
                        JwaCurve.P_384 -> JwaAlgorithm.ES384
                        JwaCurve.P_521 -> JwaAlgorithm.ES512
                        else -> null
                    },
                ).withX(x.toJwkProp())
                .withY(y.toJwkProp())
                .withCrv(curve)
                .withX5c(x5c)
                .build()
        }

        RSA_ENCRYPTION_OID -> {
            val rsaKey = decodeRsaPublicKey()
            val bitLen = rsaKey.modulus.magnitude.size * 8
            Builder()
                .withGenerateKid(generateKid)
                .withKty(JwaKeyType.RSA)
                .withAlg(
                    alg ?: when {
                        bitLen <= 2048 -> JwaAlgorithm.PS256
                        bitLen <= 3072 -> JwaAlgorithm.PS384
                        else -> JwaAlgorithm.PS512
                    },
                ).withE(rsaKey.publicExponent.magnitude.toJwkProp())
                .withN(rsaKey.modulus.magnitude.toJwkProp())
                .withX5c(x5c)
                .build()
        }

        else -> {
            throw IllegalArgumentException("Unsupported public key algorithm: $algorithmOid")
        }
    }

fun Jwk.toSubjectPublicKeyInfo(): SubjectPublicKeyInfo =
    when (kty) {
        JwaKeyType.EC -> {
            val crv = crv ?: throw IllegalArgumentException("EC key must have crv")
            val curveOid =
                EC_CURVE_OIDS[crv.value]
                    ?: throw IllegalArgumentException("Unknown curve: $crv")
            val xBytes =
                x?.decodeFromBase64Url()
                    ?: throw IllegalArgumentException("EC key must have x")
            val yBytes =
                y?.decodeFromBase64Url()
                    ?: throw IllegalArgumentException("EC key must have y")
            val uncompressedPoint = ecUncompressedPoint(crv.value, xBytes, yBytes)
            SubjectPublicKeyInfo.ec(curveOid, uncompressedPoint)
        }

        JwaKeyType.RSA -> {
            val nBytes =
                n?.decodeFromBase64Url()
                    ?: throw IllegalArgumentException("RSA key must have n")
            val eBytes =
                e?.decodeFromBase64Url()
                    ?: throw IllegalArgumentException("RSA key must have e")
            SubjectPublicKeyInfo.rsa(
                Asn1Integer.fromUnsignedByteArray(nBytes),
                Asn1Integer.fromUnsignedByteArray(eBytes),
            )
        }

        else -> {
            throw IllegalArgumentException("Unsupported key type: $kty")
        }
    }

// ============================================================================
// Pkcs8PrivateKeyInfo ↔ JWK
// ============================================================================

fun Pkcs8PrivateKeyInfo.toJwk(x5c: Array<String>? = null): Jwk =
    when (algorithmOid) {
        RSA_ENCRYPTION_OID -> {
            val rsa = decodeRsaPrivateKey()
            Builder()
                .withKty(JwaKeyType.RSA)
                .withD(rsa.privateExponent.magnitude.toJwkProp())
                .withP(rsa.prime1.magnitude.toJwkProp())
                .withQ(rsa.prime2.magnitude.toJwkProp())
                .withDP(rsa.exponent1.magnitude.toJwkProp())
                .withDQ(rsa.exponent2.magnitude.toJwkProp())
                .withN(rsa.modulus.magnitude.toJwkProp())
                .withE(rsa.publicExponent.magnitude.toJwkProp())
                .withQInv(rsa.coefficient.magnitude.toJwkProp())
                .withX5c(x5c)
                .build()
        }

        EC_PUBLIC_KEY_OID -> {
            val ec = decodeEcPrivateKey()
            val curveOid =
                algorithmParameters?.let {
                    ObjectIdentifier.decodeFromTlv(it.asPrimitive())
                }
            val curve = EC_OID_TO_CURVE[curveOid]
            val builder =
                Builder()
                    .withKty(JwaKeyType.EC)
                    .withD(ec.privateKey.toJwkProp())
                    .withX5c(x5c)
            if (curve != null) {
                builder.withCrv(curve)
            }
            var hasPublicKey = false
            ec.publicKey?.bitCarryingBytes?.let { point ->
                if (point.isNotEmpty() && point[0] == 0x04.toByte()) {
                    val coordSize = (point.size - 1) / 2
                    builder.withX(point.copyOfRange(1, 1 + coordSize).toJwkProp())
                    builder.withY(point.copyOfRange(1 + coordSize, point.size).toJwkProp())
                    hasPublicKey = true
                }
            }
            // Defer kid generation when the PKCS#8 encoding lacks the optional
            // public key (RFC 5915 §3). The caller (e.g., SoftwareKeyStoreService)
            // supplements x/y from the certificate and can regenerate the kid.
            if (!hasPublicKey) {
                builder.withGenerateKid(false)
            }
            builder.build()
        }

        else -> {
            throw IllegalArgumentException("Unsupported private key algorithm: $algorithmOid")
        }
    }

fun Jwk.toPkcs8PrivateKeyInfo(): Pkcs8PrivateKeyInfo {
    require(d != null) { "Private key component (d) is missing" }
    return when (kty) {
        JwaKeyType.RSA -> {
            val rsaKey =
                Pkcs1RsaPrivateKeyInfo(
                    version = Pkcs1RsaPrivateKeyInfo.Version.TWO_PRIME,
                    modulus = Asn1Integer.fromUnsignedByteArray(n!!.decodeFromBase64Url()),
                    publicExponent = Asn1Integer.fromUnsignedByteArray(e!!.decodeFromBase64Url()),
                    privateExponent = Asn1Integer.fromUnsignedByteArray(d!!.decodeFromBase64Url()),
                    prime1 = Asn1Integer.fromUnsignedByteArray(p?.decodeFromBase64Url() ?: byteArrayOf(0)),
                    prime2 = Asn1Integer.fromUnsignedByteArray(q?.decodeFromBase64Url() ?: byteArrayOf(0)),
                    exponent1 = Asn1Integer.fromUnsignedByteArray(dP?.decodeFromBase64Url() ?: byteArrayOf(0)),
                    exponent2 = Asn1Integer.fromUnsignedByteArray(dQ?.decodeFromBase64Url() ?: byteArrayOf(0)),
                    coefficient = Asn1Integer.fromUnsignedByteArray(qInv?.decodeFromBase64Url() ?: byteArrayOf(0)),
                )
            Pkcs8PrivateKeyInfo.rsa(rsaKey)
        }

        JwaKeyType.EC -> {
            val crv = crv ?: throw IllegalArgumentException("EC key must have crv")
            val curveOid =
                EC_CURVE_OIDS[crv.value]
                    ?: throw IllegalArgumentException("Unknown curve: $crv")
            val dBytes = d!!.decodeFromBase64Url()
            val xBytes = x?.decodeFromBase64Url()
            val yBytes = y?.decodeFromBase64Url()
            val publicKey =
                if (xBytes != null && yBytes != null) {
                    Asn1BitString(ecUncompressedPoint(crv.value, xBytes, yBytes))
                } else {
                    null
                }
            val ecKey =
                Sec1EcPrivateKeyInfo(
                    version = Sec1EcPrivateKeyInfo.Version.V1,
                    privateKey = dBytes,
                    parameters = curveOid,
                    publicKey = publicKey,
                )
            Pkcs8PrivateKeyInfo.ec(ecKey, curveOid)
        }

        else -> {
            throw IllegalArgumentException("Unsupported key type: $kty")
        }
    }
}

// ============================================================================
// X509 Signature Algorithm ↔ SignatureAlgorithm
// ============================================================================

fun X509AlgorithmIdentifier.toSignatureAlgorithmOrNull(): SignatureAlgorithm? = OID_TO_SIGNATURE_ALG[oid]

// ============================================================================
// SubjectPublicKeyInfo PEM encoding
// ============================================================================

fun SubjectPublicKeyInfo.encodeToPem(): String {
    val der = DER.encodeToByteArray(this)
    val base64 = der.encodeToBase64()
    val body = base64.chunked(64).joinToString("\n")
    return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----"
}
