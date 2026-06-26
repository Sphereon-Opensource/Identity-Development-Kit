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

package com.sphereon.oauth2.server.authorization.command.discovery

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contracts for the discovery and JWKS routes. Each is a thin shell over
 * [HandleDiscoveryRequestCommand] / [com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand];
 * the command parses request data and renders the response, leaving business logic in the
 * underlying ServiceCommand.
 *
 * The OAuth2 server metadata and OIDC discovery routes both serve the request via
 * [HandleDiscoveryRequestCommand]; the OIDC variant first checks the `oidc` feature policy and
 * returns 404 when disabled. Tenant-path variants are matched by the impl's `supports()` override
 * which accepts both the bare and the `{tenant-path}` form.
 */

@JsExportCompat
interface OAuth2ServerMetadataHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.discovery.oauth2-server-metadata-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/oauth-authorization-server",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "serverMetadataDefault",
                commandId = COMMAND_ID,
                tags = setOf("discovery"),
                summary = "RFC 8414 OAuth 2.0 Authorization Server Metadata",
                // RFC 8414 server metadata is an anonymous, well-known discovery document.
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )

        const val TENANT_PATH_PATTERN = "/.well-known/oauth-authorization-server/{tenant-path}"
    }
}

@JsExportCompat
interface OpenidDiscoveryHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.discovery.openid-configuration-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/openid-configuration",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "openidConfigurationDefault",
                commandId = COMMAND_ID,
                tags = setOf("discovery", "oidc"),
                summary = "OpenID Connect Discovery 1.0",
                // OIDC discovery is an anonymous, well-known document.
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )

        const val TENANT_PATH_PATTERN = "/.well-known/openid-configuration/{tenant-path}"
    }
}

@JsExportCompat
interface JwksHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.discovery.jwks-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/jwks.json",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "jwks",
                commandId = COMMAND_ID,
                tags = setOf("discovery", "jwks"),
                summary = "JSON Web Key Set used to verify access tokens and id_tokens",
                // JWKS must be anonymously fetchable: every token validator (operator bearer auth,
                // satellite RestAuth) resolves it as <issuer>/.well-known/jwks.json.
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}
