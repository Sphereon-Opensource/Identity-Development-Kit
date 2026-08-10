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

package com.sphereon.statuslist.spi

/**
 * Server-internal seam that yields the key name a status list's token must be signed under.
 *
 * A deployment that manages signing material centrally binds an implementation; it derives the name
 * from its own server-side binding for ([tenantId], [statusListId]) and never from a value a caller
 * supplied. While an implementation is bound it is the only source of the signing key name: a
 * `signingKeyAlias` carried on a definition is ignored, and a null answer means "refuse to sign",
 * never "pick something else".
 *
 * Implementations must answer identically for every reason a binding cannot be honoured, so no
 * caller can tell an absent binding apart from one that exists but is not usable, and must never
 * create key material as a side effect of resolution.
 *
 * The returned name is process-internal. Never place it on a DTO, a REST response, or any
 * serializable model.
 */
interface StatusListSigningKeyNameResolver {
    suspend fun resolveSigningKeyName(
        tenantId: String,
        statusListId: String,
    ): String?
}
