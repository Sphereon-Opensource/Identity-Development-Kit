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

package com.sphereon.credential.claims.mapper.api.model

import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A credential with its DCQL identifier and format information.
 *
 * This is the input type for the claims mapping service. Each credential
 * is identified by its DCQL credential ID and includes the raw credential
 * payload along with its format.
 *
 * @property credentialId The DCQL credential ID that this credential satisfies.
 *   This must match a credential ID from the DCQL query.
 *
 * @property format The credential format (SD-JWT, mDoc, etc.)
 *
 * @property payload The raw credential data. The format depends on the credential type:
 *   - SD-JWT: The complete SD-JWT string (issuer-jwt~disclosure1~...~kb-jwt)
 *   - mDoc: Base64-encoded CBOR data
 *   - JWT VC: The JWT string
 *
 * @property disclosedClaims Optional pre-decoded claims for SD-JWT credentials.
 *   If the holder has already decoded the SD-JWT disclosures, the resulting
 *   claims can be provided here to avoid re-parsing.
 */
@Serializable
data class CredentialWithId(
    val credentialId: String,
    val format: CredentialFormat,
    val payload: String,
    val disclosedClaims: JsonObject? = null
) {
    companion object {
        /**
         * Creates a CredentialWithId for an SD-JWT credential.
         */
        fun sdJwt(
            credentialId: String,
            payload: String,
            disclosedClaims: JsonObject? = null
        ): CredentialWithId {
            return CredentialWithId(
                credentialId = credentialId,
                format = CredentialFormat.SD_JWT_DC,
                payload = payload,
                disclosedClaims = disclosedClaims
            )
        }

        /**
         * Creates a CredentialWithId for an mDoc credential.
         */
        fun mDoc(
            credentialId: String,
            payload: String
        ): CredentialWithId {
            return CredentialWithId(
                credentialId = credentialId,
                format = CredentialFormat.MSO_MDOC,
                payload = payload
            )
        }
    }
}
