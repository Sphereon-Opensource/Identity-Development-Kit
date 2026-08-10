/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.api.context

/**
 * App-scoped hook for resources that retain tenant or principal scoped state.
 *
 * Implementations are notified when a user context is explicitly destroyed.
 * Automatic idle eviction only destroys the UserScope graph. AppScope resources
 * can be shared with background and later user contexts, so their tenant lifecycle
 * must not be inferred from the absence of regular user contexts. Implementations
 * should release only derived/runtime resources; persisted data is out of scope for
 * this explicit-destruction hook.
 */
interface ContextScopedResourceInvalidator {
    suspend fun invalidateTenantContext(
        tenantId: String,
        reason: String,
    )

    suspend fun invalidatePrincipalContext(
        tenantId: String,
        principalId: String,
        reason: String,
    )
}
