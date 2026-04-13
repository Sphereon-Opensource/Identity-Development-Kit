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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.di.HasOrder

/**
 * Catalog-driven dispatcher that:
 * - selects the correct adapter by serverPrefix/basePath/endpoint match
 * - normalizes the request path for compatibility (strip tenant segment + strip serverPrefix)
 * - applies tenant resolution precedence when both existing and path-based tenant ids are available
 *
 * **Replacement via DI:**
 * This interface extends [HasOrder] so multiple implementations can be contributed,
 * and the highest-priority one (lowest [getOrder] value) wins. Use [com.sphereon.di.selectByOrder]
 * to select the winning implementation from a `Set<HttpAdapterDispatcher>`.
 *
 * The codebase standard is to depend on interfaces (not concrete implementations).
 */
interface HttpAdapterDispatcher : HasOrder {
    suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse
}
