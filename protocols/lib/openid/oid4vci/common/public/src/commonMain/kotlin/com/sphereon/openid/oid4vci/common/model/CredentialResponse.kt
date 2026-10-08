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

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vci.common.serializer.CredentialResponseSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OID4VCI 1.0 §8.3 Credential Response Item.
 *
 * Each item in the `credentials` array wraps a single credential.
 */
@JsExportCompat
@Serializable
data class CredentialResponseItem(
    val credential: JsonElement,
)

/**
 * OID4VCI 1.0 §8.3 Credential Response.
 *
 * - [credentials]: REQUIRED for synchronous issuance — array of [CredentialResponseItem], each
 *   containing a `credential` field.
 * - [transactionId]: REQUIRED for deferred issuance.
 * - [interval]: OPTIONAL polling hint when [transactionId] is present.
 * - [notificationId]: OPTIONAL pointer for the holder to call the notification endpoint.
 *
 * Nonces are obtained from the dedicated `/nonce` endpoint (§7.2) — they are no longer
 * carried inline on this response.
 */
@JsExportCompat
@Serializable(with = CredentialResponseSerializer::class)
data class CredentialResponse(
    val credentials: List<CredentialResponseItem>? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("notification_id") val notificationId: String? = null,
    val interval: Int? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)
