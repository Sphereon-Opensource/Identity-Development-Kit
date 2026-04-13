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

package com.sphereon.core.api.http.describe

/**
 * Metadata-only contributor for [HttpAdapterDescription].
 *
 * Host applications must be able to build a startup catalog without instantiating Session-scoped adapters.
 * This provider can be safely created in App scope and should not depend on tenant/session scoped services.
 */
interface HttpAdapterDescriptorProvider {
    /**
     * Stable identifier of the adapter this provider describes.
     * Must match the runtime [com.sphereon.core.api.http.HttpAdapter.id].
     */
    val id: String

    /**
     * Describe mount + endpoints for this adapter.
     *
     * Notes:
     * - This is metadata-only; do not depend on request/session context.
     * - Future iterations may allow config layering; keep the description deterministic for a given runtime.
     */
    fun describe(): HttpAdapterDescription
}
