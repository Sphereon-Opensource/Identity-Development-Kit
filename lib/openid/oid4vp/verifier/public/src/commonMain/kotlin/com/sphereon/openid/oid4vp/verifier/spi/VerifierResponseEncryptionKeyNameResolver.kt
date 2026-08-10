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
 * Server-internal seam that yields the key name a verifier decrypts an encrypted authorization
 * response (`direct_post.jwt`) with.
 *
 * A deployment that manages key material centrally binds an implementation; it derives the name from
 * its own server-side binding for ([tenantId], [verifierInstanceId]) and never from a value a caller,
 * a configuration property, or a stored session supplied. A null answer means "refuse to accept an
 * encrypted response", never "pick something else" and never "create one".
 *
 * The answer must be stable for the lifetime of a binding. The public half of the same key is
 * published in `client_metadata.jwks` when the authorization request is created and the private half
 * performs the key agreement when the response arrives, so a name that changed between those two
 * points would advertise a key the verifier cannot decrypt under.
 *
 * Implementations must answer identically for every reason a binding cannot be honoured, so no caller
 * can tell an absent binding apart from one that exists but is not usable, and must never create key
 * material as a side effect of resolution.
 *
 * The returned name is process-internal. Never place it on a DTO, a REST response, a stored session,
 * or any serializable model.
 */
interface VerifierResponseEncryptionKeyNameResolver {
    suspend fun resolveResponseEncryptionKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String?
}
