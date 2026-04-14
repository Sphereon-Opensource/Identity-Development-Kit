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
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch

@JsExportCompat
interface IdentityMatchStore {
    suspend fun findByIdentifierHash(
        tenantId: String,
        identifierHash: String,
        identifierType: IdentifierType,
    ): IdentityMatch?

    suspend fun findById(
        tenantId: String,
        matchId: String,
    ): IdentityMatch?

    suspend fun findByInternalIdentityId(
        tenantId: String,
        internalIdentityId: String,
    ): List<IdentityMatch>

    suspend fun create(match: IdentityMatch): IdentityMatch

    suspend fun update(match: IdentityMatch): IdentityMatch

    suspend fun delete(
        tenantId: String,
        matchId: String,
    ): Boolean
}
