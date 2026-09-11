/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.bridge.authorizationServerTarget
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.Oid4vciIssuerSessionEventTypes
import com.sphereon.openid.oid4vci.issuer.impl.event.emitOid4vciSessionHistoryEvent
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.NotificationStateStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Handles deferred credential requests per OID4VCI 1.0 Section 9.1 / 1.1 Section 10.
 *
 * Flow:
 * 1. Validate access token via AS bridge
 * 2. Look up deferred entry by transaction ID
 * 3. If READY: deliver credential, mark as DELIVERED
 * 4. If PENDING: enforce `expiresAt`, then hand off to
 *    [DeferredPipelineReExecutor] which runs the DEFERRED pipeline phase, re-checks
 *    completeness, and dispatches the format handler when complete. If re-execution does not
 *    fire (pure-IDK, no pipeline binding, still incomplete, etc.) return the OID4VCI 1.1 §10.2
 *    transactionId+interval response so the HTTP adapter answers 202
 * 5. If FAILED / EXPIRED / DELIVERED: return invalid_transaction_id error
 * 6. Apply encryption if requested
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleDeferredCredentialRequestCommand>())
class HandleDeferredCredentialRequestCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val deferredStore: DeferredCredentialStore,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val notificationStore: NotificationStateStore,
    private val clock: Clock,
    private val eventService: SessionEventService? = null,
    /**
     * Factory for the optional [DeferredPipelineReExecutor] that drives the PENDING-branch
     * pipeline re-execution. Returns `null` in pure-IDK deployments that did not wire the
     * EDK pipeline commands; the PENDING branch then falls through to the OID4VCI 1.1 §10.2
     * `transactionId+interval` response.
     */
    private val reExecutorFactory: DeferredPipelineReExecutorFactory,
) : TypedServiceCommandAdapter<HandleDeferredCredentialRequestArgs, CredentialResponse, IdkError>(
        commandId = HandleDeferredCredentialRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleDeferredCredentialRequestArgs>(),
        outputTypeToken = typeToken<CredentialResponse>(),
    ),
    HandleDeferredCredentialRequestCommand {
    override val commandId: String get() = HandleDeferredCredentialRequestCommand.COMMAND_ID

    /**
     * Lazily built so pure-IDK deployments (no pipeline commands wired in) keep the executor
     * null and the PENDING branch falls through to the 202 transactionId+interval response.
     */
    private val reExecutor: DeferredPipelineReExecutor? =
        reExecutorFactory.create(
            tenantIdProvider = {
                runCatching { execution.sessionContext.context.tenant.tenantId }.getOrNull()
            },
        )
    private var pendingHistorySession: IssuanceSession? = null
    private var pendingHistoryProtocolSessionId: String? = null
    private var pendingHistoryInstanceId: String? = null
    private var pendingDeferredExpiresAt: Long? = null
    private var pendingDeferredStatus: DeferredCredentialStatus? = null
    private var pendingCredentialConfigurationId: String? = null
    private var pendingRetryAfterSeconds: Int? = null

    override suspend fun supports(args: Any): Boolean = args is HandleDeferredCredentialRequestArgs

    override suspend fun doExecute(
        args: HandleDeferredCredentialRequestArgs,
        applyDuring: (HandleDeferredCredentialRequestArgs) -> HandleDeferredCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        pendingHistorySession = null
        pendingHistoryProtocolSessionId = null
        pendingHistoryInstanceId = null
        pendingDeferredExpiresAt = null
        pendingDeferredStatus = null
        pendingCredentialConfigurationId = null
        pendingRetryAfterSeconds = null
        var result = doExecuteInternal(args, applyDuring)
        if (result.isOk) {
            val response = result.value
            val notificationId = response.notificationId
            val protocolSessionId = pendingHistoryProtocolSessionId
            if (notificationId != null && protocolSessionId != null) {
                val instanceId = pendingHistoryInstanceId
                    ?: return Err(IdkError.INVALID_STATE(message = "instanceId must be resolved before notification registration"))
                val expiresAt = pendingHistorySession?.expiresAt ?: pendingDeferredExpiresAt ?: clock.now().toEpochMilliseconds() + 86_400_000L
                val remainingMillis = expiresAt - clock.now().toEpochMilliseconds()
                val ttlSeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(1L)
                result =
                    notificationStore
                        .registerNotification(notificationId, protocolSessionId, instanceId, ttlSeconds)
                        .map { response }
            }
        }
        emitOutcome(result)
        pendingHistorySession = null
        pendingHistoryProtocolSessionId = null
        pendingHistoryInstanceId = null
        pendingDeferredExpiresAt = null
        pendingDeferredStatus = null
        pendingCredentialConfigurationId = null
        pendingRetryAfterSeconds = null
        return result
    }

    private suspend fun emitOutcome(
        result: IdkResult<CredentialResponse, IdkError>,
    ) {
        val es = eventService ?: return
        val session = pendingHistorySession
        val protocolSessionId =
            pendingHistoryProtocolSessionId
                ?: run {
                    check(!result.isOk) { "Successful deferred credential request has no protocolSessionId for history" }
                    return
                }
        val instanceId =
            pendingHistoryInstanceId
                ?: run {
                    check(!result.isOk) { "Successful deferred credential request has no instanceId for history" }
                    return
                }
        val credentialIssued = result.isOk && result.value.credentials != null
        val type =
            when {
                credentialIssued -> EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED

                result.isOk -> Oid4vciIssuerSessionEventTypes.DEFERRED_RETRY

                else -> Oid4vciIssuerSessionEventTypes.DEFERRED_FAILED
            }
        es.emitOid4vciSessionHistoryEvent(
            type = type,
            origin = HandleDeferredCredentialRequestCommand.COMMAND_ID,
            instanceId = instanceId,
            protocolSessionId = protocolSessionId,
            correlationId = session?.lifecycleCorrelationId,
            oldState = pendingDeferredStatus?.name,
            newState = when {
                credentialIssued -> DeferredCredentialStatus.DELIVERED.name
                result.isOk -> DeferredCredentialStatus.PENDING.name
                else -> DeferredCredentialStatus.FAILED.name
            },
            stage = "DEFERRED_CREDENTIAL",
            outcome = when {
                credentialIssued -> "RETURNED"
                result.isOk -> "RETRY"
                else -> "FAILED"
            },
            credentialConfigurationId = pendingCredentialConfigurationId,
            retryAfterSeconds = pendingRetryAfterSeconds,
        )
    }

    private suspend fun doExecuteInternal(
        args: HandleDeferredCredentialRequestArgs,
        applyDuring: (HandleDeferredCredentialRequestArgs) -> HandleDeferredCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val applied = applyDuring(args)
        val deferredRequest = applied.deferredRequest

        // Resolve only server-owned deferred/session state before authentication, then verify the
        // token against that session's immutable AS target.
        val transactionId = deferredRequest.transactionId
        val entry = deferredStore.get(transactionId).getOrElse { return Err(it) }
            ?: return invalidTransactionId()
        val protocolSessionId = entry.issuanceSessionId.takeIf(String::isNotBlank) ?: return invalidTransactionId()
        val pinnedSession = sessionStore.get(protocolSessionId).getOrElse { return Err(it) }
            ?: return Err(IdkError.INVALID_STATE(message = "Deferred credential has no issuance session"))
        val snapshot = pinnedSession.authorizationPolicySnapshot
            ?: return Err(IdkError.INVALID_STATE(message = "Deferred credential session has no immutable authorization-server snapshot"))

        // 1. Validate access token
        val tokenContext =
            asBridge
                .validateAccessToken(
                    ValidateAccessTokenArgs(
                        authorizationServer = snapshot.authorizationServerTarget(),
                        expectedAudience = pinnedSession.issuerId,
                        accessToken = applied.accessToken,
                        dpopProof = applied.dpopProof,
                        httpUrl = applied.httpUrl,
                        httpMethod = applied.httpMethod,
                    ),
                ).getOrElse { return Err(it) }
        validateAuthorizationServerSnapshot(snapshot, tokenContext).getOrElse { return Err(it) }

        // 2. Look up deferred entry
        pendingDeferredStatus = entry.status
        pendingCredentialConfigurationId = entry.credentialConfigurationId
        pendingRetryAfterSeconds = entry.retryAfterSeconds
        pendingDeferredExpiresAt = entry.expiresAt
        pendingHistoryProtocolSessionId = protocolSessionId
        pendingHistorySession = pinnedSession
        val storedInstanceId = pendingHistorySession?.instanceId ?: entry.instanceId
        if (pendingHistorySession != null && pendingHistorySession?.instanceId != entry.instanceId) {
            return Err(IdkError.INVALID_STATE(message = "Deferred credential identity does not match its issuance session"))
        }
        pendingHistoryInstanceId = storedInstanceId

        // 3. Handle based on status
        return when (entry.status) {
            DeferredCredentialStatus.READY -> deliverReady(entry)

            DeferredCredentialStatus.PENDING -> handlePending(entry, tokenContext)

            DeferredCredentialStatus.FAILED,
            DeferredCredentialStatus.EXPIRED,
            DeferredCredentialStatus.DELIVERED,
            -> invalidTransactionId()
        }
    }

    private fun invalidTransactionId(): IdkResult<CredentialResponse, IdkError> = Err(IdkError.NOT_FOUND_ERROR(message = INVALID_TRANSACTION_ID))

    /**
     * Mark a READY entry as DELIVERED and return the stored credential response per
     * OID4VCI 1.0 §8.3.
     */
    private suspend fun deliverReady(entry: DeferredCredentialEntry,): IdkResult<CredentialResponse, IdkError> {
        deferredStore
            .update(entry.copy(status = DeferredCredentialStatus.DELIVERED))
            .getOrElse { return Err(it) }

        val batchCredentials = entry.credentialResponses
        val items =
            if (!batchCredentials.isNullOrEmpty()) {
                batchCredentials.map { CredentialResponseItem(credential = it) }
            } else {
                val credentialJson =
                    entry.credentialResponse
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Deferred entry READY but no credential stored"))
                listOf(CredentialResponseItem(credential = credentialJson))
            }
        return Ok(
            CredentialResponse(
                credentials = items,
                notificationId = entry.notificationId,
            ),
        )
    }

    /**
     * PENDING branch: enforce expiry, then attempt a pipeline re-execution, and
     * finally fall back to the OID4VCI 1.1 §10.2 transactionId+interval response so the HTTP
     * adapter answers 202.
     */
    private suspend fun handlePending(
        entry: DeferredCredentialEntry,
        tokenContext: ValidatedTokenContext,
    ): IdkResult<CredentialResponse, IdkError> {
        val expiredResult = enforceExpiry(entry)
        if (expiredResult != null) return expiredResult

        val reExecResult = reExecutor?.attempt(entry, tokenContext)
        if (reExecResult != null) return reExecResult

        return Ok(
            CredentialResponse(
                transactionId = entry.transactionId,
                interval = entry.retryAfterSeconds,
            ),
        )
    }

    /**
     * Returns `Err(invalid_transaction_id)` and flips the entry to EXPIRED when the entry has
     * passed its `expiresAt`. Returns `null` otherwise so the caller continues with re-execution.
     *
     * OID4VCI 1.0 §10.2 lists `invalid_transaction_id` as the error for "the transaction does
     * not exist, has expired, or is unknown": same code the FAILED / EXPIRED / DELIVERED branch
     * uses, surfaced consistently here when we discover expiry during a PENDING poll.
     */
    private suspend fun enforceExpiry(entry: DeferredCredentialEntry,): IdkResult<CredentialResponse, IdkError>? {
        if (clock.now().toEpochMilliseconds() < entry.expiresAt) return null
        deferredStore
            .update(entry.copy(status = DeferredCredentialStatus.EXPIRED))
            .getOrElse { return Err(it) }
        return invalidTransactionId()
    }

    private companion object {
        const val INVALID_TRANSACTION_ID = "invalid_transaction_id"
    }
}
