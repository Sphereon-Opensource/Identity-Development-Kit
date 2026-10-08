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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent

interface CredentialOfferCacheStore {
    suspend fun cache(
        offerId: String,
        cached: CachedOffer,
    ): IdkResult<Unit, IdkError>

    suspend fun get(offerId: String): IdkResult<CachedOffer?, IdkError>

    suspend fun delete(offerId: String): IdkResult<Boolean, IdkError>
}

interface Oid4vciHolderSessionStore {
    suspend fun create(session: Oid4vciHolderSession): IdkResult<Oid4vciHolderSession, IdkError>

    suspend fun get(sessionId: String): IdkResult<Oid4vciHolderSession?, IdkError>

    suspend fun update(session: Oid4vciHolderSession): IdkResult<Oid4vciHolderSession, IdkError>
}

interface DeferredPollingStore {
    suspend fun schedule(entry: DeferredPollingEntry): IdkResult<Unit, IdkError>

    suspend fun getPending(): IdkResult<List<DeferredPollingEntry>, IdkError>

    suspend fun updateLastPolled(
        transactionId: String,
        timestamp: Long,
    ): IdkResult<Unit, IdkError>

    suspend fun markCompleted(transactionId: String): IdkResult<Unit, IdkError>

    suspend fun markFailed(transactionId: String): IdkResult<Unit, IdkError>
}

interface HolderNotificationStore {
    suspend fun record(
        notificationId: String,
        event: CredentialNotificationEvent,
    ): IdkResult<Unit, IdkError>

    suspend fun isSent(notificationId: String): IdkResult<Boolean, IdkError>
}

interface IssuedCredentialReferenceStore {
    suspend fun store(ref: IssuedCredentialReference): IdkResult<Unit, IdkError>

    suspend fun getByIssuer(issuerUrl: String): IdkResult<List<IssuedCredentialReference>, IdkError>
}
