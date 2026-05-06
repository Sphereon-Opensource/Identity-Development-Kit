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

package com.sphereon.openid.oid4vci.holder.impl.flow

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.CredentialFlowResult
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderConfig
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSession
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStatus
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialResult
import com.sphereon.openid.oid4vci.holder.RequestCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowCommand
import com.sphereon.openid.oid4vci.holder.RequestNonceArgs
import com.sphereon.openid.oid4vci.holder.RequestNonceCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Orchestrates the complete OID4VCI credential issuance flow in a single command.
 *
 * Steps:
 * 1. Auto-nonce: if [Oid4vciHolderConfig.autoRequestNonce] is true and a nonce endpoint is provided,
 *    fetch a fresh c_nonce before building the proof.
 * 2. Create proof: call [CreateCredentialRequestProofCommand] with the c_nonce.
 * 3. Update session to CREDENTIAL_REQUESTED.
 * 4. Request credential: call [RequestCredentialCommand].
 * 5. Nonce retry: if the issuer returns `INVALID_NONCE_FRESH_NONCE_AVAILABLE:<nonce>`, extract the
 *    fresh nonce, rebuild the proof, and retry the credential request exactly once.
 * 6. Deferred branch (transactionId != null && no credentials):
 *    a. Update session to DEFERRED_PENDING.
 *    b. Call [PollDeferredCredentialCommand].
 *    c. [PollDeferredCredentialResult.Ready] → session CREDENTIAL_RECEIVED, send notification,
 *       return [CredentialFlowResult.DeferredCompleted].
 *    d. [PollDeferredCredentialResult.Exhausted] → return [CredentialFlowResult.DeferredExhausted].
 * 7. Immediate branch: session CREDENTIAL_RECEIVED, send notification,
 *    return [CredentialFlowResult.Immediate].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestCredentialWithFlowCommand>())
class RequestCredentialWithFlowCommandImpl(
    execution: SessionExecution,
    private val requestNonceCommand: RequestNonceCommand,
    private val createCredentialRequestProofCommand: CreateCredentialRequestProofCommand,
    private val requestCredentialCommand: RequestCredentialCommand,
    private val pollDeferredCredentialCommand: PollDeferredCredentialCommand,
    private val sendNotificationWithRetryCommand: SendNotificationWithRetryCommand,
    private val sessionStore: Oid4vciHolderSessionStore,
    private val config: Oid4vciHolderConfig,
) : TypedServiceCommandAdapter<RequestCredentialWithFlowArgs, CredentialFlowResult, IdkError>(
        commandId = RequestCredentialWithFlowCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RequestCredentialWithFlowArgs>(),
        outputTypeToken = typeToken<CredentialFlowResult>(),
    ),
    RequestCredentialWithFlowCommand {
    override val commandId: String get() = RequestCredentialWithFlowCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RequestCredentialWithFlowArgs

    override suspend fun doExecute(
        args: RequestCredentialWithFlowArgs,
        applyDuring: (RequestCredentialWithFlowArgs) -> RequestCredentialWithFlowArgs,
    ): IdkResult<CredentialFlowResult, IdkError> {
        val applied = applyDuring(args)

        // Step 1: Auto-nonce
        var cNonce: String? = null
        val nonceEndpoint = applied.nonceEndpoint
        if (config.autoRequestNonce && nonceEndpoint != null) {
            log.debug("Auto-requesting nonce from $nonceEndpoint")
            val nonceResult = requestNonceCommand.execute(RequestNonceArgs(nonceEndpoint = nonceEndpoint))
            if (nonceResult.isOk) {
                cNonce = nonceResult.value?.cNonce
                log.debug("Received c_nonce from nonce endpoint")
            } else {
                log.warn("Failed to fetch nonce from ${applied.nonceEndpoint}: ${nonceResult.error?.message?.defaultMessage}")
            }
        }

        // Step 2: Create proof
        val proof = createProof(applied, cNonce).getOrElse { return Err(it) }

        // Step 3: Update session to CREDENTIAL_REQUESTED
        updateSessionStatus(applied.sessionId, Oid4vciHolderSessionStatus.CREDENTIAL_REQUESTED)

        // Step 4: Request credential
        var credentialResult = requestCredential(applied, proof)

        // Step 5: Nonce retry on INVALID_NONCE_FRESH_NONCE_AVAILABLE
        if (credentialResult.isErr) {
            val error = credentialResult.error!!
            val freshNonce = extractFreshNonceFromError(error)
            if (freshNonce != null) {
                log.debug("Retrying credential request with fresh nonce")
                val retryProof = createProof(applied, freshNonce).getOrElse { return Err(it) }
                credentialResult = requestCredential(applied, retryProof)
            }
        }

        if (credentialResult.isErr) {
            return Err(credentialResult.error!!)
        }

        val response = credentialResult.value!!

        // Step 6: Deferred branch — OID4VCI 1.0 §10.2: a deferred response carries
        // `transaction_id` (and optional `interval`) without `credentials`.
        val isDeferred =
            response.transactionId != null &&
                response.credentials.isNullOrEmpty()

        if (isDeferred) {
            val deferredEndpoint = applied.deferredCredentialEndpoint
            if (deferredEndpoint == null) {
                return Err(
                    IdkError.fromString(
                        message =
                            "Issuer returned a deferred response (transaction_id=${response.transactionId}) " +
                                "but no deferredCredentialEndpoint was provided in args.",
                        code = "DEFERRED_ENDPOINT_MISSING",
                    ),
                )
            }

            updateSessionStatus(applied.sessionId, Oid4vciHolderSessionStatus.DEFERRED_PENDING)
            log.debug("Credential is deferred (transactionId=${response.transactionId}), starting polling")

            val pollResult =
                pollDeferredCredentialCommand
                    .execute(
                        PollDeferredCredentialArgs(
                            deferredCredentialEndpoint = deferredEndpoint,
                            accessToken = applied.accessToken,
                            transactionId = response.transactionId!!,
                            sessionId = applied.sessionId,
                            credentialResponseEncryption = applied.credentialResponseEncryption,
                            decryptionKey = applied.decryptionKey,
                        ),
                    ).getOrElse { return Err(it) }

            return when (pollResult) {
                is PollDeferredCredentialResult.Ready -> {
                    updateSessionStatus(applied.sessionId, Oid4vciHolderSessionStatus.CREDENTIAL_RECEIVED)
                    val notificationSent = maybeSendNotification(applied, pollResult.credential)
                    Ok(
                        CredentialFlowResult.DeferredCompleted(
                            credential = pollResult.credential,
                            pollAttempts = pollResult.pollAttempts,
                            notificationSent = notificationSent,
                        ),
                    )
                }

                is PollDeferredCredentialResult.Exhausted -> {
                    Ok(
                        CredentialFlowResult.DeferredExhausted(
                            transactionId = pollResult.transactionId,
                            attemptsMade = pollResult.attemptsMade,
                            lastInterval = pollResult.lastInterval,
                        ),
                    )
                }
            }
        }

        // Step 7: Immediate issuance
        updateSessionStatus(applied.sessionId, Oid4vciHolderSessionStatus.CREDENTIAL_RECEIVED)
        val notificationSent = maybeSendNotification(applied, response)
        return Ok(
            CredentialFlowResult.Immediate(
                credential = response,
                notificationSent = notificationSent,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun createProof(
        args: RequestCredentialWithFlowArgs,
        cNonce: String?,
    ): IdkResult<com.sphereon.openid.oid4vci.holder.CreatedProof, IdkError> =
        createCredentialRequestProofCommand.execute(
            CreateCredentialRequestProofArgs(
                issuerUrl = args.issuerUrl,
                cNonce = cNonce,
                signingKeyId = args.signingKeyId,
                signingAlgorithm = args.signingAlgorithm,
                clientId = config.clientId,
            ),
        )

    private suspend fun requestCredential(
        args: RequestCredentialWithFlowArgs,
        proof: CreatedProof,
    ): IdkResult<CredentialResponse, IdkError> =
        requestCredentialCommand.execute(
            RequestCredentialArgs(
                credentialEndpoint = args.credentialEndpoint,
                accessToken = args.accessToken,
                credentialConfigurationId = args.credentialConfigurationId,
                credentialIdentifier = args.credentialIdentifier,
                proofs = proof.proofs,
                credentialResponseEncryption = args.credentialResponseEncryption,
                nonceEndpoint = args.nonceEndpoint,
                decryptionKey = args.decryptionKey,
            ),
        )

    /**
     * Extracts a fresh c_nonce from an INVALID_NONCE_FRESH_NONCE_AVAILABLE error code.
     *
     * The RequestCredentialCommandImpl embeds the fresh nonce in the error code as:
     * `INVALID_NONCE_FRESH_NONCE_AVAILABLE:<nonce>`
     */
    private fun extractFreshNonceFromError(error: IdkError): String? {
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        val code = error.code ?: return null
        if (!code.startsWith(prefix)) {
            return null
        }
        val nonce = code.removePrefix(prefix).trim()
        return nonce.ifBlank { null }
    }

    private suspend fun updateSessionStatus(
        sessionId: String,
        status: Oid4vciHolderSessionStatus,
    ) {
        val session = sessionStore.get(sessionId).getOrElse { return } ?: return
        val result = sessionStore.update(session.copy(status = status))
        if (result.isErr) {
            log.warn("Failed to persist session status $status for $sessionId: ${result.error?.message?.defaultMessage}")
        } else {
            log.debug("Session $sessionId → $status")
        }
    }

    /**
     * Sends a credential received notification if a notification endpoint and notification ID
     * are available. Returns true if the notification was sent (or attempted and recorded).
     */
    private suspend fun maybeSendNotification(
        args: RequestCredentialWithFlowArgs,
        credential: CredentialResponse,
    ): Boolean {
        val notificationEndpoint = args.notificationEndpoint ?: return false
        val notificationId = credential.notificationId ?: return false

        log.debug("Sending credential_accepted notification for notificationId=$notificationId")

        val result =
            sendNotificationWithRetryCommand.execute(
                SendNotificationWithRetryArgs(
                    notificationEndpoint = notificationEndpoint,
                    accessToken = args.accessToken,
                    notificationId = notificationId,
                    event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                ),
            )

        if (result.isErr) {
            log.warn("Notification failed (non-fatal): ${result.error?.message?.defaultMessage}")
        }
        return result.isOk
    }
}
