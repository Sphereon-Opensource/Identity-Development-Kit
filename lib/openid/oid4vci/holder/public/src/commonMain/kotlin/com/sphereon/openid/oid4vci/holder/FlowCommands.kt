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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption

// ============================================================================
// PollDeferredCredentialCommand
// ============================================================================

/**
 * Args for polling a deferred credential endpoint with retry logic.
 *
 * @param deferredCredentialEndpoint URL of the deferred credential endpoint.
 * @param accessToken Bearer token for the deferred endpoint.
 * @param transactionId The transaction_id from the initial deferred credential response.
 * @param sessionId Session identifier used to update session state on completion.
 * @param interval Polling interval in seconds. Falls back to [Oid4vciHolderConfig.defaultDeferredPollingInterval].
 * @param maxAttempts Maximum number of poll attempts. Falls back to [Oid4vciHolderConfig.maxDeferredPollingAttempts].
 * @param credentialResponseEncryption Optional encryption params to include in each deferred request.
 * @param decryptionKey Optional key to decrypt JWE credential responses.
 */
data class PollDeferredCredentialArgs(
    val deferredCredentialEndpoint: String,
    val accessToken: String,
    val transactionId: String,
    val sessionId: String,
    val interval: Int? = null,
    val maxAttempts: Int? = null,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    val decryptionKey: ManagedIdentifierOptsOrResult? = null,
)

/**
 * Result of polling the deferred credential endpoint.
 *
 * Both [Ready] and [Exhausted] are valid Ok results — the caller decides how to handle exhaustion.
 * Only a protocol-level error (e.g. credential_request_denied) returns Err.
 */
sealed class PollDeferredCredentialResult {
    /**
     * The credential is now available.
     *
     * @param credential The received credential response.
     * @param pollAttempts Number of poll attempts made before the credential was ready.
     */
    data class Ready(
        val credential: CredentialResponse,
        val pollAttempts: Int,
    ) : PollDeferredCredentialResult()

    /**
     * Polling exhausted the maximum attempt count without receiving a credential.
     *
     * This is a valid Ok result — the caller may choose to retry later, surface a UI
     * message, or treat it as a failure depending on its policy.
     *
     * @param transactionId The last known transaction_id (may have been updated by the server).
     * @param attemptsMade Number of poll attempts made.
     * @param lastInterval Last polling interval in seconds used.
     */
    data class Exhausted(
        val transactionId: String,
        val attemptsMade: Int,
        val lastInterval: Int,
    ) : PollDeferredCredentialResult()
}

interface PollDeferredCredentialCommand : ServiceCommand<PollDeferredCredentialArgs, PollDeferredCredentialResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        // 3-segment module.service.command per the convention; lowercase + hyphens
        // per `feedback_command_ids_lowercase_hyphen`. Was 4-segment
        // `oid4vci.holder.flow.polldeferred` which fails CommandId validation.
        const val COMMAND_ID = "oid4vci.holder.poll-deferred"
    }
}

// ============================================================================
// SendNotificationWithRetryCommand
// ============================================================================

/**
 * Args for sending a credential event notification with exponential backoff retry.
 *
 * @param notificationEndpoint URL of the notification endpoint.
 * @param accessToken Bearer token for the notification endpoint.
 * @param notificationId The notification_id from the credential response.
 * @param event The credential notification event type.
 * @param eventDescription Optional human-readable description of the event.
 * @param maxRetries Maximum number of retry attempts after the initial try (default 3).
 * @param initialBackoffMs Initial backoff delay in milliseconds before retrying (default 1000).
 */
data class SendNotificationWithRetryArgs(
    val notificationEndpoint: String,
    val accessToken: String,
    val notificationId: String,
    val event: CredentialNotificationEvent,
    val eventDescription: String? = null,
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 1000L,
)

interface SendNotificationWithRetryCommand : ServiceCommand<SendNotificationWithRetryArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.notify-retry"
    }
}

// ============================================================================
// RequestCredentialWithFlowCommand
// ============================================================================

/**
 * Args for the full credential issuance flow including proof creation, optional nonce
 * retry, deferred polling, and notification.
 *
 * @param sessionId Session identifier (used to update session state).
 * @param credentialEndpoint URL of the issuer's credential endpoint.
 * @param accessToken Bearer token for the credential endpoint.
 * @param issuerUrl Issuer URL used as JWT `aud` in the proof.
 * @param signingKeyId Managed key identifier used for proof signing.
 * @param signingAlgorithm JWA signing algorithm (default "ES256").
 * @param keyAttestationJwt Optional OID4VCI key-attestation JWT for proof JWT header or attestation proof mode.
 * @param proofType Proof type to create. Defaults to `jwt`; `attestation` sends [keyAttestationJwt] as the proof value.
 * @param credentialConfigurationId Mutually exclusive with [credentialIdentifier].
 * @param credentialIdentifier Mutually exclusive with [credentialConfigurationId].
 * @param nonceEndpoint Optional nonce endpoint for auto-nonce and invalid_nonce retry.
 * @param deferredCredentialEndpoint Optional deferred endpoint. Required for deferred polling.
 * @param notificationEndpoint Optional notification endpoint. When present and credential received, notification is sent.
 * @param credentialResponseEncryption Optional encryption params for the credential response.
 * @param decryptionKey Optional key to decrypt JWE credential responses.
 */
data class RequestCredentialWithFlowArgs(
    val sessionId: String,
    val credentialEndpoint: String,
    val accessToken: String,
    val issuerUrl: String,
    val signingKeyId: String,
    val signingAlgorithm: String = "ES256",
    val keyAttestationJwt: String? = null,
    val proofType: String = "jwt",
    val credentialConfigurationId: String? = null,
    val credentialIdentifier: String? = null,
    val nonceEndpoint: String? = null,
    val deferredCredentialEndpoint: String? = null,
    val notificationEndpoint: String? = null,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    val decryptionKey: ManagedIdentifierOptsOrResult? = null,
)

/**
 * Result of the full credential issuance flow.
 */
sealed class CredentialFlowResult {
    /**
     * Credential was issued immediately.
     *
     * @param credential The received credential response.
     * @param notificationSent Whether a notification was successfully sent to the issuer.
     */
    data class Immediate(
        val credential: CredentialResponse,
        val notificationSent: Boolean,
    ) : CredentialFlowResult()

    /**
     * Credential was deferred and polling completed successfully.
     *
     * @param credential The received credential response.
     * @param pollAttempts Number of poll attempts made.
     * @param notificationSent Whether a notification was successfully sent to the issuer.
     */
    data class DeferredCompleted(
        val credential: CredentialResponse,
        val pollAttempts: Int,
        val notificationSent: Boolean,
    ) : CredentialFlowResult()

    /**
     * Credential was deferred but polling exhausted the maximum attempt count.
     *
     * @param transactionId The last known transaction_id.
     * @param attemptsMade Number of poll attempts made.
     * @param lastInterval Last polling interval in seconds used.
     */
    data class DeferredExhausted(
        val transactionId: String,
        val attemptsMade: Int,
        val lastInterval: Int,
    ) : CredentialFlowResult()
}

interface RequestCredentialWithFlowCommand : ServiceCommand<RequestCredentialWithFlowArgs, CredentialFlowResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.credential-flow"
    }
}
