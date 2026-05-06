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

package com.sphereon.oauth2.server.authorization.impl.resolver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Resolves the signing JWKS registered for a client, used by `private_key_jwt` client auth
 * (RFC 7523 §3) to constrain acceptable assertion-signing keys to the set the client registered
 * via RFC 7591 §2 `jwks` / `jwks_uri`.
 *
 * WP2 introduced as a narrow, OAuth2-AS-local resolver. (spec-aligned) will add an
 * `IssuerJwksResolver` for OP ID-token-validation on the RP side; once both are in place WP5
 * will merge them into a shared implementation.
 */
public interface ClientJwksResolver {
    /**
     * Resolve the registered JWKS for a client. Currently only inline `jwks` is supported;
     * `jwks_uri` resolution is deferred to WP5 (needs HTTP fetch + cache + rotation handling).
     *
     * @return the client's registered keys, or an error if the client has no registered key material.
     */
    public suspend fun resolveFor(client: ClientRegistration): IdkResult<List<Jwk>, AuthorizationServerError>
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClientJwksResolver>())
public class DefaultClientJwksResolver : ClientJwksResolver {
    override suspend fun resolveFor(client: ClientRegistration): IdkResult<List<Jwk>, AuthorizationServerError> {
        val inline = client.jwks
        if (inline != null) {
            return Ok(inline)
        }
        if (client.jwksUri != null) {
            // WP5 follow-up: fetch + cache the client's jwks_uri. For OIDF Basic conformance we
            // require clients to register inline jwks until then.
            return Err(
                AuthorizationServerError.InvalidClient(
                    details = "Client '${client.clientId}' registered a jwks_uri but remote JWKS fetch is not yet supported; register inline jwks instead",
                ),
            )
        }
        return Err(
            AuthorizationServerError.InvalidClient(
                details = "Client '${client.clientId}' has no registered JWKS for assertion-based client authentication",
            ),
        )
    }
}
