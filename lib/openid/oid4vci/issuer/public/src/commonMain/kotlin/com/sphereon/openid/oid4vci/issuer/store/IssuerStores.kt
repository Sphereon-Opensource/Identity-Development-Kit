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

package com.sphereon.openid.oid4vci.issuer.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import kotlinx.serialization.Serializable

/**
 * Store for credential offers with TTL support.
 */
interface CredentialOfferStore {
    suspend fun store(
        offerId: String,
        offer: CredentialOffer,
        ttlSeconds: Long,
        sessionId: String? = null,
    ): IdkResult<Unit, IdkError>

    suspend fun get(offerId: String): IdkResult<CredentialOffer?, IdkError>

    suspend fun getSessionId(offerId: String): IdkResult<String?, IdkError>

    suspend fun delete(offerId: String): IdkResult<Boolean, IdkError>
}

/**
 * Store for credential nonces (single-use).
 *
 * Nonces are issuer-owned protocol state, not routed through the AS bridge.
 */
interface CredentialNonceStore {
    suspend fun create(
        nonce: String,
        ttlSeconds: Long,
    ): IdkResult<NonceEntry, IdkError>

    suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError>
}

@Serializable
data class NonceEntry(
    val nonce: String,
    val createdAt: Long,
    val expiresAt: Long,
)

/**
 * Store for issuance sessions.
 */
interface CredentialIssuanceSessionStore {
    suspend fun create(session: IssuanceSession): IdkResult<IssuanceSession, IdkError>

    suspend fun get(sessionId: String): IdkResult<IssuanceSession?, IdkError>

    suspend fun getByIssuerState(state: String): IdkResult<IssuanceSession?, IdkError>

    suspend fun findByCredentialConfigurationId(configId: String): IdkResult<IssuanceSession?, IdkError>

    suspend fun update(session: IssuanceSession): IdkResult<IssuanceSession, IdkError>
}

/**
 * Store for deferred credential entries, keyed by transaction ID.
 */
interface DeferredCredentialStore {
    suspend fun create(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError>

    suspend fun get(transactionId: String): IdkResult<DeferredCredentialEntry?, IdkError>

    suspend fun update(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError>
}

/**
 * Store for credential notification state, providing idempotent notification processing.
 */
interface NotificationStateStore {
    suspend fun recordNotification(
        notificationId: String,
        event: CredentialNotificationEvent,
    ): IdkResult<Unit, IdkError>

    suspend fun isProcessed(notificationId: String): IdkResult<Boolean, IdkError>
}
