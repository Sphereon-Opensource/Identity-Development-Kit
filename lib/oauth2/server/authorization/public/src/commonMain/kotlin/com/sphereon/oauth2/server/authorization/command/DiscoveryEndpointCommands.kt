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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

// ============================================================================
// BuildServerMetadataCommand
// ============================================================================

/**
 * Arguments for building authorization server metadata
 */
data class BuildServerMetadataArgs(
    val serverId: String? = null,
    val baseUrlOverride: String? = null,
)

/**
 * Build server metadata command
 *
 * RFC 8414: OAuth 2.0 Authorization Server Metadata
 *
 * Builds the complete authorization server metadata document from
 * the current server configuration. Only valid for HOSTED mode servers.
 */
interface BuildServerMetadataCommand : ServiceCommand<BuildServerMetadataArgs, AuthorizationServerMetadata, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.discovery.metadata"
    }
}

// ============================================================================
// BuildSignedAuthorizationServerMetadataCommand
// ============================================================================

/**
 * Arguments for building the JWS-signed copy of the authorization server metadata
 * document. The [metadata] is the unsigned metadata produced by
 * [BuildServerMetadataCommand]; this command wraps it in a JWS Compact Serialisation
 * with the registered RFC 8414 §2 claims (sub, iat) overlaid as top-level members.
 *
 * [signingKey] identifies which key in the AS's KMS / SigningKeyStore to sign with.
 * [identifierMode] controls whether the protected header references the key by `kid`
 * or another supported identifier form. The default is `KID`, matching the AS JWKS
 * publication and rotation model.
 */
data class BuildSignedAuthorizationServerMetadataArgs(
    val metadata: AuthorizationServerMetadata,
    val signingKey: com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult,
    val identifierMode: com.sphereon.crypto.jose.jws.JwsIdentifierMode = com.sphereon.crypto.jose.jws.JwsIdentifierMode.KID,
)

/**
 * Build the JWS-signed copy of the AS metadata document per RFC 8414 §2. The output
 * is a JWS Compact Serialisation embeddable as the `signed_metadata` JSON member of
 * the unsigned document. RPs that pin a key for the AS verify the JWS against JWKS
 * and trust the signed copy over the unsigned one — defends against metadata
 * tampering between the AS and the RP.
 *
 * Pattern is borrowed directly from `oid4vci.issuer.signedmetadata` (OID4VCI 1.0
 * §12.2.3 has the same shape: top-level metadata as JWT claims plus sub + iat). The
 * `typ` header is `oauth-as-metadata+jwt` to distinguish from the OID4VCI variant.
 */
interface BuildSignedAuthorizationServerMetadataCommand :
    ServiceCommand<
        BuildSignedAuthorizationServerMetadataArgs,
        com.sphereon.crypto.jose.jws.JwtCompactResult,
        IdkError,
    > {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.discovery.signed-metadata"
        const val JWT_TYP: String = "oauth-as-metadata+jwt"
    }
}
