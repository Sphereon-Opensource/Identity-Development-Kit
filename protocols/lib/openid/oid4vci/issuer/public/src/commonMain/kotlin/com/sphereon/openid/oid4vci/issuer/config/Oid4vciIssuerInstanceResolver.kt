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

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves which OID4VCI issuer instance id is active for a given HTTP request.
 *
 * The HTTP adapter publishes the result on [MutableOid4vciIssuerInstanceIdProvider] so downstream
 * session-scoped collaborators (notably the issuer config provider) read it without taking the id
 * as a method argument. The result must be a canonical issuer resource UUID. A `null`, invalid, or
 * error result fails closed at the HTTP boundary and never selects a singular config namespace.
 *
 * Tenant-aware routing strategies live above IDK; downstream layers (EDK/VDX) contribute their own
 * resolver implementation that replaces the default IDK binding — e.g. mapping a request to a
 * persisted issuer resource UUID under `${INSTANCES_NAMESPACE}.<id>`.
 *
 * Twin of the OAuth2 `OAuth2ServerInstanceResolver`.
 */
@JsExportCompat
interface Oid4vciIssuerInstanceResolver {
    suspend fun resolve(request: GenericHttpRequest): IdkResult<String?, IdkError>
}
