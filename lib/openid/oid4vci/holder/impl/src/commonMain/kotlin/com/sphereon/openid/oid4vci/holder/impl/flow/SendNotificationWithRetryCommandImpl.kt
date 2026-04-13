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
import com.sphereon.openid.oid4vci.holder.HolderNotificationStore
import com.sphereon.openid.oid4vci.holder.SendNotificationArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.delay

/**
 * Sends a credential event notification with idempotency checking and exponential backoff retry.
 *
 * Behaviour:
 * 1. Idempotency: if [HolderNotificationStore.isSent] returns true the notification is skipped
 *    and Ok(Unit) is returned immediately — the notification was already delivered.
 * 2. Loop: up to [SendNotificationWithRetryArgs.maxRetries] additional attempts after the initial try.
 * 3. Backoff: before each retry (attempt > 0) the command delays for the current backoff duration,
 *    then doubles it for the next retry (exponential backoff).
 * 4. On success: the notification is recorded in [HolderNotificationStore] and Ok(Unit) is returned.
 * 5. When all attempts are exhausted: Err with the last error is returned.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SendNotificationWithRetryCommand>())
class SendNotificationWithRetryCommandImpl(
    execution: SessionExecution,
    private val sendNotificationCommand: SendNotificationCommand,
    private val notificationStore: HolderNotificationStore,
) : TypedServiceCommandAdapter<SendNotificationWithRetryArgs, Unit>(
        commandId = SendNotificationWithRetryCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SendNotificationWithRetryArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    SendNotificationWithRetryCommand {
    override val commandId: String get() = SendNotificationWithRetryCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SendNotificationWithRetryArgs

    override suspend fun doExecute(
        args: SendNotificationWithRetryArgs,
        applyDuring: (SendNotificationWithRetryArgs) -> SendNotificationWithRetryArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)

        // Idempotency check — skip if already sent
        val isSentResult = notificationStore.isSent(applied.notificationId)
        if (isSentResult.isOk && isSentResult.value == true) {
            log.debug("Notification ${applied.notificationId} already sent, skipping")
            return Ok(Unit)
        }

        log.debug(
            "Sending notification '${applied.event}' to ${applied.notificationEndpoint} " +
                "(maxRetries=${applied.maxRetries}, initialBackoffMs=${applied.initialBackoffMs})",
        )

        var lastError: IdkError? = null
        var backoffMs = applied.initialBackoffMs

        // attempt 0 = initial try; attempts 1..maxRetries = retries
        for (attempt in 0..applied.maxRetries) {
            if (attempt > 0) {
                log.debug("Retry attempt $attempt/${applied.maxRetries} for notification ${applied.notificationId}, backoff ${backoffMs}ms")
                delay(backoffMs)
                backoffMs *= 2
            }

            val result =
                sendNotificationCommand.execute(
                    SendNotificationArgs(
                        notificationEndpoint = applied.notificationEndpoint,
                        accessToken = applied.accessToken,
                        notificationId = applied.notificationId,
                        event = applied.event,
                        eventDescription = applied.eventDescription,
                    ),
                )

            if (result.isOk) {
                val recordResult = notificationStore.record(applied.notificationId, applied.event)
                if (recordResult.isErr) {
                    log.warn("Failed to record notification ${applied.notificationId} in store: ${recordResult.error?.message?.defaultMessage}")
                }
                log.debug("Notification ${applied.notificationId} sent successfully on attempt ${attempt + 1}")
                return Ok(Unit)
            }

            lastError = result.error
            log.warn("Notification attempt ${attempt + 1} failed: ${lastError?.message?.defaultMessage}")
        }

        log.warn("All notification attempts exhausted for ${applied.notificationId}: ${lastError?.message?.defaultMessage}")
        return Err(
            lastError ?: IdkError.fromString(
                message = "All notification retry attempts exhausted",
                code = "NOTIFICATION_RETRIES_EXHAUSTED",
            ),
        )
    }
}
