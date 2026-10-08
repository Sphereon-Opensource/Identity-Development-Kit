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

package com.sphereon.oauth2.client.metadata

import com.sphereon.core.api.IdkResult
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

/**
 * Resolves an OIDC issuer's JWKS by chaining discovery metadata + `jwks_uri` fetch.
 *
 * Used by the RP to verify ID token signatures against exactly the keys the issuer advertises:
 * the resolver enforces HTTPS on the `jwks_uri`, caches results per-issuer for a bounded TTL, and
 * exposes an explicit [invalidate] hook so callers can force re-fetch on `kid` miss (standard
 * pattern for key rotation handling).
 *
 * The default implementation (`DefaultIssuerJwksResolver`) composes
 * `FetchAuthorizationServerMetadataCommand` for discovery with the IDK identifier-resolution
 * system (`JwksUrlExternalIdentifierResolutionService`) for `jwks_uri` key resolution. Production
 * deployments that want cross-tenant caching behaviour can substitute their own implementation.
 */
public interface IssuerJwksResolver {
    /**
     * Returns the issuer's JWKS. Cache-first; fetches metadata + JWKS on miss.
     *
     * Fails with [Oauth2Error] when metadata fetch fails, `jwks_uri` is absent or non-HTTPS, or
     * the JWKS payload is invalid/empty.
     */
    public suspend fun resolve(issuer: String): IdkResult<JwkSet, Oauth2Error>

    /**
     * Resolve JWKS using pre-fetched metadata, avoiding the extra discovery round-trip when the
     * caller has already loaded the metadata document (e.g. during an OIDC login callback flow).
     */
    public suspend fun resolve(metadata: AuthorizationServerMetadata): IdkResult<JwkSet, Oauth2Error>

    /** Invalidate the cached entry for [issuer] so the next [resolve] call refetches. */
    public suspend fun invalidate(issuer: String)
}
