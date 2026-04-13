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
 */

package com.sphereon.openid.oid4vp.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Credential Format for OpenID4VP
 *
 * OpenID4VP 1.0 Section 7:
 * "The vp_token is self-descriptive, meaning the format can be determined from the token itself."
 *
 * Supported formats per OpenID4VP 1.0:
 * - dc+sd-jwt: SD-JWT Digital Credentials (recommended for new deployments)
 * - mso_mdoc: ISO 18013-5 mobile driving license format
 * - jwt_vc_json: JWT Verifiable Credential
 * - jwt_vp_json: JWT Verifiable Presentation
 * - vc+sd-jwt: SD-JWT Verifiable Credential (older format identifier)
 */
@Serializable
enum class CredentialFormat(val value: String) {
    /**
     * SD-JWT Digital Credential format
     *
     * The recommended format for new deployments per OpenID4VP 1.0.
     * Structure: issuer-signed-jwt~disclosure1~disclosure2~...~kb-jwt
     */
    @SerialName("dc+sd-jwt")
    SD_JWT_DC("dc+sd-jwt"),

    /**
     * SD-JWT Verifiable Credential format (older identifier)
     *
     * Older format identifier that may still be encountered.
     * Structure is the same as dc+sd-jwt.
     */
    @SerialName("vc+sd-jwt")
    SD_JWT_VC("vc+sd-jwt"),

    /**
     * ISO mDoc (ISO 18013-5) format
     *
     * Mobile document format, typically used for mobile driving licenses.
     * Structure: Base64/Base64url encoded CBOR DeviceResponse
     */
    @SerialName("mso_mdoc")
    MSO_MDOC("mso_mdoc"),

    /**
     * JWT Verifiable Credential format
     *
     * W3C Verifiable Credential in JWT format.
     * Structure: header.payload.signature
     */
    @SerialName("jwt_vc_json")
    JWT_VC_JSON("jwt_vc_json"),

    /**
     * JWT Verifiable Presentation format
     *
     * W3C Verifiable Presentation in JWT format.
     * Structure: header.payload.signature
     */
    @SerialName("jwt_vp_json")
    JWT_VP_JSON("jwt_vp_json");

    /**
     * Check if this format is an SD-JWT variant
     */
    val isSdJwt: Boolean
        get() = this == SD_JWT_DC || this == SD_JWT_VC

    /**
     * Check if this format is a JWT variant (but not SD-JWT)
     */
    val isJwt: Boolean
        get() = this == JWT_VC_JSON || this == JWT_VP_JSON

    /**
     * Check if this format is mDoc
     */
    val isMdoc: Boolean
        get() = this == MSO_MDOC

    companion object {
        /**
         * Parse credential format from string value
         *
         * @param value String value of credential format
         * @return CredentialFormat enum value, or null if not recognized
         */
        fun fromValue(value: String): CredentialFormat? = entries.find { it.value == value }

        /**
         * Parse credential format from string, checking for partial matches
         *
         * Useful when the format string may contain variations like "sd-jwt" instead of "dc+sd-jwt"
         *
         * @param value String value that may contain format identifier
         * @return CredentialFormat enum value, or null if not recognized
         */
        fun fromValueLenient(value: String): CredentialFormat? {
            // First try exact match
            fromValue(value)?.let { return it }

            // Try partial matches
            val lowerValue = value.lowercase()
            return when {
                lowerValue.contains("sd-jwt") || lowerValue.contains("sd_jwt") -> SD_JWT_DC
                lowerValue == "mso_mdoc" || lowerValue.contains("mdoc") -> MSO_MDOC
                lowerValue.contains("jwt_vc") || lowerValue == "jwt_vc_json" -> JWT_VC_JSON
                lowerValue.contains("jwt_vp") || lowerValue == "jwt_vp_json" -> JWT_VP_JSON
                else -> null
            }
        }

        /**
         * Detect the credential format from a presentation string
         *
         * Analyzes the structure of the presentation to determine its format:
         * - SD-JWT: Contains '~' separator (e.g., "header.payload.signature~disclosure1~kb-jwt")
         * - JWT: Three base64url parts separated by dots (header.payload.signature)
         * - mDoc: Base64/Base64url encoded CBOR (no dots, longer than 20 chars)
         *
         * @param presentation The presentation string to analyze
         * @return Detected CredentialFormat, or null if format cannot be determined
         */
        fun detectFormat(presentation: String): CredentialFormat? {
            return when {
                // SD-JWT: Contains disclosure separators (~)
                presentation.contains("~") -> SD_JWT_DC

                // JWT: Three base64url parts separated by dots
                presentation.matches(JWT_PATTERN) -> JWT_VC_JSON

                // mDoc: Base64/Base64url encoded CBOR (no dots, reasonable length)
                presentation.length > 20 && !presentation.contains(".") -> MSO_MDOC

                else -> null
            }
        }

        /**
         * Check if a format string matches any known credential format
         *
         * @param format The format string to check
         * @return True if the format is recognized
         */
        fun isKnownFormat(format: String): Boolean = fromValueLenient(format) != null

        private val JWT_PATTERN = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$")
    }
}

/**
 * Extension function to detect format of a presentation string
 */
fun String.detectCredentialFormat(): CredentialFormat? = CredentialFormat.detectFormat(this)

/**
 * Extension function to check if a format string matches a CredentialFormat
 */
fun String.matchesCredentialFormat(format: CredentialFormat): Boolean {
    val detected = CredentialFormat.fromValueLenient(this)
    return detected == format || (detected?.isSdJwt == true && format.isSdJwt)
}
