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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.compat.JsExportCompat

/**
 * Per-request seam exposing the active `oidc_login_sid` cookie value (if any) for the inbound
 * HTTP request. Lives at `SessionScope` (one HTTP request = one session in the OAuth2 AS).
 *
 * The HTTP adapter for `/authorize` and `/authorize/callback` reads the cookie at request entry
 * and publishes the id here, so downstream session-scoped collaborators (e.g. the `prompt` /
 * `max_age` / `id_token_hint` handlers added in later groups) can locate the active
 * [OidcLoginSession] without taking the id as a method argument.
 *
 * `null` means no cookie was present, the cookie was malformed, or the request hit an entry
 * point that does not consume the login session.
 */
@JsExportCompat
interface OidcLoginSessionIdProvider {
    fun currentLoginSessionId(): String?
}

/**
 * Mutable counterpart of [OidcLoginSessionIdProvider]. The HTTP adapter calls
 * [setCurrentLoginSessionId] before delegating to downstream session-scoped collaborators, then
 * clears it via [clearCurrentLoginSessionId] on exit so the holder does not leak across
 * unrelated dispatches that happen to share the same session container.
 */
@JsExportCompat
interface MutableOidcLoginSessionIdProvider : OidcLoginSessionIdProvider {
    fun setCurrentLoginSessionId(sessionId: String)

    fun clearCurrentLoginSessionId()
}
