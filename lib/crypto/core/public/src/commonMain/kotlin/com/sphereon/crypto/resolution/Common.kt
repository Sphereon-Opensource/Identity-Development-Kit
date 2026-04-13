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

package com.sphereon.crypto.resolution

import com.sphereon.crypto.core.IdentifierLookupType
import com.sphereon.crypto.core.KeyDTOType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.cose.CoseKeyDTOType
import com.sphereon.crypto.core.jose.JwkDTOType

data class IdentifierContext(
    val issuer: String? = null,
    val clientId: String? = null,
    val clientIdScheme: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class AdditionalIdentifierLookup(
    // Always resolve, even if we already resolved before
    override val noCache: Boolean = false,
    override val kid: String? = null,
    override val alias: String? = null,
    override val opts: Map<String, String> = emptyMap(),
    override val providerId: String? = null,
) : IdentifierLookupType

/**
 * Simplified Identifier representation
 */
data class DIDIdentifier(
    val did: String,
    val keys: List<KeyType>,
    val controllerKeyId: String? = null,
)

/**
 * Type of identifier method
 */
interface IIdentifierMethod {
    val methodName: String
}

enum class IdentifierMethodDefaults : IIdentifierMethod {
    DID,
    JWK,
    X5C,
    KID,
    KEY_ALIAS,
    KEY,
    COSE_KEY,
    OID4VCI_ISSUER,
    ENTITY_ID,
    OIDC_DISCOVERY,
    JWKS_URL,

    /** RFC 7800 Confirmation (cnf) claim - contains kid, jwk, or jku for holder binding */
    CNF,

    ;

    override val methodName: String
        get() = this.name.uppercase()
}

/**
 * Utility functions to check the type of identifier
 */
object IdentifierTypeUtils {
    fun isDidIdentifier(identifier: Any): Boolean =
        when (identifier) {
            is DIDIdentifier -> true
            is String -> identifier.startsWith("did:")
            else -> false
        }

    fun isJwkIdentifier(identifier: Any): Boolean = identifier is JwkDTOType && identifier.kty.isNotEmpty()

    fun isOidcDiscoveryIdentifier(identifier: Any): Boolean =
        identifier is String &&
            identifier.startsWith("http") &&
            identifier.contains("://") &&
            identifier.endsWith("/.well-known/openid-configuration")

    fun isJwksUrlIdentifier(identifier: Any): Boolean =
        identifier is String &&
            identifier.startsWith("http") &&
            identifier.contains("://") &&
            identifier.endsWith("jwks.json")

    fun isKeyAliasIdentifier(identifier: Any): Boolean =
        identifier is String &&
            !identifier.contains("://") &&
            !identifier.startsWith("did:") &&
            !identifier.contains(" ")

    fun isKidIdentifier(identifier: Any): Boolean =
        identifier is String &&
            !identifier.startsWith("did:") &&
            !identifier.startsWith("http")

    fun isOID4VCIssuerIdentifier(identifier: Any): Boolean =
        identifier is String &&
            identifier.startsWith("http") &&
            identifier.contains("://") &&
            identifier.contains("/.well-known/openid-credential-issuer")

    fun isKeyIdentifier(identifier: Any): Boolean = identifier is KeyDTOType

    fun isCoseKeyIdentifier(identifier: Any): Boolean = identifier is CoseKeyDTOType

    fun isOIDFEntityIdIdentifier(identifier: Any): Boolean = identifier is String && identifier.startsWith("https://")

    fun isX5cIdentifier(identifier: Any): Boolean = identifier is Collection<*> && identifier.isNotEmpty() && identifier.all { it is String }
}
