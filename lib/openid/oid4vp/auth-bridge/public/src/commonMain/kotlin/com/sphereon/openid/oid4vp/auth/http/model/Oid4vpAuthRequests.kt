/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.auth.http.model

import kotlinx.serialization.Serializable

/**
 * Request to create a new OID4VP authentication session.
 *
 * POST /auth/oid4vp/sessions
 *
 * @property queryId Query ID to use for this session. Determines which credential
 *   query and claims mapping configuration to use. If not provided, the default
 *   from configuration is used (if configured).
 * @property oauthSessionId OAuth2 session ID to link with this OID4VP session.
 *   Used for correlating the authentication with an OAuth2 authorization flow.
 * @property returnUrl URL to redirect to after successful authentication.
 *   Used by the QR code page for automatic redirection.
 * @property ttlSeconds Session TTL in seconds. Overrides the default from configuration.
 */
@Serializable
data class CreateOid4vpAuthSessionRequest(
    val queryId: String? = null,
    val clientId: String? = null,
    val oauthSessionId: String? = null,
    val returnUrl: String? = null,
    val requestedProjection: String? = null,
    val ttlSeconds: Long? = null,
    val forceReconciliation: Boolean = false
)
