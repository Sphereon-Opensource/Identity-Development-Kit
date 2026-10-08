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

package com.sphereon.oauth2.common.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves which authorization server instance id is active for a given HTTP request.
 *
 * Implementations match the request against configured discriminators on each
 * [OAuth2ServerInstanceConfig] (e.g. `issuer`, `issuerTemplate`) and
 * return the matching server id from [OAuth2ServersConfig.servers]. The HTTP
 * adapter publishes the result on [MutableOAuth2ServerInstanceIdProvider] so
 * downstream session-scoped collaborators read it without taking the id as a
 * method argument.
 *
 * Tenant-aware routing strategies live above IDK; downstream layers contribute
 * their own resolver implementation that replaces the default IDK binding.
 */
@JsExportCompat
interface OAuth2ServerInstanceResolver {
    suspend fun resolve(request: GenericHttpRequest): IdkResult<String, IdkError>
}
