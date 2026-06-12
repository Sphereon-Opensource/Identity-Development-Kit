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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Maps an OAuth2 `client_id` (plus optional request host) to an opaque application /
 * login-surface id, or `null` when no mapping exists. The id is opaque to IDK: it is stamped
 * onto [com.sphereon.oauth2.server.authorization.model.AuthorizationSession.applicationId] at
 * session mint and threaded through [AuthenticationContext] so authentication providers can
 * scope their behaviour per application without IDK knowing what an "application" is.
 *
 * IDK ships a none-resolver
 * ([com.sphereon.oauth2.server.authorization.impl.provider.NoneClientApplicationResolver])
 * that always returns `Ok(null)`; richer runtimes replace it via
 * `@ContributesBinding(..., replaces = [NoneClientApplicationResolver::class])`.
 */
interface ClientApplicationResolver {
    /**
     * Resolve the application / login-surface id for [clientId].
     *
     * @param clientId OAuth2 client identifier from the authorization request
     * @param requestHost Optional host the request arrived on, for host-based surfaces
     * @return The opaque application id, `Ok(null)` when no mapping exists, or an error
     */
    suspend fun resolveApplicationId(
        clientId: String,
        requestHost: String? = null,
    ): IdkResult<String?, IdkError>
}
