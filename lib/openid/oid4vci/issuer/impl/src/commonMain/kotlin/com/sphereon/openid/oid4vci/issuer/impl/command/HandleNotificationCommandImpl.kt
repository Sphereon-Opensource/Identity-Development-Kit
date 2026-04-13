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
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.store.NotificationStateStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Handles credential notification requests per OID4VCI 1.0 Section 11.
 *
 * Flow:
 * 1. Validate notification_id is not blank
 * 2. Check if already processed (idempotent)
 * 3. Record the notification
 * 4. Return Ok(Unit)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleNotificationCommand>())
class HandleNotificationCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val notificationStore: NotificationStateStore,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<HandleNotificationArgs, Unit>(
        commandId = HandleNotificationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleNotificationArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    HandleNotificationCommand {
    override val commandId: String get() = HandleNotificationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleNotificationArgs

    override suspend fun doExecute(
        args: HandleNotificationArgs,
        applyDuring: (HandleNotificationArgs) -> HandleNotificationArgs,
    ): IdkResult<Unit, IdkError> {
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(args, result)
        return result
    }

    private suspend fun emitOutcome(
        args: HandleNotificationArgs,
        result: IdkResult<Unit, IdkError>,
    ) {
        if (!result.isOk) return
        val payload =
            buildJsonObject {
                put("notificationId", args.notification.notificationId)
                put("event", args.notification.event.toString())
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(EventTypes.OID4VCI_NOTIFICATION_RECEIVED)
                .subsystem(EventSubsystems.OID4VCI)
                .category(EventCategories.OPERATION)
                .origin(HandleNotificationCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun doExecuteInternal(
        args: HandleNotificationArgs,
        applyDuring: (HandleNotificationArgs) -> HandleNotificationArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        val notification = applied.notification

        // 0. Validate access token
        asBridge
            .validateAccessToken(
                ValidateAccessTokenArgs(accessToken = applied.accessToken),
            ).getOrElse { return Err(it) }

        // 1. Validate notification_id is not blank — malformed request per OID4VCI spec
        if (notification.notificationId.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid_notification_request"))
        }

        // 2. Check if already processed (idempotent)
        val alreadyProcessed =
            notificationStore
                .isProcessed(notification.notificationId)
                .getOrElse { return Err(it) }
        if (alreadyProcessed) {
            return Ok(Unit)
        }

        // 3. Record the notification.
        // Per OID4VCI spec, if the store rejects the notification ID (e.g. it was never issued
        // by this issuer), we return invalid_notification_id to distinguish it from a malformed
        // request (invalid_notification_request). With the current in-memory store this path
        // is only reachable on underlying KV errors, but custom store implementations may
        // actively reject IDs that were never registered at issuance time.
        val recordResult = notificationStore.recordNotification(notification.notificationId, notification.event)
        if (recordResult.isErr) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid_notification_id"))
        }

        // 4. Return success
        return Ok(Unit)
    }
}
