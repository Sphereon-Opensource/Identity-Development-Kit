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

import com.sphereon.core.compat.JsExportCompat

/**
 * Per-request seam exposing the active authorization server instance id.
 *
 * Lives at `SessionScope` (one HTTP request = one session in the OAuth2 AS).
 * The HTTP adapter sets the active id at the entry of every endpoint that may
 * reach a session-scoped collaborator that needs to scope config under
 * `${OAuth2ServerInstanceConfig.CONFIG_PREFIX}.<asId>`. Collaborators read it
 * via [currentAsInstanceId] without taking the id as a method argument.
 *
 * `null` means no AS instance has been resolved for the current request, e.g.
 * the request hit an entry point that does not need per-instance routing or
 * the resolution step has not run yet. Consumers fall back to a global default
 * config key in that case.
 */
@JsExportCompat
interface OAuth2ServerInstanceIdProvider {
    fun currentAsInstanceId(): String?
}

/**
 * Mutable counterpart of [OAuth2ServerInstanceIdProvider]. The HTTP adapter
 * (or any other request entry point) calls [setCurrentAsInstanceId] before
 * delegating to downstream session-scoped collaborators, then clears it via
 * [clearCurrentAsInstanceId] on exit so the holder does not leak across
 * unrelated dispatches that share the session.
 */
@JsExportCompat
interface MutableOAuth2ServerInstanceIdProvider : OAuth2ServerInstanceIdProvider {
    fun setCurrentAsInstanceId(asInstanceId: String)

    fun clearCurrentAsInstanceId()
}
