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

package com.sphereon.statuslist

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Source of the status-list definitions a deployment hosts.
 *
 * Status lists are a standalone, protocol-neutral concern: a hosted list assigns each referenced
 * artifact an index and a bit value, and is dereferenced from the `uri` embedded in that artifact.
 * That artifact is NOT necessarily a credential — IETF Token Status List (RFC draft) covers any
 * referenced token (access tokens, etc.) just as the W3C Bitstring list covers credentials. So the
 * definitions live here, decoupled from any one issuance protocol, and are hosted at their own root
 * path (`/statuslists/{id}`).
 *
 * A credential issuer (OID4VCI) consumes these only indirectly: it declares a *binding* from a
 * credential type to a list `correlationId`, then resolves the list's spec/purposes through this
 * provider. A token issuer can reference the very same definitions.
 */
@JsExportCompat
interface StatusListDefinitionsProvider {
    /** All configured status-list definitions. Empty when the deployment hosts none. */
    val definitions: List<CreateStatusListArgs>

    /**
     * The definition with this [correlationId] (the `{id}` segment of the hosting path
     * `/statuslists/{id}`), or null when undefined.
     */
    fun byId(correlationId: String): CreateStatusListArgs?

    /**
     * Resolve a definition at an issuance/runtime boundary. Implementations backed by suspend
     * persistence may override this without forcing the synchronous config/metadata surface to
     * block. The default keeps existing providers source-compatible.
     */
    suspend fun resolve(correlationId: String): IdkResult<CreateStatusListArgs?, IdkError> = Ok(byId(correlationId))
}
