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

package com.sphereon.crypto.resolution.extern

import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import kotlinx.serialization.json.JsonObject

/**
 * Result of OIDC/OAuth2 AS metadata discovery.
 * Resolves AS metadata and JWKS from the authorization server.
 */
data class OidcDiscoveryExternalIdentifierResult(
    override val identifierOpts: ExternalIdentifierOidcDiscoveryOpts,
    override val jwks: Array<ExternalJwkInfo>,
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
    val authorizationServerMetadata: JsonObject,
    val jwksUri: String? = null,
) : ExternalIdentifierResult(
        method = IdentifierMethodDefaults.OIDC_DISCOVERY,
        jwks = jwks,
        keyInfo = keyInfo,
        identifierOpts = identifierOpts,
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is OidcDiscoveryExternalIdentifierResult) {
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
        if (authorizationServerMetadata != other.authorizationServerMetadata) {
            return false
        }
        if (jwksUri != other.jwksUri) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = identifierOpts.hashCode()
        result = 31 * result + jwks.contentHashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + authorizationServerMetadata.hashCode()
        result = 31 * result + (jwksUri?.hashCode() ?: 0)
        return result
    }
}

/**
 * Marker interface for the OIDC discovery external identifier resolution service.
 */
interface OidcDiscoveryExternalIdentifierService : ExternalIdentifierService
