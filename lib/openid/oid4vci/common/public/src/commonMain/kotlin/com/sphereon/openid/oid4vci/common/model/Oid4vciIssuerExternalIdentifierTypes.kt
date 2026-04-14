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

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalJwkInfo
import kotlinx.serialization.json.JsonObject

/**
 * Options for resolving an OID4VCI credential issuer by its issuer URL.
 *
 * The [identifier] is the base issuer URL (e.g. `https://issuer.example.com` or
 * `https://issuer.example.com/tenant1`). The resolver constructs the correct
 * `.well-known/openid-credential-issuer` URL per OID4VCI 1.1 Section 13.2.
 */
@JsExportCompat
data class ExternalIdentifierOid4vciIssuerOpts(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(
        method = IdentifierMethodDefaults.OID4VCI_ISSUER,
        identifier = identifier,
        context = context,
        lookup = lookup,
    )

/**
 * Result of OID4VCI issuer metadata resolution.
 *
 * Contains the credential issuer metadata, the resolved authorization server metadata
 * (if available), and the JWKS resolved from the authorization server.
 */
@JsExportCompat
data class Oid4vciIssuerExternalIdentifierResult(
    override val identifierOpts: ExternalIdentifierOid4vciIssuerOpts,
    override val jwks: Array<ExternalJwkInfo>,
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
    val issuerMetadata: CredentialIssuerMetadata,
    val authorizationServerUrl: String? = null,
    val authorizationServerMetadata: JsonObject? = null,
) : ExternalIdentifierResult(
        method = IdentifierMethodDefaults.OID4VCI_ISSUER,
        jwks = jwks,
        keyInfo = keyInfo,
        identifierOpts = identifierOpts,
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Oid4vciIssuerExternalIdentifierResult) {
            return false
        }
        if (identifierOpts != other.identifierOpts) {
            return false
        }
        if (!jwks.contentEquals(other.jwks)) {
            return false
        }
        if (keyInfo != other.keyInfo) {
            return false
        }
        if (issuerMetadata != other.issuerMetadata) {
            return false
        }
        if (authorizationServerUrl != other.authorizationServerUrl) {
            return false
        }
        if (authorizationServerMetadata != other.authorizationServerMetadata) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = identifierOpts.hashCode()
        result = 31 * result + jwks.contentHashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + issuerMetadata.hashCode()
        result = 31 * result + (authorizationServerUrl?.hashCode() ?: 0)
        result = 31 * result + (authorizationServerMetadata?.hashCode() ?: 0)
        return result
    }
}

/**
 * Marker interface for the OID4VCI issuer external identifier resolution service.
 */
@JsExportCompat
interface Oid4vciIssuerExternalIdentifierService : ExternalIdentifierService
