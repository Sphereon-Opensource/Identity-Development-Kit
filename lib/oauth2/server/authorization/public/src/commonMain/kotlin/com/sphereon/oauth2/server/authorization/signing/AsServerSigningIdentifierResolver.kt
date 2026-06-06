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

package com.sphereon.oauth2.server.authorization.signing

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult

/**
 * Resolves the AS sign-time signing identifier.
 *
 * This is the single seam every AS sign path (id_token, access_token, logout_token, signed
 * metadata, signed authorization/JARM responses) and the JWKS endpoint go through to obtain the
 * current ACTIVE signing key, and the abstraction OID4VCI issuance reuses when it signs a
 * deferral-scoped access token with the same key as the wallet's original access token. Because
 * resolution is `suspend` the implementation reads its key store directly — no `runBlocking`
 * bridge — so the AS signs identically on every Kotlin target (JVM, JS, wasmJs, native).
 *
 * Lives in the public surface so cross-module consumers (e.g. the OID4VCI issuer) can depend on
 * the abstraction without depending on the authorization-server implementation module; the impl
 * module contributes the binding.
 */
interface AsServerSigningIdentifierResolver {
    /**
     * The current sign-time identifier, or `null` when this process should not sign with an AS
     * server key — either it does not host an AS (no `oauth2.servers.*` declared) or it runs
     * opaque tokens with OIDC disabled. The returned pointer is resolved to private bytes by the
     * KMS lazily at signing time; the same key info feeds the JWKS endpoint so RP-side
     * verification picks the matching entry.
     */
    suspend fun resolveSigningIdentifier(): ManagedIdentifierOptsOrResult?
}
