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
import com.sphereon.openid.oid4vci.holder.DeferredPollingEntry
import com.sphereon.openid.oid4vci.holder.DeferredPollingStore
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderConfig
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialResult
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Polls the deferred credential endpoint in a loop until the credential is ready,
 * the server returns a terminal error, or the maximum attempt count is exhausted.
 *
 * Per OID4VCI 1.1 Section 10.1:
 * - The server MAY return a new transaction_id on each poll response.
 * - The server MAY return an updated interval on each poll response.
 * - HTTP 200 with credentials present → credential ready.
 * - HTTP 202 without credentials      → still pending, continue polling.
 * - HTTP 4xx with credential_request_denied → terminal error, stop polling.
 *
 * Interval and maxAttempts fall back to [Oid4vciHolderConfig] defaults when not specified in args.
 *
 * Exhaustion is returned as [Ok(Exhausted)] — it is a valid result, not a protocol error.
 * The caller decides what to do when the maximum attempt count is reached.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<PollDeferredCredentialCommand>())
class PollDeferredCredentialCommandImpl(
    execution: SessionExecution,
    private val requestDeferredCredentialCommand: RequestDeferredCredentialCommand,
    private val deferredPollingStore: DeferredPollingStore,
    private val config: Oid4vciHolderConfig,
) : TypedServiceCommandAdapter<PollDeferredCredentialArgs, PollDeferredCredentialResult, IdkError>(
        commandId = PollDeferredCredentialCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<PollDeferredCredentialArgs>(),
        outputTypeToken = typeToken<PollDeferredCredentialResult>(),
    ),
    PollDeferredCredentialCommand {
    override val commandId: String get() = PollDeferredCredentialCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is PollDeferredCredentialArgs

    override suspend fun doExecute(
        args: PollDeferredCredentialArgs,
        applyDuring: (PollDeferredCredentialArgs) -> PollDeferredCredentialArgs,
    ): IdkResult<PollDeferredCredentialResult, IdkError> {
        val applied = applyDuring(args)

        val maxAttempts = applied.maxAttempts ?: config.maxDeferredPollingAttempts
        var interval = applied.interval ?: config.defaultDeferredPollingInterval
        var transactionId = applied.transactionId

        log.debug("Starting deferred credential polling: transactionId=$transactionId, maxAttempts=$maxAttempts, interval=$interval s")

        // Register the entry in the polling store so callers can observe pending polls
        val scheduleResult =
            deferredPollingStore.schedule(
                DeferredPollingEntry(
                    transactionId = transactionId,
                    sessionId = applied.sessionId,
                    interval = interval,
                ),
            )
        if (scheduleResult.isErr) {
            log.warn("Failed to schedule deferred polling entry: ${scheduleResult.error?.message}")
            // Non-fatal — continue polling even if store registration failed
        }

        for (attempt in 1..maxAttempts) {
            log.debug("Deferred poll attempt $attempt/$maxAttempts for transactionId=$transactionId")

            // Wait for the interval before each poll attempt
            delay(interval * MILLIS_PER_SECOND)

            val now = Clock.System.now().toEpochMilliseconds()
            deferredPollingStore.updateLastPolled(transactionId, now)

            val pollResult =
                requestDeferredCredentialCommand.execute(
                    RequestDeferredCredentialArgs(
                        deferredCredentialEndpoint = applied.deferredCredentialEndpoint,
                        accessToken = applied.accessToken,
                        transactionId = transactionId,
                        credentialResponseEncryption = applied.credentialResponseEncryption,
                        decryptionKey = applied.decryptionKey,
                    ),
                )

            if (pollResult.isErr) {
                val error = pollResult.error!!
                // Terminal error: credential_request_denied — stop immediately
                if (error.code.contains("credential_request_denied", ignoreCase = true) ||
                    error.message.defaultMessage.contains("credential_request_denied", ignoreCase = true)
                ) {
                    log.warn("Deferred credential request denied after $attempt attempt(s): ${error.message.defaultMessage}")
                    deferredPollingStore.markFailed(transactionId)
                    return Err(error)
                }
                // Transient error — log and continue
                log.warn("Transient error on deferred poll attempt $attempt: ${error.message.defaultMessage}")
                continue
            }

            val response = pollResult.value!!

            // Server may return a new transaction_id — update for subsequent polls
            val serverTransactionId = response.transactionId
            if (serverTransactionId != null && serverTransactionId != transactionId) {
                log.debug("Server updated transactionId: $transactionId -> $serverTransactionId")
                transactionId = serverTransactionId
            }

            // Server may return an updated interval — honour it
            val serverInterval = response.interval
            if (serverInterval != null && serverInterval != interval) {
                log.debug("Server updated polling interval: $interval -> $serverInterval s")
                interval = serverInterval
            }

            // OID4VCI 1.0 §8.3 / §10.2: credentials are delivered in the `credentials` array.
            val hasCredential = response.credentials?.isNotEmpty() == true
            if (hasCredential) {
                log.debug("Deferred credential ready after $attempt attempt(s)")
                deferredPollingStore.markCompleted(transactionId)
                return Ok(PollDeferredCredentialResult.Ready(credential = response, pollAttempts = attempt))
            }

            log.debug("Credential not yet ready (attempt $attempt/$maxAttempts)")
        }

        // Exhausted — valid Ok result, caller decides policy
        log.debug("Deferred credential polling exhausted after $maxAttempts attempt(s) for transactionId=$transactionId")
        return Ok(
            PollDeferredCredentialResult.Exhausted(
                transactionId = transactionId,
                attemptsMade = maxAttempts,
                lastInterval = interval,
            ),
        )
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
