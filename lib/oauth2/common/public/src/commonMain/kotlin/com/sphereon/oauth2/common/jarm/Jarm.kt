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
 */

package com.sphereon.oauth2.common.jarm

import io.konform.validation.Validation
import io.konform.validation.jsonschema.pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * JARM (JWT Secured Authorization Response Mode) for OAuth 2.0
 *
 * Reference: RFC 9101 - JWT Secured Authorization Response Mode for OAuth 2.0
 *
 * JARM provides confidentiality and integrity protection for authorization responses
 * by encoding them as JWTs (signed, encrypted, or signed-then-encrypted).
 */

/**
 * JARM encoding mode determines how the authorization response is protected.
 *
 * Per RFC 9101:
 * - Signed: Response is a signed JWT (JWS)
 * - Encrypted: Response is an encrypted JWT (JWE) - plaintext payload
 * - SignedEncrypted: Response is signed first, then encrypted (nested JWT)
 */
@Serializable
enum class JarmMode {
    /**
     * Response is signed only (JWS compact serialization)
     * - Provides integrity and authenticity
     * - Payload is visible to intermediaries
     */
    @SerialName("signed")
    SIGNED,

    /**
     * Response is encrypted only (JWE compact serialization)
     * - Provides confidentiality
     * - No signature verification possible
     */
    @SerialName("encrypted")
    ENCRYPTED,

    /**
     * Response is signed then encrypted (nested JWT)
     * - JWS inside JWE
     * - Provides both integrity/authenticity and confidentiality
     */
    @SerialName("signed_encrypted")
    SIGNED_ENCRYPTED,
}

/**
 * JARM Authorization Response payload structure.
 *
 * Per RFC 9101, when using JARM response modes (query.jwt, fragment.jwt, form_post.jwt, etc.),
 * the authorization response is encoded as a JWT with these standard claims plus
 * the authorization response parameters as additional claims.
 *
 * Standard JWT claims:
 * - iss: REQUIRED - Issuer (authorization server identifier)
 * - aud: REQUIRED - Audience (client_id)
 * - exp: REQUIRED - Expiration time
 * - iat: OPTIONAL - Issued at time
 *
 * Authorization response parameters become JWT claims (e.g., code, state, vp_token, etc.)
 */
@Serializable
data class JarmResponsePayload(
    /**
     * Issuer of the JWT (authorization server identifier or wallet identifier)
     */
    @SerialName("iss")
    val iss: String,
    /**
     * Audience (client_id of the recipient)
     */
    @SerialName("aud")
    val aud: String,
    /**
     * Expiration time (Unix timestamp in seconds)
     */
    @SerialName("exp")
    val exp: Long,
    /**
     * Issued at time (Unix timestamp in seconds)
     */
    @SerialName("iat")
    val iat: Long? = null,
    /**
     * State value from the original authorization request.
     * This is a common parameter across OAuth 2.0 flows.
     */
    @SerialName("state")
    val state: String? = null,
    /**
     * Authorization response parameters as additional claims.
     * Examples:
     * - OAuth 2.0: code, token_type, access_token, etc.
     * - OpenID4VP: vp_token
     * - OIDC: id_token
     */
    val responseParameters: JsonObject = JsonObject(emptyMap()),
)

/**
 * Configuration for creating JARM authorization responses.
 *
 * Per RFC 9101, the client can configure how the authorization response should be protected:
 * - authorization_signed_response_alg: JWS algorithm for signing
 * - authorization_encrypted_response_alg: JWE key encryption algorithm
 * - authorization_encrypted_response_enc: JWE content encryption algorithm
 *
 * @property signingAlgorithm JWS algorithm for signing (e.g., "ES256", "RS256")
 * @property encryptionAlgorithm JWE key encryption algorithm (e.g., "ECDH-ES+A256KW", "RSA-OAEP")
 * @property contentEncryptionAlgorithm JWE content encryption algorithm (e.g., "A256GCM", "A128CBC-HS256")
 * @property mode JARM mode (Signed, Encrypted, or SignedEncrypted)
 */
@Serializable
data class JarmConfig(
    /**
     * JWS algorithm for signing the response.
     * Used when mode is SIGNED or SIGNED_ENCRYPTED.
     * Maps to authorization_signed_response_alg in client metadata.
     */
    val signingAlgorithm: String? = null,
    /**
     * JWE key encryption algorithm.
     * Used when mode is ENCRYPTED or SIGNED_ENCRYPTED.
     * Maps to authorization_encrypted_response_alg in client metadata.
     */
    val encryptionAlgorithm: String? = null,
    /**
     * JWE content encryption algorithm.
     * Used when mode is ENCRYPTED or SIGNED_ENCRYPTED.
     * Maps to authorization_encrypted_response_enc in client metadata.
     * Defaults to "A256GCM" if not specified.
     */
    val contentEncryptionAlgorithm: String? = null,
    /**
     * JARM encoding mode.
     * Determines whether to sign, encrypt, or both.
     */
    val mode: JarmMode = JarmMode.SIGNED,
) {
    companion object {
        /**
         * Default content encryption algorithm when encryption is enabled.
         */
        const val DEFAULT_CONTENT_ENCRYPTION_ALG = "A256GCM"

        /**
         * Create a signed-only JARM configuration.
         */
        fun signed(algorithm: String = "ES256"): JarmConfig = JarmConfig(signingAlgorithm = algorithm, mode = JarmMode.SIGNED)

        /**
         * Create an encrypted-only JARM configuration.
         */
        fun encrypted(
            keyEncryptionAlg: String,
            contentEncryptionAlg: String = DEFAULT_CONTENT_ENCRYPTION_ALG,
        ): JarmConfig =
            JarmConfig(
                encryptionAlgorithm = keyEncryptionAlg,
                contentEncryptionAlgorithm = contentEncryptionAlg,
                mode = JarmMode.ENCRYPTED,
            )

        /**
         * Create a signed-then-encrypted JARM configuration.
         */
        fun signedEncrypted(
            signingAlg: String = "ES256",
            keyEncryptionAlg: String,
            contentEncryptionAlg: String = DEFAULT_CONTENT_ENCRYPTION_ALG,
        ): JarmConfig =
            JarmConfig(
                signingAlgorithm = signingAlg,
                encryptionAlgorithm = keyEncryptionAlg,
                contentEncryptionAlgorithm = contentEncryptionAlg,
                mode = JarmMode.SIGNED_ENCRYPTED,
            )

        /**
         * Derive JARM configuration from client metadata parameters.
         *
         * Per RFC 9101:
         * - authorization_signed_response_alg only: Signed (JWS)
         * - authorization_encrypted_response_alg only: Encrypted (JWE)
         * - Both present: Signed then encrypted (nested JWT)
         *
         * @param signedAlg authorization_signed_response_alg
         * @param encryptedAlg authorization_encrypted_response_alg
         * @param encryptedEnc authorization_encrypted_response_enc
         * @return JarmConfig if JARM parameters present, null otherwise
         */
        fun fromClientMetadata(
            signedAlg: String? = null,
            encryptedAlg: String? = null,
            encryptedEnc: String? = null,
        ): JarmConfig? =
            when {
                // Sign + Encrypt mode
                signedAlg != null && encryptedAlg != null -> {
                    signedEncrypted(
                        signingAlg = signedAlg,
                        keyEncryptionAlg = encryptedAlg,
                        contentEncryptionAlg = encryptedEnc ?: DEFAULT_CONTENT_ENCRYPTION_ALG,
                    )
                }

                // Sign only mode
                signedAlg != null -> {
                    signed(signedAlg)
                }

                // Encrypt only mode
                encryptedAlg != null -> {
                    encrypted(
                        keyEncryptionAlg = encryptedAlg,
                        contentEncryptionAlg = encryptedEnc ?: DEFAULT_CONTENT_ENCRYPTION_ALG,
                    )
                }

                // No JARM parameters
                else -> {
                    null
                }
            }
    }
}

/**
 * Result of JARM response verification.
 *
 * @property payload Decoded authorization response payload
 * @property mode The JARM mode that was used
 * @property signatureVerified Whether the JWT was signed (and signature verified)
 * @property decrypted Whether the JWT was encrypted (and successfully decrypted)
 */
@Serializable
data class JarmVerificationResult(
    /**
     * Decoded JARM authorization response payload.
     */
    val payload: JarmResponsePayload,
    /**
     * The JARM mode that was used for protection.
     */
    val mode: JarmMode,
    /**
     * Whether the JWT was signed (and signature verified).
     */
    val signatureVerified: Boolean = false,
    /**
     * Whether the JWT was encrypted (and successfully decrypted).
     */
    val decrypted: Boolean = false,
)

/**
 * Konform validator for JarmConfig
 */
val validateJarmConfig =
    Validation<JarmConfig> {
        // Signing algorithm required for SIGNED and SIGNED_ENCRYPTED modes
        constrain("signingAlgorithm required for SIGNED and SIGNED_ENCRYPTED modes") {
            when (it.mode) {
                JarmMode.SIGNED, JarmMode.SIGNED_ENCRYPTED -> !it.signingAlgorithm.isNullOrBlank()
                JarmMode.ENCRYPTED -> true
            }
        }

        // Encryption algorithm required for ENCRYPTED and SIGNED_ENCRYPTED modes
        constrain("encryptionAlgorithm required for ENCRYPTED and SIGNED_ENCRYPTED modes") {
            when (it.mode) {
                JarmMode.ENCRYPTED, JarmMode.SIGNED_ENCRYPTED -> !it.encryptionAlgorithm.isNullOrBlank()
                JarmMode.SIGNED -> true
            }
        }

        // Validate algorithm formats (basic check)
        JarmConfig::signingAlgorithm ifPresent {
            pattern("[A-Z0-9-]+".toRegex()) hint "Invalid signing algorithm format"
        }

        JarmConfig::encryptionAlgorithm ifPresent {
            pattern("[A-Z0-9-+]+".toRegex()) hint "Invalid encryption algorithm format"
        }
    }

/**
 * Konform validator for JarmResponsePayload
 */
val validateJarmResponsePayload =
    Validation<JarmResponsePayload> {
        JarmResponsePayload::iss {
            constrain("iss cannot be empty") { it.isNotBlank() }
        }

        JarmResponsePayload::aud {
            constrain("aud cannot be empty") { it.isNotBlank() }
        }

        JarmResponsePayload::exp {
            constrain("exp must be positive") { it > 0 }
        }
    }
