/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

data class FetchServerMetadataArgs(
    val issuer: String,
)

data class FetchJwksArgs(
    val jwksUri: String,
)

/**
 * Command for fetching Authorization Server Metadata from a well-known endpoint
 *
 * Implements RFC 8414 OAuth 2.0 Authorization Server Metadata and
 * OpenID Connect Discovery specifications.
 *
 * Tries multiple well-known URLs in order:
 * 1. {origin}/.well-known/oauth-authorization-server{path} (RFC 8414 compliant)
 * 2. {origin}{path}/.well-known/oauth-authorization-server (legacy non-compliant)
 * 3. {issuer}/.well-known/openid-configuration (OpenID Connect Discovery)
 */
interface FetchAuthorizationServerMetadataCommand : ServiceCommand<FetchServerMetadataArgs, AuthorizationServerMetadata> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.metadata.fetchserver"
    }
}

/**
 * Command for fetching JWK Set from a jwks_uri endpoint
 *
 * Used to retrieve the authorization server's public keys for signature verification
 */
interface FetchJwksCommand : ServiceCommand<FetchJwksArgs, JwkSet> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.metadata.fetchjwks"
    }
}
