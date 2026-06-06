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

import com.sphereon.attribute.pipeline.LookupKey
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    /**
     * Controls whether the offer URI is single-use (default) or stays alive across multiple
     * wallet fetches. Stored so the GET handler can apply the correct fetch semantics.
     */
    @SerialName("uri_lifecycle")
    val uriLifecycle: OfferUriLifecycle = OfferUriLifecycle.SINGLE_USE,
    /**
     * Rate-limit for reusable offer URIs. Present when [uriLifecycle] is
     * [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH].
     */
    @SerialName("rate_limit")
    val rateLimit: OfferRateLimit? = null,
    /**
     * Lookup keys seeded at offer-creation time. Stored so the GET handler can forward them
     * to a fresh pipeline session when a new issuance is minted on each fetch.
     */
    @SerialName("initial_lookup_keys")
    @JsExportIgnoreCompat
    val initialLookupKeys: List<LookupKey> = emptyList(),
    /**
     * Replayable offer-creation inputs. Present so the GET handler can rebuild a
     * [com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs] and mint a fresh
     * inner offer on each fetch of a [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH] URI, while the
     * stable offer URI / offer id and this session row stay put. Null for legacy sessions and
     * for sessions created before the template was captured.
     */
    @SerialName("offer_template")
    @JsExportIgnoreCompat
    val offerTemplate: CredentialOfferTemplate? = null,
)

/**
 * Minimal replayable snapshot of the inputs that produced this offer's inner protocol content.
 *
 * Holds exactly the fields a fresh mint needs to rebuild
 * [com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs]. The reusable-URI fields
 * ([OfferUriLifecycle], [OfferRateLimit], lookup keys) already live on [CredentialOfferSession],
 * so they are not duplicated here.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialOfferTemplate", exact = true)
@JsExportCompat
@Serializable
data class CredentialOfferTemplate(
    @SerialName("issuer_id")
    val issuerId: String,
    @SerialName("credential_configuration_ids")
    val credentialConfigurationIds: List<String>,
    @SerialName("pre_authorized_code_grant")
    val preAuthorizedCodeGrant: Boolean = false,
    @SerialName("authorization_code_grant")
    val authorizationCodeGrant: Boolean = false,
    @SerialName("tx_code_required")
    val txCodeRequired: Boolean = false,
    @SerialName("tx_code_length")
    val txCodeLength: Int? = null,
    @SerialName("tx_code_input_mode")
    val txCodeInputMode: String? = null,
    @SerialName("pre_seeded_attributes")
    @JsExportIgnoreCompat
    val preSeededAttributes: Map<String, JsonElement>? = null,
    @SerialName("offer_ttl_seconds")
    val offerTtlSeconds: Long = 600,
    val scheme: String? = null,
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

    /**
     * Looks up a session by its [offerId]. Returns the session when found, or `Ok(null)` when
     * no session with that offer ID exists.
     */
    suspend fun getByOfferId(offerId: String): IdkResult<CredentialOfferSession?, IdkError>

    suspend fun update(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError>

    suspend fun delete(correlationId: String): IdkResult<Boolean, IdkError>
}
