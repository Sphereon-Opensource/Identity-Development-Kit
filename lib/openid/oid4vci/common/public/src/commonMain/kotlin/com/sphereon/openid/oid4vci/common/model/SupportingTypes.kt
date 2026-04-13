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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * OID4VCI Deferred Credential Request (1.0 Section 9.1, 1.1 Section 10).
 *
 * [transactionId] is the pending issuance reference.
 * [proofs] is optional fresh proof of possession (1.1: may be required by issuer policy).
 * [credentialResponseEncryption] optionally requests encrypted deferred response (1.1).
 */
@Serializable
data class DeferredCredentialRequest(
    @SerialName("transaction_id") val transactionId: String,
    val proofs: CredentialRequestProofs? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
)

/**
 * OID4VCI 1.0 Notification Request (Section 11.1)
 */
@Serializable
data class CredentialNotification(
    @SerialName("notification_id") val notificationId: String,
    val event: CredentialNotificationEvent,
    @SerialName("event_description") val eventDescription: String? = null,
)

/**
 * OID4VCI 1.0 Notification event types (Section 11.1)
 */
@Serializable
enum class CredentialNotificationEvent(
    val value: String,
) {
    @SerialName("credential_accepted")
    CREDENTIAL_ACCEPTED("credential_accepted"),

    @SerialName("credential_failure")
    CREDENTIAL_FAILURE("credential_failure"),

    @SerialName("credential_deleted")
    CREDENTIAL_DELETED("credential_deleted"),
}

/**
 * OID4VCI 1.0 Nonce Endpoint Response (Section 7.2)
 */
@Serializable
data class NonceResponse(
    @SerialName("c_nonce") val cNonce: String,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Int? = null,
)

/**
 * OID4VCI authorization_details type "openid_credential" (Section 5.1.1 / RFC 9396)
 *
 * [credentialConfigurationId] is REQUIRED per spec.
 * [claims] is [JsonElement] to support both spec versions:
 * - OID4VCI 1.0: format-specific claims map (same structure as credential configuration metadata)
 * - OID4VCI 1.1: array of [ClaimsDescriptionObject] (Appendix B.1 path-based structure)
 *
 * Use extension functions [claimsAsDescriptionObjects] (1.1) or [claimsAsMap] (1.0) for typed access.
 */
@Serializable
data class Oid4vciAuthorizationDetail(
    val type: String = "openid_credential",
    @SerialName("credential_configuration_id") val credentialConfigurationId: String,
    val claims: JsonElement? = null,
    val locations: List<String>? = null,
    @SerialName("credential_identifiers") val credentialIdentifiers: List<String>? = null,
)

/**
 * Claims Description Object (OID4VCI 1.1 Appendix B.1)
 *
 * Path-based structure for specifying claims in authorization_details.
 * This is a 1.1-specific structure; 1.0 uses format-specific claim maps instead.
 */
@Serializable
data class ClaimsDescriptionObject(
    val path: List<JsonElement>,
    val values: List<JsonElement>? = null,
)

/**
 * Parse [Oid4vciAuthorizationDetail.claims] as OID4VCI 1.1 Claims Description Objects.
 */
fun Oid4vciAuthorizationDetail.claimsAsDescriptionObjects(): List<ClaimsDescriptionObject>? {
    val element = claims ?: return null
    return try {
        Json.decodeFromJsonElement<List<ClaimsDescriptionObject>>(element)
    } catch (_: Exception) {
        // Ignored: claims element is not a valid 1.1 ClaimsDescriptionObject array
        null
    }
}

/**
 * Parse [Oid4vciAuthorizationDetail.claims] as OID4VCI 1.0 format-specific claim metadata map.
 */
fun Oid4vciAuthorizationDetail.claimsAsMap(): Map<String, ClaimMetadata>? {
    val element = claims ?: return null
    return try {
        Json.decodeFromJsonElement<Map<String, ClaimMetadata>>(element)
    } catch (_: Exception) {
        // Ignored: claims element is not a valid 1.0 claim metadata map
        null
    }
}

/**
 * OID4VCI Error Response (Section 8.3.1, 9.2.1, 11.2)
 */
@Serializable
data class Oid4vciErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Int? = null,
    val interval: Int? = null,
)

/**
 * OID4VCI error codes (Section 8.3.1)
 */
object Oid4vciErrors {
    const val INVALID_REQUEST = "invalid_request"
    const val INVALID_CREDENTIAL_REQUEST = "invalid_credential_request"
    const val UNSUPPORTED_CREDENTIAL_TYPE = "unsupported_credential_type"
    const val UNSUPPORTED_CREDENTIAL_FORMAT = "unsupported_credential_format"
    const val INVALID_PROOF = "invalid_proof"
    const val INVALID_ENCRYPTION_PARAMETERS = "invalid_encryption_parameters"
    const val INVALID_TOKEN = "invalid_token"
    const val CREDENTIAL_REQUEST_DENIED = "credential_request_denied"
    const val ISSUANCE_PENDING = "issuance_pending"
    const val INVALID_TRANSACTION_ID = "invalid_transaction_id"
    const val INVALID_NOTIFICATION_ID = "invalid_notification_id"
    const val INVALID_NOTIFICATION_REQUEST = "invalid_notification_request"

    // OID4VCI 1.1 Section 9.3.1.2 error codes
    const val UNKNOWN_CREDENTIAL_CONFIGURATION = "unknown_credential_configuration"
    const val UNKNOWN_CREDENTIAL_IDENTIFIER = "unknown_credential_identifier"
    const val INVALID_NONCE = "invalid_nonce"
}

/**
 * OID4VCI 1.1 Interaction types for IAE
 */
object Oid4vciInteractionTypes {
    const val OPENID4VP_PRESENTATION = "urn:openid:dcp:iae:openid4vp_presentation"
    const val REDIRECT_TO_WEB = "urn:openid:dcp:iae:redirect_to_web"
}
