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
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.impl.event.emitOid4vciSessionHistoryEvent
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuanceLifecycleHook
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuancePhase
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.NotificationStateStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Handles credential notification requests per OID4VCI 1.0 Section 11.
 *
 * Flow:
 * 1. Validate notification_id is not blank
 * 2. Atomically record the receipt and resolve its exact protocol session
 * 3. Run offer-session lifecycle integration when that session exists
 * 4. Return Ok(Unit)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleNotificationCommand>())
class HandleNotificationCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val notificationStore: NotificationStateStore,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val lifecycleHook: Oid4vciIssuanceLifecycleHook? = null,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<HandleNotificationArgs, Unit, IdkError>(
        commandId = HandleNotificationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleNotificationArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    HandleNotificationCommand {
    override val commandId: String get() = HandleNotificationCommand.COMMAND_ID
    private var pendingHistorySession: IssuanceSession? = null
    private var pendingHistoryProtocolSessionId: String? = null
    private var pendingHistoryInstanceId: String? = null
    private var pendingNotificationEvent: String? = null

    override suspend fun supports(args: Any): Boolean = args is HandleNotificationArgs

    override suspend fun doExecute(
        args: HandleNotificationArgs,
        applyDuring: (HandleNotificationArgs) -> HandleNotificationArgs,
    ): IdkResult<Unit, IdkError> {
        pendingHistorySession = null
        pendingHistoryProtocolSessionId = null
        pendingHistoryInstanceId = null
        pendingNotificationEvent = null
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(result)
        pendingHistorySession = null
        pendingHistoryProtocolSessionId = null
        pendingHistoryInstanceId = null
        pendingNotificationEvent = null
        return result
    }

    private suspend fun emitOutcome(
        result: IdkResult<Unit, IdkError>,
    ) {
        val es = eventService ?: return
        val session = pendingHistorySession
        val protocolSessionId =
            pendingHistoryProtocolSessionId
                ?: run {
                    check(!result.isOk) { "Successful notification has no protocolSessionId for history" }
                    return
                }
        val instanceId =
            pendingHistoryInstanceId
                ?: run {
                    check(!result.isOk) { "Successful notification has no instanceId for history" }
                    return
                }
        es.emitOid4vciSessionHistoryEvent(
            type = EventTypes.OID4VCI_NOTIFICATION_RECEIVED,
            origin = HandleNotificationCommand.COMMAND_ID,
            instanceId = instanceId,
            protocolSessionId = protocolSessionId,
            correlationId = session?.lifecycleCorrelationId,
            oldState = session?.status?.name ?: "CREDENTIAL_ISSUED",
            newState = session?.status?.name ?: "CREDENTIAL_ISSUED",
            stage = "NOTIFICATION",
            outcome = if (result.isOk) "RECEIVED" else "FAILED",
            notificationEvent = pendingNotificationEvent,
        )
    }

    private suspend fun doExecuteInternal(
        args: HandleNotificationArgs,
        applyDuring: (HandleNotificationArgs) -> HandleNotificationArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        val notification = applied.notification

        // 0. Validate access token
        val tokenContext =
            asBridge
                .validateAccessToken(
                    ValidateAccessTokenArgs(
                        accessToken = applied.accessToken,
                        dpopProof = applied.dpopProof,
                        httpUrl = applied.httpUrl,
                        httpMethod = applied.httpMethod,
                    ),
                ).getOrElse { return Err(it) }

        // 1. Validate notification_id is not blank — malformed request per OID4VCI spec
        if (notification.notificationId.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid_notification_request"))
        }
        pendingNotificationEvent = notification.event.value

        // 2. Resolve and consume the issuer-generated notification identifier atomically. The
        // returned protocolSessionId is the only valid history correlation; configuration IDs
        // and subjects are deliberately not consulted.
        val identity =
            notificationStore
                .getNotificationIdentity(notification.notificationId)
                .getOrElse { return Err(it) }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid_notification_id"))
        val session = sessionStore.get(identity.protocolSessionId).getOrElse { return Err(it) }
        if (session != null && session.instanceId != identity.instanceId) {
            return Err(IdkError.INVALID_STATE(message = "Notification identity does not match its issuance session"))
        }

        val receipt =
            notificationStore
                .recordNotification(notification.notificationId, notification.event)
                .getOrElse { return Err(it) }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid_notification_id"))
        if (!receipt.firstReceipt) return Ok(Unit)

        pendingHistoryProtocolSessionId = receipt.protocolSessionId
        pendingHistoryInstanceId = receipt.instanceId
        // History is already exactly correlated by notification_id. A wallet-initiated protocol
        // session intentionally has no IssuanceSession row, and a transient optional lookup failure
        // must not discard the atomically accepted receipt or its durable history event.
        pendingHistorySession = session

        contributeNotificationReceiptPhase(tokenContext, notification, pendingHistorySession).getOrElse { return Err(it) }

        // 4. Return success
        return Ok(Unit)
    }

    private suspend fun contributeNotificationReceiptPhase(
        tokenContext: ValidatedTokenContext,
        notification: CredentialNotification,
        session: IssuanceSession?,
    ): IdkResult<Unit, IdkError> {
        val hook = lifecycleHook ?: return Ok(Unit)
        session ?: return Ok(Unit)
        val correlationId = session.lifecycleCorrelationId ?: return Ok(Unit)
        hook
            .recordPhase(
                Oid4vciPhaseLifecycleArgs(
                    correlationId = correlationId,
                    protocolSessionId = session.sessionId,
                    phase = Oid4vciIssuancePhase.NOTIFICATION_RECEIPT,
                    fields = notificationReceiptFields(tokenContext, notification),
                ),
            ).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    private fun notificationReceiptFields(
        tokenContext: ValidatedTokenContext,
        notification: CredentialNotification,
    ): Map<String, JsonElement> =
        buildMap {
            put("oid4vci.notificationId", JsonPrimitive(notification.notificationId))
            put("oid4vci.notificationEvent", JsonPrimitive(notification.event.value))
            notification.eventDescription?.let { put("oid4vci.notificationEventDescription", JsonPrimitive(it)) }
            put("oid4vci.subject", JsonPrimitive(tokenContext.subject))
            put("oid4vci.clientId", JsonPrimitive(tokenContext.clientId))
            put(
                "oid4vci.authorizedCredentialConfigurationIds",
                buildJsonArray { tokenContext.credentialConfigurationIds.forEach { add(it) } },
            )
        }
}
