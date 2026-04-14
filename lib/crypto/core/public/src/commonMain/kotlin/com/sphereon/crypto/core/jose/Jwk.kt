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

package com.sphereon.crypto.core.jose

import com.sphereon.cbor.json.HasToJsonDTO
import com.sphereon.cbor.json.HasToJsonString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyDTOType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyDTOType
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyJsonDTOType
import com.sphereon.crypto.core.cose.CoseKeyJsonType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.generic.toCoseAlgorithm
import com.sphereon.crypto.core.generic.toCoseCurve
import com.sphereon.crypto.core.generic.toCoseKeyOperations
import com.sphereon.crypto.core.generic.toCoseKeyType
import com.sphereon.crypto.core.generic.toJoseCurve
import com.sphereon.crypto.core.generic.toJoseKeyType
import com.sphereon.crypto.core.generic.toJoseSignatureAlgorithm
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.interop.publicKeyECPemFrom
import com.sphereon.crypto.core.interop.publicKeyRSAPemFrom
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateChainFromPem
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.wrapX509CertificatePem
import com.sphereon.crypto.core.x509.x509DerOrPemToDer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 *
 * Represents a JSON Web Key (JWK) that is serializable to and from JSON format.
 *
 * This interface defines the properties of a JWK as specified by the JSON Web Key (JWK) specification. It extends the `Key` interface and includes properties for common cryptographic
 *  key parameters used in various algorithms.
 *
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwkDTOType", exact = true)
@JsExportCompat
@Serializable
sealed interface JwkDTOType : KeyDTOType {
    /**
     * The algorithm name used for cryptographic operations.
     * The value may be null if the algorithm is not specified or initialized.
     */
    override val alg: String?

    /**
     * Represents the curve parameter identifier for elliptic curve cryptography.
     * It defines the specific curve to be used in cryptographic operations.
     * The value of this property can be null if no curve has been specified.
     */
    override val crv: String?

    /**
     * A nullable string that represents a specific data element
     * which can be overridden in subclasses.
     */
    override val d: String?

    /**
     * The `e` variable holds a nullable String value that can be used to store or manipulate
     * string data. This variable can also represent the absence of a string by holding a `null` value.
     */
    val e: String?

    val p: String?

    val q: String?

    val dP: String?
    val dQ: String?
    val qInv: String?

    /**
     * The shared secret (symmetric key) parameter.
     *
     * This value represents a symmetric key used in cryptographic operations. The key might
     * be used for various purposes such as encryption, decryption, or other algorithm-specific
     * symmetric key operations.
     *
     * The parameter can be null if not specified or applicable for the given cryptographic context.
     */
    val k: String?

    /**
     * Represents the operations that can be performed on the key.
     * This property holds an array of strings, each indicating a specific
     * permissible operation such as "encrypt", "decrypt", "sign", etc.
     *
     * It is optional and may be null, depending on whether the key
     * supports any specified operations.
     */
    override val key_ops: Array<String>?

    /**
     * A unique identifier for the cryptographic key.
     * This property holds an optional string that represents
     * the Key ID (kid), which is used to uniquely identify a key
     * in key management operations. This can be useful for
     * referencing, rotating, or managing keys in a secure manner.
     * It may be null if no specific Key ID is attributed.
     */
    override val kid: String?

    /**
     * The 'kty' (Key Type) parameter identifies the cryptographic algorithm family used with the key.
     * This parameter is a part of the JSON Web Key (JWK) specification.
     * It is one of the primary fields in a JWK and indicates the type of key, such as RSA, EC, oct (symmetric keys).
     */
    override val kty: String

    /**
     * Represents a nullable string value that can hold any string value
     * or be null. This variable can be used in scenarios where the
     * value may not always be available or needed.
     */
    val n: String?

    /**
     * Represents the intended use of the cryptographic key.
     *
     * This parameter specifies the purpose for which the key is meant, such as signing (sig)
     * or encryption (enc). Understanding the intended use helps in ensuring that the key is
     * only utilized in contexts that match its designated purpose, thus supporting correct
     * application and enhanced security.
     */
    val use: String?

    /**
     * Represents a nullable string that can be overridden by subclasses.
     * The purpose and usage of this variable should be defined
     * in the context of the class that overrides it.
     */
    override val x: String?

    /**
     * Variable `x5c` holds an optional array of strings.
     * It can be used to store a collection of string values.
     * The array may be null.
     */
    val x5c: Array<String>?

    /**
     * This variable represents a unique identifier known as the x5t (X.509 certificate SHA-1 thumbprint).
     * The x5t is used mainly in security contexts to uniquely identify an X.509 certificate.
     * It is a nullable string, meaning that it can either hold a SHA-1 thumbprint encoded as a Base64Url
     * string, or be null if no thumbprint is available.
     */
    val x5t: String?

    /**
     * The `x5u` variable represents a nullable string that is typically used
     * to hold a URL pointing to a JSON Web Key Set (JWKS). This URL can be
     * dereferenced to obtain the key material necessary for validating JSON
     * Web Tokens (JWTs) or other cryptographic operations.
     */
    val x5u: String?

    /**
     * Represents the base64url-encoded SHA-256 thumbprint (digest) of the DER encoding of
     * the X.509 certificate associated with a cryptographic key.
     *
     * This property is used to verify the integrity and authenticity of the X.509 certificate
     * by providing a unique fingerprint of the certificate's content. The thumbprint is
     * often used in security protocols and systems to ensure that the certificate has not
     * been tampered with and to quickly compare certificates.
     */
    @SerialName("x5t#S256")
    val x5t_S256: String?

    /**
     * This property represents an optional string value.
     * It overrides a property from a superclass or an interface.
     * The value can be null, indicating that no string is assigned.
     */
    override val y: String?

    fun toJwk(): Jwk = Jwk.fromDTO(this)
}

/**
 * Represents a JSON Web Key (JWK).
 */
@JsExportCompat
@Serializable
sealed interface JwkType : KeyType {
    /**
     * Represents the JSON Web Algorithm (JWA) used in the Key object.
     * This property is an override of the `alg` value defined in the `JwkType` interface.
     *
     * Possible values include cryptographic algorithms used for signing and encryption
     * as specified in the JSON Web Algorithms (JWA) specification.
     *
     * @see JwaAlgorithm
     */
    override val alg: JwaAlgorithm?

    /**
     * Represents the elliptic curve parameter for the JSON Web Algorithms (JWA).
     * This variable specifies which curve is used in elliptic curve cryptography (ECC) operations.
     * It is an instance of the `JwaCurve` class, which encapsulates details of the curve being used.
     */
    override val crv: JwaCurve?

    /**
     * Represents the private or secret part of the cryptographic key.
     *
     * This property is primarily utilized in asymmetric key operations,
     * such as decryption and signing, which require access to the private key material.
     *
     * It may be null if the key is meant to be used only for public key operations
     * (e.g., encryption and verification) or if the private part is not available or applicable.
     */
    override val d: String?

    /**
     * Represents the exponent value in the RSA algorithm within the JSON Web Key (JWK) structure.
     *
     * The 'e' parameter is a graph of the public key and is often set to a common default value.
     * It is used in conjunction with the 'n' parameter (modulus) to form the RSA public key.
     *
     * Can be null if not provided or applicable.
     */
    val e: String?

    val p: String?

    val q: String?

    val dP: String?
    val dQ: String?
    val qInv: String?

    /**
     * Represents the symmetric key material used for cryptographic operations.
     *
     * This variable can hold a string representation of the symmetric key, which is typically
     * used in algorithms such as HMAC or AES. It may be null if the key is not specified or
     * not relevant in the context of asymmetric cryptographic operations.
     */
    val k: String?

    /**
     * An array of allowed cryptographic operations for the key.
     *
     * This property specifies which cryptographic operations are permissible
     * for the associated key. `key_ops` is used to restrict the usage of cryptographic keys
     * to specific operations such as encryption, decryption, signing, or verifying.
     *
     * This array contains elements of `JoseKeyOperations` which defines the various possible
     * operations that can be performed with the key.
     *
     * The property returns `null` if no specific operations are assigned.
     */
    override val key_ops: Array<JoseKeyOperations>?

    /**
     * The `kid` variable represents a key identifier that is used to
     * uniquely identify a specific cryptographic key. The value is a
     * nullable string, which means it can either hold a key identifier
     * as a string or be null if no key identifier is assigned.
     */
    override val kid: String?

    /**
     * Represents the key type (`kty`) for the `JwkType` interface implementation.
     *
     * This property holds the type of the key as defined by the JSON Web Algorithms (JWA) specification.
     * It is used to identify the key type for cryptographic operations, ensuring compatibility
     * between different security frameworks and allowing the key to be correctly interpreted
     * and utilized across various implementations.
     */
    override val kty: JwaKeyType

    /**
     * The 'n' parameter in a JSON Web Key (JWK).
     *
     * Represents the 'modulus' value for RSA keys.
     *
     * This value is used in conjunction with the 'e' (exponent) parameter to form the public key graph
     * for RSA encryption and signature verification.
     *
     * It may be null, indicating that the modulus is not provided or not applicable.
     */
    val n: String?

    /**
     * Indicates the intended use of the key.
     *
     * This variable represents the public key use, such as `sig` (signature) or `enc` (encryption),
     * which helps to specify the intended purpose of the key.
     */
    val use: String?

    /**
     * Represents the 'x' coordinate parameter for an elliptic curve key or a similar cryptographic key graph.
     *
     * This value is typically used in the context of keys that rely on elliptic curve algorithms,
     * and it is essential for cryptographic operations involving such keys.
     *
     * The representation is flexible and can accommodate various types of data required by different
     * cryptographic standards.
     */
    override val x: String?

    /**
     * Represents the X.509 certificate chain associated with the key.
     *
     * This property holds an array of strings, each representing an X.509 certificate
     * in the chain. The certificates are typically base64-encoded DER (Distinguished Encoding Rules)
     * representations. The first certificate in the array is usually the end-entity certificate (the
     * certificate associated with the key) followed by intermediate and root CA certificates.
     *
     * It can be null if no x5c certificate chain is provided.
     */
    val x5c: Array<String>?

    /**
     * Represents the `x5t` (X.509 certificate SHA-1 thumbprint) parameter.
     *
     * This value is the SHA-1 thumbprint (also known as a fingerprint) of the DER encoding of an X.509
     * certificate associated with the key. It is used for ensuring the integrity and authenticity
     * of the associated certificate in various cryptographic operations and protocols.
     *
     * The `x5t` field can be null if the key does not include a X.509 certificate or if the thumbprint
     * is not available.
     */
    val x5t: String?

    /**
     * The URI of the X.509 certificate.
     *
     * This property holds the URI that references the X.509 public key certificate or certificate chain,
     * which is often used in the context of JSON Web Keys (JWKs) to provide a trust anchor for the key.
     * The certificate can be used for verifying the authenticity and integrity of the associated key.
     */
    val x5u: String?

    /**
     * Represents the X.509 certificate SHA-256 thumbprint in JWT (JSON Web Token).
     *
     * This value is the base64url-encoded SHA-256 hash of the DER encoding of the X.509 certificate.
     * It is used to uniquely identify the certificate.
     *
     * This attribute allows the receiver to validate that a token was signed by the corresponding
     * private key, enforcing stronger security in cryptographic operations.
     */
    @SerialName("x5t#S256")
    val x5t_S256: String?

    /**
     * Represents a nullable string value.
     * This value might be null or contain some string content.
     */
    override val y: String?

    override fun toPublicKey(): JwkType
}

/**
 * Represents a JSON Web Key (JWK) as defined by the JSON Web Key (JWK) specification.
 *
 * @property alg The algorithm intended for use with the key.
 * @property crv The cryptographic curve used with the key.
 * @property d The private key value.
 * @property e The public exponent for the key.
 * @property k The symmetric key value.
 * @property key_ops The allowed operations for the key.
 * @property kid The key ID.
 * @property kty The key type.
 * @property n The modulus value for the RSA key.
 * @property use The intended use of the key.
 * @property x The x coordinate of the elliptic curve point.
 * @property x5c The X.509 certificate chain.
 * @property x5t The X.509 certificate SHA-1 thumbprint.
 * @property x5u The URL to the X.509 certificate or certificate chain.
 * @property x5t_S256 The X.509 certificate SHA-256 thumbprint.
 * @property y The y coordinate of the elliptic curve point.
 */
@JsExportCompat
@Serializable
data class
Jwk
    @JvmOverloads
    constructor(
        @Transient
        private val generateKid: Boolean = false,
        override val alg: JwaAlgorithm? = null,
        override val crv: JwaCurve? = null,
        override val d: String? = null,
        override val p: String? = null,
        override val e: String? = null,
        override val k: String? = null,
        override val q: String? = null,
        @SerialName("dp")
        override val dP: String? = null,
        @SerialName("dq")
        override val dQ: String? = null,
        @SerialName("qi")
        override val qInv: String? = null,
        override val key_ops: Array<JoseKeyOperations>? = null,
        override var kid: String? = null,
        override val kty: JwaKeyType,
        override val n: String? = null,
        override val use: String? = null,
        override val x: String? = null,
        override val x5c: Array<String>? = null,
        override val x5t: String? = null,
        override val x5u: String? = null,
        @SerialName("x5t#S256")
        override val x5t_S256: String? = null,
        override val y: String? = null,
    ) : JwkType,
        HasToJsonString,
        HasToJsonDTO {
        init {
            if (kid === null && generateKid) {
                this.kid = determineKid()
            }
        }

        /**
         * Represents additional JSON attributes not explicitly defined in the JWK specification.
         *
         * This property may hold any arbitrary data in the form of a JSON object, allowing for
         * flexibility in extending the standard JWK structure with custom attributes.
         *
         * For instance, if a particular JWK implementation or usage scenario requires storing
         * extra metadata, this property can encapsulate those needs without interfering with
         * the predefined fields.
         *
         * Note: This field is optional and may be null if no additional attributes are present.
         */
        override val additional: JsonObject?
            get() = null // TODO

        private fun determineKid(): String = generateJwkThumbprint(this)

        /**
         * Retrieves the algorithm mapping for the current JWK (JSON Web Key) instance.
         *
         * If 'alg' field is present, it is used as the authoritative source.
         * Otherwise, attempts to infer a reasonable default based on key type and parameters:
         * - EC keys: infer from curve (P-256 → ES256, P-384 → ES384, P-521 → ES512, secp256k1 → ES256K)
         * - RSA keys: default to RS256 (cannot distinguish RS256/PS256/RS384/PS384/RS512/PS512 from key alone)
         * - oct keys: infer from key length (256 bits → HS256, 384 bits → HS384, 512 bits → HS512)
         *
         * @return the algorithm mapping, or null if it cannot be determined
         */
        override fun getSignatureAlgorithm(): SignatureAlgorithm? {
            // If alg is explicitly set, use it as the authoritative source.
            // Returns null for encryption-only algorithms (e.g., RSA-OAEP) that have no signature mapping.
            alg?.let { return SignatureAlgorithm.tryFromJose(it).getOrNull() }

            // Infer algorithm from key type and parameters
            return when (kty.value) {
                "EC" -> {
                    // Infer from curve
                    when (crv?.value) {
                        "P-256" -> SignatureAlgorithm.ECDSA_SHA256
                        "P-384" -> SignatureAlgorithm.ECDSA_SHA384
                        "P-521" -> SignatureAlgorithm.ECDSA_SHA512
                        "secp256k1" -> SignatureAlgorithm.ES256K
                        else -> null
                    }
                }

                "RSA" -> {
                    // Cannot distinguish between RS256/PS256/etc from key alone
                    // Default to RS256 as it's the most common
                    // Note: If PS256 is needed, it must be explicitly set in the JWT header's alg field
                    SignatureAlgorithm.RSA_SHA256
                }

                "oct" -> {
                    // Infer from key length (k is base64url encoded)
                    val keyBytes =
                        k?.let {
                            // Estimate byte length from base64url length (each 4 chars = 3 bytes)
                            (it.length * 3) / 4
                        }
                    when {
                        keyBytes == null -> null

                        keyBytes >= 64 -> SignatureAlgorithm.HMAC_SHA512

                        // 512 bits
                        keyBytes >= 48 -> SignatureAlgorithm.HMAC_SHA384

                        // 384 bits
                        keyBytes >= 32 -> SignatureAlgorithm.HMAC_SHA256

                        // 256 bits
                        else -> null
                    }
                }

                "OKP" -> {
                    // Ed25519, Ed448, X25519, X448
                    when (crv?.value) {
                        "Ed25519" -> SignatureAlgorithm.ED25519
                        else -> null // Ed448 not currently defined
                    }
                }

                else -> {
                    null
                }
            }
        }

        /**
         * Retrieves the key type mapping for the current instance.
         *
         * @return KeyTypeMapping that corresponds to the current instance's JOSE key type.
         */
        override fun getKeyType(): KeyTypeMapping = KeyTypeMapping.fromJose(this.kty)

        /**
         * Returns an array of KeyOperationsMapping objects derived from the key operations specified
         * in the JWK (JSON Web Key) object. If no key operations are defined, it returns null.
         *
         * @return An array of KeyOperationsMapping objects or null if no key operations are defined.
         */
        override fun getKeyOperations(): Array<KeyOperations>? = key_ops?.map { KeyOperations.fromJose(it) }?.toTypedArray()

        /**
         * Retrieves the X.509 certificate chain array (x5c) associated with the JSON Web Key (JWK) in base64 encoded DER.
         *
         * @return An array of strings representing the X.509 certificate chain, or null if it is not set.
         */
        override fun getX509CertificateChain(): Array<String>? = x5c?.map { x509DerOrPemToDer(it) }?.toTypedArray()

        /**
         * Retrieves the leaf X.509 certificate as DTO. This function assumes the leaf certificate is the first one in the chain (which is common practice.)
         *
         * @return the leaf X.509 certificate as DTO, or null if the certificate is not present.
         */
        override fun getX509Certificate(): Certificate? = getX509CertificateChain()?.firstOrNull()?.let { cert -> certificateFromDer(cert.decodeFrom(Encoding.BASE64)) }

        /**
         * Retrieves the leaf X.509 certificate as PEM-encoded. This function assumes the leaf certificate is the first one in the chain (which is common practice.)
         *
         * @return the leaf X.509 certificate as PEM-encoded, or null if the certificate is not present.
         */
        override fun getX509CertificatePem(): String? = getX509CertificateChain()?.firstOrNull()?.let { cert -> wrapX509CertificatePem(cert) }

        override fun getKeyId(generate: Boolean) =
            kid ?: if (generate) {
                determineKid()
            } else {
                kid
            }

        override fun getXAsString() = x

        override fun getYAsString() = y

        override fun getDAsString() = d

        override fun toPublicKey(): Jwk = copy(d = null)

        /**
         * Returns a minimal JWK containing only the required fields for the key type.
         * This is useful for CNF (confirmation) claims per RFC 7800, which should contain
         * only the essential public key material (kty and key-specific parameters).
         *
         * For EC keys: kty, crv, x, y
         * For RSA keys: kty, n, e
         * For symmetric keys: kty, k
         *
         * All optional fields (kid, alg, use, key_ops, etc.) are excluded.
         *
         * @return IdkResult containing a minimal JWK with only required fields, or an error if key type is unsupported
         */
        fun tryToMinimalJwk(): IdkResult<Jwk, IdkError> =
            when (kty) {
                JwaKeyType.EC -> {
                    Ok(
                        Jwk(
                            kty = kty,
                            crv = crv,
                            x = x,
                            y = y,
                        ),
                    )
                }

                JwaKeyType.RSA -> {
                    Ok(
                        Jwk(
                            kty = kty,
                            n = n,
                            e = e,
                        ),
                    )
                }

                JwaKeyType.oct -> {
                    Ok(
                        Jwk(
                            kty = kty,
                            k = k,
                        ),
                    )
                }

                else -> {
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported key type for minimal JWK: $kty"))
                }
            }

        /**
         * Returns a minimal JWK containing only the required fields for the key type.
         * This is useful for CNF (confirmation) claims per RFC 7800, which should contain
         * only the essential public key material (kty and key-specific parameters).
         *
         * For EC keys: kty, crv, x, y
         * For RSA keys: kty, n, e
         * For symmetric keys: kty, k
         *
         * All optional fields (kid, alg, use, key_ops, etc.) are excluded.
         *
         * @return a minimal JWK with only required fields
         * @throws IllegalArgumentException if key type is unsupported
         */
        fun toMinimalJwk(): Jwk =
            tryToMinimalJwk().getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        /**
         * Safely converts the current object into a PEM-encoded public key string.
         *
         * @return IdkResult containing the PEM-encoded public key, or an error if conversion is not possible
         */
        fun tryPublicKeyPem(): IdkResult<String, IdkError> {
            val keyType = this.kty
            return when (keyType) {
                JwaKeyType.EC -> tryToECPublicKeyPem()
                JwaKeyType.RSA -> tryToRSAPublicKeyPem()
                else -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported public key type: $keyType"))
            }
        }

        /**
         * Converts the current object into a PEM-encoded public key string.
         *
         * @return the PEM-encoded public key
         * @throws IllegalArgumentException if required parameters for the detected key type are absent,
         *                                  or if the key type is not supported
         */
        override fun publicKeyPem(): String =
            tryPublicKeyPem().getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        /**
         * Safely converts the current object to an RSA PEM-formatted public key.
         *
         * @return IdkResult containing the RSA PEM public key, or an error if required parameters are missing
         */
        private fun tryToRSAPublicKeyPem(): IdkResult<String, IdkError> {
            val n =
                this.n?.decodeFrom(Encoding.BASE64URL)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "n is required to be present for RSA keys"))
            val e =
                this.e?.decodeFrom(Encoding.BASE64URL)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "e is required to be present for RSA keys"))
            return Ok(publicKeyRSAPemFrom(n = n, e = e))
        }

        /**
         * Converts the current object to an RSA PEM-formatted public key.
         *
         * @throws IllegalArgumentException if any required RSA parameter is missing.
         */
        private fun toRSAPublicKeyPem(): String =
            tryToRSAPublicKeyPem().getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        /**
         * Safely converts the current object to an EC PEM-formatted public key.
         *
         * @return IdkResult containing the EC PEM public key, or an error if required parameters are missing
         */
        private fun tryToECPublicKeyPem(): IdkResult<String, IdkError> {
            val xBytes =
                x?.decodeFrom(Encoding.BASE64URL)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "x is required to be present for EC keys"))
            val yBytes =
                y?.decodeFrom(Encoding.BASE64URL)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "y is required to be present for EC keys"))
            val curveName =
                crv?.toString()
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "crv is required to be present for EC keys"))
            return Ok(publicKeyECPemFrom(curveName, xBytes, yBytes))
        }

        /**
         * Converts the current object to an EC PEM-formatted public key.
         *
         * @throws IllegalArgumentException if any required EC parameter is missing.
         */
        private fun toECPublicKeyPem(): String =
            tryToECPublicKeyPem().getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        /**
         * The `Builder` class is used to construct instances of the `Jwk` class with various optional properties.
         */
        class Builder {
            /**
             * Indicates whether the 'kid' (Key ID) should be automatically generated for the JSON Web Key (JWK).
             *
             * This is a boolean flag that, when set to true, enables the automatic creation of a unique Key ID for the JWK.
             * If set to false, the 'kid' will not be generated automatically, and it must be explicitly provided if needed.
             *
             * In the builder we enable generation by default, contrary to when we decode a key
             */
            var generateKid: Boolean = true

            /**
             * The `alg` variable holds an instance of the `JwaAlgorithm` class, representing the algorithm
             * used for JSON Web Algorithms (JWA). It's used in conjunction with JSON Web Tokens (JWT) for
             * signing or encryption operations.
             *
             * This variable can be `null`, indicating that no specific algorithm has been assigned yet.
             *
             * The `JwaAlgorithm` class encompasses various cryptographic algorithms standardized for use
             * in JSON-based tokens. The choice of algorithm affects the security and performance
             * characteristics of the cryptographic operations.
             */
            var alg: JwaAlgorithm? = null

            /**
             * Represents the JSON Web Algorithm (JWA) curve used in cryptographic operations.
             * This variable can hold different types of curves such as P-256, P-384, and P-521.
             * The value is nullable, indicating that the curve might not always be set.
             */
            var crv: JwaCurve? = null

            /**
             * This variable holds a nullable String which can be used to store text data.
             * Initially, it is set to null which indicates that it does not contain any value currently.
             * It can be assigned a non-null value later during the program execution.
             */
            var d: String? = null

            /**
             * The 'e' parameter of a JWK (JSON Web Key) representing the RSA public exponent.
             * Typically used with RSA public keys.
             */
            var e: String? = null

            /**
             * Represents the symmetric key used in cryptographic operations.
             *
             * This property stores the key value, which is used in JWK (JSON Web Key) structures.
             * The key can be null, indicating that no key value has been set.
             */
            var k: String? = null

            /**
             * The operations that the key is intended to be used for.
             * Examples of possible values include "sign", "verify", "encrypt", etc.
             */
            var key_ops: Array<JoseKeyOperations>? = null

            /**
             * Represents a child entity, potentially containing the name or
             * identifier of a child. This can be used in contexts where
             * distinguishing or identifying a child is required.
             *
             * The value is initialized to null, indicating no value has been set.
             * It can be assigned a non-null string to represent the name/identifier.
             *
             * Example use cases include tracking children in a school system,
             * managing child elements in hierarchical data structures, and other
             * similar scenarios where child identification is necessary.
             */
            var kid: String? = null

            /**
             * Defines the key type (kty) parameter in the JWK (JSON Web Key) as specified by the JSON Web Algorithms (JWA).
             * This property indicates the specific cryptographic algorithm family used by the key.
             *
             * Possible values are:
             * - EC: Elliptic Curve
             * - RSA: RSA Encryption
             * - oct: Octet sequence (used to represent symmetric keys)
             * - OKP: Octet Key Pair (used for algorithms like EdDSA)
             *
             * This parameter is critical and its absence will cause the build process to fail,
             * as it is essential for the cryptographic operations that the key will perform.
             */
            var kty: JwaKeyType? = null

            /**
             * A nullable String variable `n` that can be used to store a string value or null.
             * It is initialized to null by default.
             */
            var n: String? = null

            var p: String? = null

            var q: String? = null

            var dP: String? = null
            var dQ: String? = null
            var qInv: String? = null

            /**
             * A nullable string variable that can be used to store any text or String data.
             * The variable is initially set to null, indicating that it has no value assigned.
             * It can be updated to hold a non-null string as needed throughout the program.
             * Common use cases include temporary string storage, input/output handling, and data manipulation.
             */
            var use: String? = null

            /**
             * A nullable String variable that can be used to store text data.
             *
             * This variable can hold either a String value or null. Initially, it is set to null.
             *
             * It can be used in scenarios where the string value might not always be available or optional.
             */
            var x: String? = null

            /**
             * Holds an array of x5c certificates as strings.
             *
             * This variable can be used to store x5c certificates,
             * which are typically Base64-encoded DER representations of X.509 certificates.
             *
             * It is nullable and can be set to null initially.
             */
            var x5c: Array<String>? = null

            /**
             * Represents the X.509 certificate SHA-1 thumbprint.
             *
             * The `x5t` parameter contains the base64url-encoded SHA-1 thumbprint of the DER-encoded X.509 certificate.
             * It is used to reference a specific certificate when performing cryptographic operations.
             * This value is optional and can be null if not specified.
             */
            var x5t: String? = null

            /**
             * x5u variable holds a URL-safe representation of a JWK Set used in the application.
             *
             * The value is a nullable String. When populated, it should contain a URL that points to a JSON Web Key Set.
             * This variable can be used in processes involving security keys or cryptographic operations.
             *
             * Default value is null.
             */
            var x5u: String? = null

            /**
             * The base64url-encoded SHA-256 thumbprint of the X.509 certificate associated with the key.
             * This value provides a strong reference to the certificate when it is used in JSON Web Key (JWK) objects.
             * It is an optional field and may be null if not specified.
             */
            var x5t_S256: String? = null

            /**
             * A nullable variable that holds a string value.
             * It can be assigned a non-null string or a null value.
             * Initially, it is set to null.
             */
            var y: String? = null

            /**
             * Sets the JSON Web Algorithm (JWA) for this builder instance.
             *
             * @param alg An instance of [JwaAlgorithm], or null if the algorithm is to be unset.
             */
            fun withAlg(alg: JwaAlgorithm? = null) = apply { this.alg = alg }

            /**
             * Sets the JwaCurve for the current instance.
             *
             * @param crv The JwaCurve to be set for this instance.
             * @return The current instance with the specified JwaCurve applied.
             */
            fun withCrv(crv: JwaCurve?) = apply { this.crv = crv }

            fun withGenerateKid(generateKid: Boolean? = false) = apply { this.generateKid = generateKid ?: true }

            /**
             * Sets the 'd' parameter for this builder instance.
             *
             * @param d The 'd' value to set, or null if not applicable.
             */
            fun withD(d: String?) = apply { this.d = d }

            fun withP(p: String?) = apply { this.p = p }

            fun withQ(q: String?) = apply { this.q = q }

            fun withDP(dp: String?) = apply { this.dP = dp }

            fun withDQ(dq: String?) = apply { this.dQ = dq }

            fun withQInv(qInv: String?) = apply { this.qInv = qInv }

            /**
             * Sets the `e` parameter of the Builder and returns the Builder instance.
             *
             * @param e the value to set for the `e` parameter. It can be null.
             */
            fun withE(e: String?) = apply { this.e = e }

            /**
             * Sets the 'k' (key) parameter for the Builder.
             *
             * @param k the key value to set, can be nullable.
             */
            fun withK(k: String?) = apply { this.k = k }

            /**
             * Sets the key operations for the current object.
             *
             * @param keyOps An array of JoseKeyOperations specifying the permissible operations for the key.
             * Setting this to null will clear the currently set key operations.
             */
            fun withKeyOps(keyOps: Array<JoseKeyOperations>?) = apply { this.key_ops = keyOps }

            /**
             * Sets the 'kid' (Key ID) parameter for the JWK (JSON Web Key) being constructed.
             *
             * @param kid the Key ID to set for the JWK, or null if no Key ID should be assigned.
             * @return the Builder instance with the updated 'kid' property.
             */
            fun withKid(
                kid: String? = null,
                generate: Boolean? = null,
            ) = apply {
                this.kid = kid
                this.generateKid = generate ?: generateKid
            }

            /**
             * Sets the key type for this object.
             *
             * @param kty The JwaKeyType to be set. Can be null.
             */
            fun withKty(kty: JwaKeyType?) = apply { this.kty = kty }

            /**
             * Sets the 'n' field and returns the current Builder instance.
             *
             * @param n The 'n' value to set.
             */
            fun withN(n: String?) = apply { this.n = n }

            /**
             * Sets the 'use' parameter for the Builder instance.
             *
             * @param use A string indicating the intended use of the key (e.g., "sig" for signature or "enc" for encryption).
             */
            fun withUse(use: String?) = apply { this.use = use }

            /**
             * Sets the `x` property of the Builder object.
             *
             * @param x the value to set for the `x` property
             */
            fun withX(x: String?) = apply { this.x = x }

            /**
             * Sets the x5c property and returns the current object.
             *
             * @param x5c an array of strings to set the x5c property. It can be null.
             */
            fun withX5c(x5c: Array<String>?) = apply { this.x5c = x5c }

            /**
             * Sets the x5t (X.509 certificate SHA-1 thumbprint) value.
             *
             * @param x5t the X.509 certificate SHA-1 thumbprint to be set. Can be nullable.
             */
            fun withX5t(x5t: String?) = apply { this.x5t = x5t }

            /**
             * Sets the `x5u` attribute.
             *
             * @param x5u The new value for the `x5u` attribute. This parameter can be null.
             */
            fun withX5u(x5u: String?) = apply { this.x5u = x5u }

            /**
             * Sets the x5t_S256 parameter for the instance and returns the modified instance.
             *
             * @param x5tS256 The x5t_S256 value to set. It is a nullable String.
             */
            fun withX5t_S256(x5tS256: String?) = apply { this.x5t_S256 = x5tS256 }

            /**
             * Sets the `y` parameter for the Builder instance.
             *
             * @param y The value to set for the `y` parameter. It can be a nullable string.
             */
            fun withY(y: String?) = apply { this.y = y }

            /**
             * Safely constructs a new `Jwk` instance using the provided properties.
             *
             * @return IdkResult containing a new Jwk instance, or an error if `kty` is missing.
             */
            fun tryBuild(): IdkResult<Jwk, IdkError> {
                val keyType =
                    kty
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "kty value is required"))
                return Ok(
                    Jwk(
                        generateKid = generateKid,
                        alg = alg,
                        crv = crv,
                        d = d,
                        e = e,
                        k = k,
                        p = p,
                        q = q,
                        dP = dP,
                        dQ = dQ,
                        qInv = qInv,
                        key_ops = key_ops,
                        kid = kid,
                        kty = keyType,
                        n = n,
                        use = use,
                        x = x,
                        x5c = x5c,
                        x5t = x5t,
                        x5u = x5u,
                        x5t_S256 = x5t_S256,
                        y = y,
                    ),
                )
            }

            /**
             * Constructs a new `Jwk` instance using the provided properties.
             *
             * @return a new instance of `Jwk` initialized with the set properties.
             * @throws IllegalArgumentException if the `kty` property is missing.
             */
            fun build(): Jwk =
                tryBuild().getOrElse {
                    throw IllegalArgumentException(it.message.defaultMessage)
                }
        }

        /**
         * Safely converts the JSON Web Key (JWK) to a COSE key in JSON format.
         *
         * @return IdkResult containing the CoseKeyJson, or an error if conversion is not possible.
         */
        fun tryJwkToCoseKeyJson(): IdkResult<CoseKeyJson, IdkError> {
            val coseKty = KeyTypeMapping.tryToCose(this.kty).getOrElse { return Err(it) }
            val coseAlg = alg?.let { SignatureAlgorithm.tryToCose(it).getOrElse { err -> return Err(err) } }
            val coseCrv = crv?.let { Curve.tryToCose(it).getOrElse { err -> return Err(err) } }
            val coseKeyOps =
                key_ops
                    ?.map { op ->
                        KeyOperations.tryToCose(op).getOrElse { err -> return Err(err) }
                    }?.toTypedArray()

            val builder =
                CoseKeyJson
                    .Builder()
                    .withKty(coseKty)
                    .withAlg(coseAlg)
                    .withX5Chain(x5c)
                    .withKeyOps(coseKeyOps)
                    .withKid(kid, false)

            // Key-type specific parameters
            if (kty == JwaKeyType.RSA) {
                // RSA key
                builder
                    .withN(n)
                    .withRsaE(e)
                    .withD(d)
                    .withP(p)
                    .withQ(q)
                    .withDP(dP)
                    .withDQ(dQ)
                    .withQInv(qInv)
            } else {
                // EC/OKP key
                builder
                    .withCrv(coseCrv)
                    .withX(x)
                    .withY(y)
                    .withD(d)
            }

            return Ok(builder.build())
        }

        /**
         * Converts the JSON Web Key (JWK) to a COSE key in JSON format.
         *
         * @return A `CoseKeyJson` object representing the COSE key in JSON format.
         * @throws IllegalArgumentException if key type cannot be converted to COSE
         */
        fun jwkToCoseKeyJson(): CoseKeyJson =
            tryJwkToCoseKeyJson().getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        /**
         * Safely converts a JWK (JSON Web Key) to a COSE key in CBOR encoding.
         *
         * @return IdkResult containing the COSE key, or an error if conversion is not possible.
         */
        fun tryJwkToCoseKey(): IdkResult<CoseKey, IdkError> =
            tryJwkToCoseKeyJson().let { result ->
                when {
                    result.isOk -> Ok(result.value.toCbor())
                    else -> Err(result.error)
                }
            }

        /**
         * Converts a JWK (JSON Web Key) to a COSE key in CBOR encoding.
         *
         * This function transforms the current JWK instance into its corresponding
         * COSE Key representation and then serializes it into CBOR format.
         *
         * @return the CBOR-encoded representation of the COSE key.
         */
        fun jwkToCoseKey(): CoseKey = this.jwkToCoseKeyJson().toCbor()

        /**
         * Converts the current object into a JSON object representation.
         *
         * Uses the `cryptoJsonSerializer` to encode the object to a JSON element and retrieves the JSON object from it.
         *
         * @return The JSON object representation of the current object.
         */
        fun toJsonObject() = cryptoJsonSerializer.encodeToJsonElement(serializer(), this)

        override fun toJsonString() = cryptoJsonSerializer.encodeToString(serializer(), this)

        override fun <T> toJsonDTO() =
            com.sphereon.cbor.json
                .toJsonDTO<T>(this)

        /**
         * Object containing utility functions for creating JWK (JSON Web Keys) from various input types,
         * such as JwkTypeJson, JsonObject, JwkType, CoseKeyJson, CoseKey and PEM-encoded X.509 certificates.
         */
        companion object {
            /**
             * Converts a JSON representation of a JWK (JSON Web Key) to a Jwk object.
             *
             * @param jwk The JSON representation of the JWK.
             * @return The Jwk object.
             */
            @JsStatic
            @JvmStatic
            fun fromDTO(jwk: JwkDTOType): Jwk =
                with(jwk) {
                    return Jwk(
                        alg = JwaAlgorithm.fromValue(alg),
                        crv = JwaCurve.fromValue(crv),
                        d = d,
                        e = e,
                        k = k,
                        p = p,
                        q = q,
                        dP = dP,
                        dQ = dQ,
                        qInv = qInv,
                        key_ops = key_ops?.map { JoseKeyOperations.fromValue(it) }?.toTypedArray(),
                        kid = kid,
                        kty = JwaKeyType.fromValue(kty),
                        n = n,
                        use = use,
                        x = x,
                        x5c = x5c?.map { it }?.toTypedArray(),
                        x5t = x5t,
                        x5u = x5u,
                        x5t_S256 = x5t_S256,
                        y = y,
                    )
                }

            /**
             * Safely constructs a [Jwk] instance from a given [JsonObject].
             *
             * @param jwk The JSON object representing the JWK.
             * @return IdkResult containing a Jwk instance, or an error if kty is missing.
             */
            @JsStatic
            @JvmStatic
            fun tryFromJsonObject(jwk: JsonObject): IdkResult<Jwk, IdkError> =
                with(jwk) {
                    val keyType =
                        get("kty")?.jsonPrimitive?.content?.let { JwaKeyType.fromValue(it) }
                            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "kty is required in JWK"))
                    return@tryFromJsonObject Ok(
                        Jwk(
                            alg =
                                get("alg")?.jsonPrimitive?.content?.let {
                                    JwaAlgorithm.fromValue(it)
                                },
                            crv = get("crv")?.jsonPrimitive?.content?.let { JwaCurve.fromValue(it) },
                            d = get("d")?.jsonPrimitive?.content,
                            e = get("e")?.jsonPrimitive?.content,
                            k = get("k")?.jsonPrimitive?.content,
                            key_ops = get("key_ops")?.jsonArray?.map { JoseKeyOperations.fromValue(it.jsonPrimitive.content) }?.toTypedArray(),
                            kid = get("kid")?.jsonPrimitive?.content,
                            kty = keyType,
                            n = get("n")?.jsonPrimitive?.content,
                            p = get("p")?.jsonPrimitive?.content,
                            q = get("q")?.jsonPrimitive?.content,
                            dP = get("dp")?.jsonPrimitive?.content,
                            dQ = get("dq")?.jsonPrimitive?.content,
                            qInv = get("qi")?.jsonPrimitive?.content,
                            use = get("use")?.jsonPrimitive?.content,
                            x = get("x")?.jsonPrimitive?.content,
                            x5c = get("x5c")?.jsonArray?.map { it.jsonPrimitive.content }?.toTypedArray(),
                            x5t = get("x5t")?.jsonPrimitive?.content,
                            x5u = get("x5u")?.jsonPrimitive?.content,
                            x5t_S256 = get("x5t#S256")?.jsonPrimitive?.content,
                            y = get("y")?.jsonPrimitive?.content,
                        ),
                    )
                }

            /**
             * Constructs a [Jwk] instance from a given [JsonObject].
             *
             * @param jwk The JSON object representing the JWK.
             * @return A [Jwk] instance populated with the values from the JSON object.
             * @throws IllegalArgumentException if kty is missing
             */
            @JsStatic
            @JvmStatic
            fun fromJsonObject(jwk: JsonObject): Jwk =
                tryFromJsonObject(jwk).getOrElse {
                    throw IllegalArgumentException(it.message.defaultMessage)
                }

            /**
             * Converts an instance of `JwkType` to an instance of `Jwk`.
             *
             * @param jwk The `JwkType` instance to be converted.
             * @return The equivalent `Jwk` instance with the same properties.
             */
            @JsStatic
            @JvmStatic
            fun from(jwk: JwkType): Jwk =
                with(jwk) {
                    return@from Jwk(
                        alg = alg,
                        crv = crv,
                        d = d,
                        e = e,
                        k = k,
                        p = p,
                        q = q,
                        dP = dP,
                        dQ = dQ,
                        qInv = qInv,
                        key_ops = key_ops,
                        kid = kid,
                        kty = kty,
                        n = n,
                        use = use,
                        x = x,
                        x5c = x5c,
                        x5t = x5t,
                        x5u = x5u,
                        x5t_S256 = x5t_S256,
                        y = y,
                    )
                }

            /**
             * Converts a given COSE key JSON object to a JWK (JSON Web Key).
             *
             * @param coseKey the COSE key JSON object to convert.
             * @return the resulting JWK.
             */
            @JsStatic
            @JvmStatic
            fun fromCoseKeyJson(coseKey: CoseKeyJsonDTOType): Jwk {
                with(coseKey) {
                    val joseKty = kty.toJoseKeyType()
                    val builder =
                        Builder()
                            .withKty(joseKty)
                            .withAlg(alg?.toJoseSignatureAlgorithm())
                            .withD(d)
                            .withKeyOps(key_ops?.map { KeyOperations.toJose(it) }?.toTypedArray())
                            .withKid(kid, false)
                            .withX5c(x5chain)

                    // Key-type specific parameters
                    if (joseKty == JwaKeyType.RSA) {
                        // RSA key
                        builder
                            .withN(n)
                            .withE(rsaE)
                            .withP(p)
                            .withQ(q)
                            .withDP(dP)
                            .withDQ(dQ)
                            .withQInv(qInv)
                    } else {
                        // EC/OKP key
                        builder
                            .withCrv(crv?.toJoseCurve())
                            .withX(x)
                            .withY(y)
                    }

                    return builder.build()
                }
            }

            /**
             * Converts a COSE key in CBOR format to its JSON representation.
             *
             * @param coseKey An object implementing the CoseKey interface, which represents
             * a COSE key encoded in CBOR format.
             * @return The JSON representation of the COSE key.
             */
            @JsStatic
            @JvmStatic
            fun fromCoseKey(coseKey: CoseKeyDTOType) = fromCoseKeyJson(CoseKey.fromDTO(coseKey).toJson())

            /**
             * Creates a JWK from a PEM-encoded X.509 certificate or certificate chain.
             *
             * @param pem the PEM-encoded X.509 certificate or certificate chain
             * @return a Jwk representing the certificate’s public key and certificate chain
             * @throws IllegalArgumentException if the certificate chain is empty or contains no public key
             */
            @JsStatic
            @JvmStatic
            fun fromX509CertificatePem(pem: String): JwkType {
                val chain = certificateChainFromPem(pem)
                val x5c = chain.map { it.der.encodeTo(Encoding.BASE64) }.toTypedArray()
                val leafCert = chain.first()
                return leafCert.getPublicKeyJwk(x5c = x5c)
            }
        }
    }

/**
 * Converts a COSE key in CBOR format to a JWK format.
 *
 * This function takes a `CoseKey` object and transforms it into a `Jwk.Static` object.
 * It is useful for converting cryptographic keys between formats commonly used in
 * web security and cryptography.
 *
 * @receiver The COSE key represented in CBOR format to be converted.
 * @return A `Jwk.Static` instance created from the given COSE key.
 */
@JsExportCompat
fun CoseKey.cborToJwk() = Jwk.fromCoseKey(this)

/**
 * Converts a COSE key in JSON format to a JWK (JSON Web Key).
 *
 * This extension function allows transforming a COSE key represented as a JSON object
 * into its equivalent JWK representation. The conversion utilizes the `fromCoseKeyJson`
 * method from the `Jwk.Static` object.
 *
 * @receiver CoseKeyJson The COSE key in JSON format to be converted.
 * @return Jwk The corresponding JWK representation of the COSE key.
 */
@JsExportCompat
fun CoseKeyJsonType.jsonToJwk() = Jwk.fromCoseKeyJson(this)

@JsExportCompat
@Serializable
enum class JwkUse(
    val value: String,
) {
    sig("sig"),
    enc("enc"),
}

/**
 * Safely generates a JWK (JSON Web Key) thumbprint based on the key type and its parameters.
 *
 * The function creates a subset of the JWK containing essential fields based on the key type
 * (RSA or EC) and then calculates a hash of the JSON-encoded subset. This hash is converted
 * to a Base64 URL-encoded string to produce the thumbprint.
 *
 * Please note that the JWK properties are in natural order!
 *
 * @param jwk The JWK object containing key information.
 * @return IdkResult containing the thumbprint, or an error if it cannot be generated.
 */
fun tryGenerateJwkThumbprint(jwk: JwkType): IdkResult<String, IdkError> {
    val jwkSubset =
        when (jwk.kty) {
            JwaKeyType.RSA -> {
                val e =
                    jwk.e
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "e is required for RSA key thumbprint"))
                val n =
                    jwk.n
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "n is required for RSA key thumbprint"))
                mapOf(
                    "e" to e,
                    "kty" to jwk.kty.value,
                    "n" to n,
                )
            }

            JwaKeyType.EC -> {
                val crv =
                    jwk.crv?.value
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "crv is required for EC key thumbprint"))
                val x =
                    jwk.x
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "x is required for EC key thumbprint"))
                val y =
                    jwk.y
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "y is required for EC key thumbprint"))
                mapOf(
                    "crv" to crv,
                    "kty" to jwk.kty.value,
                    "x" to x,
                    "y" to y,
                )
            }

            else -> {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported key type for thumbprint: ${jwk.kty}"))
            }
        }

    val json = Json.encodeToString(jwkSubset)
    val hash = hash(json.decodeFrom(Encoding.UTF8))
    return Ok(hash.encodeToBase64Url())
}

/**
 * Generates a JWK (JSON Web Key) thumbprint based on the key type and its parameters.
 *
 * The function creates a subset of the JWK containing essential fields based on the key type
 * (RSA or EC) and then calculates a hash of the JSON-encoded subset. This hash is converted
 * to a Base64 URL-encoded string to produce the thumbprint.
 *
 * Please note that the JWK properties are in natural order!
 *
 * @param jwk The JWK object containing key information.
 * @return A Base64 URL-encoded string representing the JWK thumbprint.
 * @throws IllegalArgumentException if key type is unsupported or required parameters are missing
 */
fun generateJwkThumbprint(jwk: JwkType): String =
    tryGenerateJwkThumbprint(jwk).getOrElse {
        throw IllegalArgumentException(it.message.defaultMessage)
    }

/**
 * Generates a JWK Thumbprint URI per RFC 9278.
 *
 * Returns `urn:ietf:params:oauth:jwk-thumbprint:sha-256:<base64url-hash>`.
 *
 * @param jwk The JWK object containing key information.
 * @return The JWK Thumbprint URI string.
 * @throws IllegalArgumentException if key type is unsupported or required parameters are missing.
 */
fun generateJwkThumbprintUri(jwk: JwkType): String = "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${generateJwkThumbprint(jwk)}"

/**
 * Represents a JSON Web Key Set (JWKS) as defined by RFC 7517
 *
 * @property keys Array of JSON Web Keys
 */
@JsExportCompat
@Serializable
data class JwkSet(
    val keys: Array<Jwk>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as JwkSet

        return keys.contentEquals(other.keys)
    }

    override fun hashCode(): Int = keys.contentHashCode()

    companion object {
        /**
         * Creates a JwkSet from a JSON string
         */
        @JsStatic
        @JvmStatic
        fun fromJsonString(json: String): JwkSet = cryptoJsonSerializer.decodeFromString(serializer(), json)
    }
}
