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

package com.sphereon.openid.oid4vp.verifier.spi

/**
 * Server-internal seam that yields the key name a verifier's request object must be signed under.
 *
 * A deployment that manages signing material centrally binds an implementation; it derives the name
 * from its own server-side binding for ([tenantId], [verifierInstanceId]) and never from a value a
 * caller or a configuration property supplied. While an implementation is bound it is the only
 * source of the signing key name: a `keyAlias` and a `providerId` carried in configuration are not
 * read at all, and a null answer means "refuse to sign", never "pick something else" and never
 * "create one".
 *
 * The answer must be stable for the lifetime of a binding. Create-time `client_id` substitution and
 * fetch-time request-object signing both resolve through this seam, and the verifier's DID / x509
 * identifier is derived from the resolved key's public JWK, so a name that changed between those two
 * points would publish an identifier the signature cannot be verified against.
 *
 * Implementations must answer identically for every reason a binding cannot be honoured, so no
 * caller can tell an absent binding apart from one that exists but is not usable, and must never
 * create key material as a side effect of resolution.
 *
 * The returned name is process-internal. Never place it on a DTO, a REST response, or any
 * serializable model.
 */
interface VerifierSigningKeyNameResolver {
    suspend fun resolveRequestObjectSigningKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String?
}
