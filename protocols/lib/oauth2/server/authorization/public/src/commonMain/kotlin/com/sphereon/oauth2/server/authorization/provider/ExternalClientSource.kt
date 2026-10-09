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
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

/**
 * Resolves clients that are not registered with this authorization server but can be established from their
 * `client_id` alone, for example a client identified by a URL within a trust framework the deployment accepts.
 *
 * The client registry consults every bound source only when neither configuration nor storage holds the client.
 * A source returns `null` for a client it does not recognize or does not trust, and an error only when it could not
 * decide. The returned registration carries the keys a Request Object or client assertion must verify with; it is
 * not proof that a request came from the client.
 */
interface ExternalClientSource {
    suspend fun resolve(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError>
}
