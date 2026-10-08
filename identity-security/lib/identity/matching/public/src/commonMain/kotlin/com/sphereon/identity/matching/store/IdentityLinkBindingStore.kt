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

package com.sphereon.identity.matching.store

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.identity.matching.model.IdentityLinkBinding
import kotlin.time.Instant

/**
 * Store for [IdentityLinkBinding] records — the encrypted, reversible identity-link
 * payload with canonical claims and assurance metadata.
 *
 * Provides two lookup keys: by matchId (FK to IdentityMatch) and by holderHash
 * (HMAC of the holder key). Both are point lookups. Expired-binding scan is
 * available for inactivity cleanup.
 */
@JsExportCompat
interface IdentityLinkBindingStore {
    suspend fun create(binding: IdentityLinkBinding): IdentityLinkBinding

    suspend fun findByMatchId(
        tenantId: String,
        matchId: String,
    ): IdentityLinkBinding?

    suspend fun findByHolderHash(
        tenantId: String,
        holderHash: String,
    ): IdentityLinkBinding?

    suspend fun update(binding: IdentityLinkBinding): IdentityLinkBinding

    suspend fun delete(
        tenantId: String,
        bindingId: String,
    ): Boolean

    suspend fun findExpired(
        tenantId: String,
        inactiveSince: Instant,
    ): List<IdentityLinkBinding>
}
