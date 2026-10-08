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

package com.sphereon.openid.oid4vci.issuer.spi

/**
 * Server-internal seam that yields the key names an issuer signs its metadata with and decrypts
 * credential requests with.
 *
 * A deployment that manages key material centrally binds an implementation; it derives each name
 * from its own server-side binding for ([tenantId], [issuerInstanceId]) and never from a value a
 * caller or a configuration property supplied. While an implementation is bound it is the only
 * source of these names: the `signingKeyAlias`, `signingKmsProviderId`,
 * `encryption.request.decryptionKeyAlias`, and `encryption.request.decryptionKmsProviderId`
 * properties are not read at all, and a null answer means "refuse", never "pick something else" and
 * never "create one".
 *
 * Implementations must answer identically for every reason a binding cannot be honoured, so no
 * caller can tell an absent binding apart from one that exists but is not usable, and must never
 * create key material as a side effect of resolution.
 *
 * The returned names are process-internal. Never place one on a DTO, a REST response, or any
 * serializable model.
 */
interface IssuerKeyNameResolver {
    /** Key the issuer signs `signed_metadata` and its credentials with, or null to refuse. */
    suspend fun resolveMetadataSigningKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String?

    /** Key the issuer decrypts encrypted credential requests with, or null to refuse. */
    suspend fun resolveRequestDecryptionKeyName(
        tenantId: String,
        issuerInstanceId: String,
    ): String?

    /**
     * Key the issuer signs one specific credential configuration with, or null to refuse.
     *
     * Signing a single credential configuration under its own key is unusual but legitimate: an
     * issuer may be required to separate one credential type's key from the rest. The default
     * falls back to the metadata signing key, so an issuer that does not separate keys keeps
     * exactly one key and nothing changes for it.
     */
    suspend fun resolveCredentialSigningKeyName(
        tenantId: String,
        issuerInstanceId: String,
        credentialConfigurationId: String,
    ): String? = resolveMetadataSigningKeyName(tenantId, issuerInstanceId)
}
