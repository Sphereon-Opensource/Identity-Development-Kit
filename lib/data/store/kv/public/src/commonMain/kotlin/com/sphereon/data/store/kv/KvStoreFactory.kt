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
 *
 */

package com.sphereon.data.store.kv

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.JsExportCompat

/**
 * Factory for creating [KvStore] instances with the correct scope binding.
 *
 * Implementations should:
 * - use long-lived backing storage (AppScope) for non-APP scope bindings, so values survive
 *   request-scoped object lifetimes (for example REST session-per-request).
 * - partition by tenant/principal/session identifiers derived from [SessionExecution] when needed.
 */
@JsExportCompat
interface KvStoreFactory {
    /**
     * Identifier for selecting this factory at runtime (for example "memory" or "kottage").
     */
    val backendId: String

    fun create(
        config: KvStoreConfigBase,
        execution: SessionExecution? = null,
    ): KvStore
}
