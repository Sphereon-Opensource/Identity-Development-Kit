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

package com.sphereon.openid.oid4vp.verifier.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves which OID4VP verifier instance id is active for a given HTTP request.
 *
 * The HTTP adapter publishes the result on [MutableOid4vpVerifierInstanceIdProvider] so downstream
 * session-scoped collaborators (notably the request-object signing config provider) read it without
 * taking the id as a method argument. A `null`/error result leaves the holder empty, which makes
 * config reads fall back to the singular verifier namespace (`oid4vp.verifier.*`).
 *
 * Tenant-aware routing strategies live above IDK; downstream layers (EDK/VDX) contribute their own
 * resolver implementation that replaces the default IDK binding — e.g. mapping a request to a
 * persisted verifier party id under `${INSTANCES_NAMESPACE}.<id>`.
 *
 * Twin of the OID4VCI `Oid4vciIssuerInstanceResolver` and the OAuth2 `OAuth2ServerInstanceResolver`.
 */
@JsExportCompat
interface Oid4vpVerifierInstanceResolver {
    suspend fun resolve(request: GenericHttpRequest): IdkResult<String?, IdkError>
}
