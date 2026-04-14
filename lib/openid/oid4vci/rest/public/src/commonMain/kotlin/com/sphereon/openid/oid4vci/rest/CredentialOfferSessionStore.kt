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

package com.sphereon.openid.oid4vci.rest

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Session entity for the OID4VCI backend REST API.
 *
 * Maps a correlationId to the internal OID4VCI offer/session,
 * storing callback config and session metadata.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialOfferSession", exact = true)
@JsExportCompat
@Serializable
data class CredentialOfferSession(
    @SerialName("correlation_id")
    val correlationId: String,
    @SerialName("offer_id")
    val offerId: String,
    @SerialName("issuance_session_id")
    val issuanceSessionId: String? = null,
    val status: CredentialOfferSessionStatus,
    @SerialName("callback_config")
    val callbackConfig: IssuanceCallbackConfig? = null,
    val state: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("last_updated_at")
    val lastUpdatedAt: Long,
    @SerialName("expires_at")
    val expiresAt: Long? = null,
)

/**
 * Store for OID4VCI backend REST API sessions.
 */
@JsExportCompat
interface CredentialOfferSessionStore {
    companion object {
        const val DEFAULT_TTL_SECONDS: Long = 600
    }

    suspend fun create(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError>

    suspend fun get(correlationId: String): IdkResult<CredentialOfferSession?, IdkError>

    suspend fun update(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError>

    suspend fun delete(correlationId: String): IdkResult<Boolean, IdkError>
}
