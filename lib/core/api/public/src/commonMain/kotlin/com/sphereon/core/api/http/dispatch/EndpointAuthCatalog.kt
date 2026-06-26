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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.HasOrder

/**
 * Startup, metadata-only index of which routes are reachable without an accepted
 * bearer token, derived from each endpoint's
 * [EndpointAuthPolicy][com.sphereon.core.api.http.describe.EndpointAuthPolicy].
 *
 * Lives in App scope (like [HttpAdapterCatalog]) so the Layer-1 authentication gate
 * can consult it without instantiating Session-scoped adapters.
 *
 * **Fail-closed:** a request that does not match a known
 * [PUBLIC][com.sphereon.core.api.http.describe.EndpointAuthPolicy.PUBLIC] endpoint is
 * treated as protected. A forgotten declaration therefore requires a token rather
 * than silently exposing a route.
 *
 * This is authentication posture only; Layer-2 command authorization (role rules keyed
 * by command id) always applies regardless of [isPublic].
 *
 * Like [HttpAdapterCatalog] this extends [HasOrder] so a higher-priority implementation
 * (lower [getOrder] value) can replace the default.
 */
@JsExportCompat
interface EndpointAuthCatalog : HasOrder {
    /**
     * True when [method] + [resolvedPath] maps to an endpoint declared PUBLIC. The path
     * is the full request path as seen at the Layer-1 gate (server prefix and any tenant
     * slug still present); the catalog accounts for the adapter's tenant path policy when
     * matching.
     */
    fun isPublic(
        method: String,
        resolvedPath: String,
    ): Boolean
}
