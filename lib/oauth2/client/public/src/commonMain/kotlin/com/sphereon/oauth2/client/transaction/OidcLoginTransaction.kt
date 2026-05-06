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

package com.sphereon.oauth2.client.transaction

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

/**
 * Immutable record of an in-flight OIDC login initiated by the RP.
 *
 * Produced by `OAuth2ClientImpl.initiateOidcLogin` and retrieved by state during callback
 * handling. Holds every piece of information the RP must bind the callback back to: the CSRF
 * `state`, the replay-protection `nonce` for ID token validation, the PKCE verifier, and the
 * issuer/redirect URI so the token exchange can be replayed against the original AS.
 *
 * Persisted via [OidcLoginTransactionStore]; consumed atomically at callback time.
 */
@JsExportCompat
public data class OidcLoginTransaction
    @JvmOverloads
    constructor(
        public val state: String,
        public val nonce: String,
        public val pkceVerifier: String,
        public val issuer: String,
        public val redirectUri: String,
        public val responseMode: OAuth2ResponseMode,
        public val createdAt: Instant,
        public val expiresAt: Instant,
        public val tenantId: String? = null,
    )
