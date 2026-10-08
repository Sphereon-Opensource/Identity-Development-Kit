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

package com.sphereon.openid.oid4vp.dcql

import com.sphereon.core.compat.JsExportCompat
import io.konform.validation.Invalid
import kotlinx.serialization.Serializable

/**
 * DCQL Trusted Authority
 *
 * Specifies constraints on trusted authorities that may have issued the credential.
 *
 * OpenID4VP 1.0 Section 6.1.1:
 * "A Trusted Authorities Query allows the Verifier to specify constraints on the trusted
 * authorities that may have issued the requested Credential. This enables the Verifier
 * to trust only Credentials issued by specific entities or trust frameworks."
 *
 * Three types of trusted authorities are supported:
 * 1. `aki` - X.509 certificate Authority Key Identifier (base64url-encoded)
 * 2. `etsi_tl` - ETSI Trusted List identifier
 * 3. `openid_federation` - OpenID Federation entity identifiers (HTTPS only)
 *
 * Example:
 * ```json
 * {
 *   "trusted_authorities": [
 *     {
 *       "type": "openid_federation",
 *       "values": ["https://federation.example.com"]
 *     },
 *     {
 *       "type": "etsi_tl",
 *       "values": ["https://eidas.europa.eu/TL/EN_TL.xml"]
 *     }
 *   ]
 * }
 * ```
 *
 * @property type The type of trusted authority. Must be one of:
 *   - "aki"
 *   - "etsi_tl"
 *   - "openid_federation"
 * @property values List of values (identifiers or URLs) for this trusted authority.
 *   Must be non-empty. Format depends on type:
 *   - For aki: base64url-encoded AKI values
 *   - For etsi_tl: identifiers of ETSI Trusted Lists
 *   - For openid_federation: HTTPS URLs representing entity identifiers
 */
@Serializable
@JsExportCompat
data class DcqlTrustedAuthority(
    val type: String,
    val values: List<String>,
) {
    init {
        // Validate on construction/deserialization using konform
        val validationResult = validateDcqlTrustedAuthority(this)

        if (validationResult is Invalid) {
            val errors =
                validationResult.errors.joinToString("; ") {
                    "${it.path}: ${it.message}"
                }
            throw IllegalArgumentException("Invalid DcqlTrustedAuthority: $errors")
        }
    }

    companion object {
        /**
         * Authority Key Identifier type
         */
        const val TYPE_AKI = "aki"

        /**
         * ETSI Trusted List type
         */
        const val TYPE_ETSI_TL = "etsi_tl"

        /**
         * OpenID Federation type
         */
        const val TYPE_OPENID_FEDERATION = "openid_federation"

        /**
         * Valid trusted authority types per OpenID4VP 1.0
         */
        val VALID_TYPES =
            setOf(
                TYPE_AKI,
                TYPE_ETSI_TL,
                TYPE_OPENID_FEDERATION,
            )
    }
}
