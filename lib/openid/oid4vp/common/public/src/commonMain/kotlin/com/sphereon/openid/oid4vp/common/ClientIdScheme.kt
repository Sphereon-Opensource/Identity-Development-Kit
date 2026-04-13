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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * JAR (JWT Secured Authorization Request) signer method.
 *
 * Represents how a JAR was signed, determined by examining the JWT header.
 * Each client_id scheme has specific requirements about which signer methods are allowed.
 *
 * OpenID4VP 1.0 Final Section 5.6 defines the JAR signing requirements.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JarSignerMethod", exact = true)
@Serializable
@JsExportCompat
enum class JarSignerMethod {
    /**
     * Signed using X.509 certificate chain (x5c header present).
     * Required for: x509_san_dns, x509_san_uri, x509_hash schemes.
     * Allowed for: verifier_attestation, pre_registered schemes.
     */
    @SerialName("x5c")
    X5C,

    /**
     * Signed using a DID (Decentralized Identifier).
     * The JWT header contains a kid referencing a DID verification method.
     * Required for: decentralized_identifier scheme.
     * Allowed for: verifier_attestation, pre_registered schemes.
     */
    @SerialName("did")
    DID,

    /**
     * Signed using an embedded JWK (jwk header present).
     * Allowed for: verifier_attestation, pre_registered schemes.
     */
    @SerialName("jwk")
    JWK,

    /**
     * Signed using OpenID Federation entity statement.
     * Allowed for: verifier_attestation scheme.
     */
    @SerialName("federation")
    FEDERATION,

    /**
     * Custom/pre-configured signer (e.g., using kid from pre-registered JWKS).
     * Allowed for: pre_registered, verifier_attestation schemes.
     */
    @SerialName("custom")
    CUSTOM,

    ;

    companion object {
        /**
         * Detect the signer method from JWT header parameters.
         *
         * Detection logic:
         * 1. If x5c header is present -> X5C
         * 2. If jwk header is present -> JWK
         * 3. If kid starts with "did:" -> DID
         * 4. Otherwise -> CUSTOM (pre-registered key by kid)
         *
         * @param x5c X.509 certificate chain from header (null if not present)
         * @param jwk Embedded JWK from header (null if not present)
         * @param kid Key ID from header (null if not present)
         * @return The detected signer method
         */
        fun detect(
            x5c: List<String>?,
            jwk: Any?,
            kid: String?,
        ): JarSignerMethod =
            when {
                !x5c.isNullOrEmpty() -> X5C
                jwk != null -> JWK
                kid?.startsWith("did:") == true -> DID
                else -> CUSTOM
            }
    }
}

/**
 * JAR (JWT Secured Authorization Request) constants and validation utilities.
 *
 * Per RFC 9101 and OpenID4VP 1.0 Final specification.
 */
object JarConstants {
    /**
     * Required JWT typ header value for JAR requests.
     * Per OpenID4VP 1.0 Section 5.6, JAR MUST have typ header "oauth-authz-req+jwt".
     */
    const val JAR_JWT_TYP = "oauth-authz-req+jwt"
}

/**
 * Client Identifier Prefix values as defined in OpenID4VP 1.0 Final specification section 5.9.3.
 *
 * The Client Identifier Prefix is a string that may be prefixed to the Client Identifier in the
 * client_id parameter, separated by a : (colon) character.
 *
 * Syntax: <client_id_prefix>:<orig_client_id>
 *
 * According to spec section 5.9.1:
 * - Wallets MUST use the presence of a : character and content preceding it to determine if a prefix is used
 * - If : is present and the prefix is recognized, interpret according to that prefix
 * - If : is not present, treat as PRE_REGISTERED (fallback to RFC6749 behavior)
 * - If : is present but prefix is not recognized, can treat as PRE_REGISTERED or refuse
 *
 * @property prefix The prefix string used in the client_id (null for PRE_REGISTERED)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientIdScheme", exact = true)
@Serializable
@JsExportCompat
enum class ClientIdScheme(
    val prefix: String?,
) {
    /**
     * Pre-registered client (fallback when no prefix is present or prefix is not recognized).
     * This is equivalent to the RFC6749 default behavior.
     * No prefix value (null).
     */
    PRE_REGISTERED(null),

    /**
     * The Client Identifier is the Verifier's Redirect URI (or Response URI).
     * Prefix: "redirect_uri"
     * Example: redirect_uri:https://client.example.org/cb
     */
    REDIRECT_URI("redirect_uri"),

    /**
     * The Client Identifier is an Entity Identifier defined in OpenID Federation.
     * Prefix: "openid_federation"
     * Example: openid_federation:https://federation-verifier.example.com
     */
    OPENID_FEDERATION("openid_federation"),

    /**
     * The Client Identifier is a Decentralized Identifier (DID).
     * Prefix: "decentralized_identifier"
     * Example: decentralized_identifier:did:example:123
     */
    DECENTRALIZED_IDENTIFIER("decentralized_identifier"),

    /**
     * The Client Identifier uses Verifier attestation JWT for authentication.
     * Prefix: "verifier_attestation"
     * Example: verifier_attestation:verifier.example
     */
    VERIFIER_ATTESTATION("verifier_attestation"),

    /**
     * The Client Identifier is a DNS name matching an X.509 certificate SAN entry.
     * Prefix: "x509_san_dns"
     * Example: x509_san_dns:client.example.org
     */
    X509_SAN_DNS("x509_san_dns"),

    /**
     * The Client Identifier is a URI matching an X.509 certificate SAN entry.
     * Prefix: "x509_san_uri"
     * Example: x509_san_uri:https://client.example.org
     */
    X509_SAN_URI("x509_san_uri"),

    /**
     * The Client Identifier is a hash of an X.509 certificate.
     * Prefix: "x509_hash"
     * Example: x509_hash:Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk
     */
    X509_HASH("x509_hash"),

    /**
     * Reserved prefix for Digital Credentials API usage (Appendix A.2).
     * Wallets MUST NOT accept this prefix in requests.
     * Prefix: "origin"
     */
    ORIGIN("origin"),
    ;

    /**
     * Indicates whether JAR (JWT Secured Authorization Request) is allowed for this scheme.
     *
     * Per OpenID4VP 1.0 Final Section 5.2:
     * - redirect_uri: JAR MUST NOT be used
     * - origin: Reserved for DC API, no JAR
     * - All others: JAR is allowed or required
     */
    val jarAllowed: Boolean
        get() =
            when (this) {
                REDIRECT_URI, ORIGIN -> false
                else -> true
            }

    /**
     * Indicates whether JAR is required for this scheme.
     *
     * Per OpenID4VP 1.0 Final Section 5.2:
     * - x509_san_dns, x509_san_uri, x509_hash: JAR required with x5c
     * - decentralized_identifier: JAR required with DID
     * - verifier_attestation: JAR required
     */
    val jarRequired: Boolean
        get() =
            when (this) {
                X509_SAN_DNS, X509_SAN_URI, X509_HASH,
                DECENTRALIZED_IDENTIFIER, VERIFIER_ATTESTATION,
                -> true

                else -> false
            }

    /**
     * The allowed JAR signer methods for this client_id scheme.
     *
     * Per OpenID4VP 1.0 Final Section 5.2 and RFC 9101:
     *
     * - **PRE_REGISTERED**: Any method (custom, did, jwk, x5c) - trust is pre-configured
     * - **REDIRECT_URI**: Empty - JAR MUST NOT be used
     * - **X509_SAN_DNS**: x5c only - certificate SAN must contain client_id
     * - **X509_SAN_URI**: x5c only - certificate SAN must contain client_id
     * - **X509_HASH**: x5c only - certificate hash must match client_id
     * - **DECENTRALIZED_IDENTIFIER**: did only - signer DID must match client_id
     * - **VERIFIER_ATTESTATION**: did, federation, jwk, x5c, custom
     * - **OPENID_FEDERATION**: Empty - handled via federation trust chain
     * - **ORIGIN**: Empty - reserved for DC API, no JAR
     *
     * @return Set of allowed signer methods, empty if JAR is not allowed
     */
    val allowedSignerMethods: Set<JarSignerMethod>
        get() =
            when (this) {
                PRE_REGISTERED -> {
                    setOf(
                        JarSignerMethod.CUSTOM,
                        JarSignerMethod.DID,
                        JarSignerMethod.JWK,
                        JarSignerMethod.X5C,
                    )
                }

                REDIRECT_URI -> {
                    emptySet()
                }

                // JAR MUST NOT be used
                X509_SAN_DNS -> {
                    setOf(JarSignerMethod.X5C)
                }

                X509_SAN_URI -> {
                    setOf(JarSignerMethod.X5C)
                }

                X509_HASH -> {
                    setOf(JarSignerMethod.X5C)
                }

                DECENTRALIZED_IDENTIFIER -> {
                    setOf(JarSignerMethod.DID)
                }

                VERIFIER_ATTESTATION -> {
                    setOf(
                        JarSignerMethod.DID,
                        JarSignerMethod.FEDERATION,
                        JarSignerMethod.JWK,
                        JarSignerMethod.X5C,
                        JarSignerMethod.CUSTOM,
                    )
                }

                OPENID_FEDERATION -> {
                    emptySet()
                }

                // Handled via federation trust chain
                ORIGIN -> {
                    emptySet()
                } // Reserved for DC API
            }

    /**
     * Validates whether a given JAR signer method is allowed for this scheme.
     *
     * @param method The signer method to validate
     * @return true if the method is allowed, false otherwise
     */
    fun isSignerMethodAllowed(method: JarSignerMethod): Boolean = method in allowedSignerMethods

    companion object {
        /**
         * Parse the Client Identifier to determine the Client Identifier Prefix.
         *
         * According to OpenID4VP 1.0 Final spec section 5.9.1:
         * - Use the presence of a : character and content preceding it to determine the prefix
         * - The prefix is the string before the (first) : character
         * - If : is not present, treat as PRE_REGISTERED
         * - If : is present but prefix is not recognized, can treat as PRE_REGISTERED or refuse
         *
         * @param clientId The full client_id value from the authorization request
         * @return The parsed ClientIdScheme, defaults to PRE_REGISTERED if no recognized prefix
         */
        fun fromClientId(clientId: String): ClientIdScheme {
            val colonIndex = clientId.indexOf(':')

            // No colon means pre-registered client
            if (colonIndex == -1) {
                return PRE_REGISTERED
            }

            // Extract the prefix (everything before the first colon)
            val extractedPrefix = clientId.substring(0, colonIndex)

            // Find matching enum by prefix value
            return entries.find { it.prefix == extractedPrefix }
                ?: PRE_REGISTERED // Fallback if prefix not recognized
        }

        /**
         * Extracts the original client identifier (without the prefix).
         *
         * @param clientIdWithScheme The full client_id value from the authorization request
         * @return The original client identifier without the prefix and colon
         */
        fun extractClientIdWithoutScheme(clientIdWithScheme: String): String {
            val colonIndex = clientIdWithScheme.indexOf(':')
            return if (colonIndex == -1) {
                clientIdWithScheme
            } else {
                clientIdWithScheme.substring(colonIndex + 1)
            }
        }

        /**
         * Find ClientIdScheme by prefix value.
         *
         * @param prefix The prefix string to look up
         * @return The matching ClientIdScheme, or null if not found
         */
        fun fromPrefix(prefix: String?): ClientIdScheme? = entries.find { it.prefix == prefix }
    }
}
