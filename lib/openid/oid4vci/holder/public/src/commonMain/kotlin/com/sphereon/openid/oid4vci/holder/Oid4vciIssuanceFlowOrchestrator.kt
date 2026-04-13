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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent

/**
 * Service interface for OID4VCI issuance flow orchestration.
 *
 * Exposes higher-level flow operations that compose multiple lower-level commands:
 * - [pollDeferredCredential] — polls a deferred credential endpoint with configurable retry
 * - [sendNotificationWithRetry] — sends a credential event notification with exponential backoff
 * - [requestCredentialWithFlow] — full end-to-end credential issuance flow
 */
interface Oid4vciIssuanceFlowService {
    suspend fun pollDeferredCredential(args: PollDeferredCredentialArgs): IdkResult<PollDeferredCredentialResult, IdkError>

    suspend fun sendNotificationWithRetry(args: SendNotificationWithRetryArgs): IdkResult<Unit, IdkError>

    suspend fun requestCredentialWithFlow(args: RequestCredentialWithFlowArgs): IdkResult<CredentialFlowResult, IdkError>
}

/**
 * Adapter interface that mirrors [Oid4vciIssuanceFlowService].
 *
 * Kept as a separate type so deployment assemblies can bind it independently.
 */
interface Oid4vciIssuanceFlowAdapter : Oid4vciIssuanceFlowService

/**
 * Main orchestrator interface for OID4VCI issuance flows, exposing command objects.
 *
 * The [Commands] inner interface gives callers direct access to the underlying command
 * instances, enabling command chaining and composition patterns.
 */
interface Oid4vciIssuanceFlowOrchestrator : Oid4vciIssuanceFlowAdapter {
    val commands: Commands

    interface Commands {
        val pollDeferredCredential: PollDeferredCredentialCommand
        val sendNotificationWithRetry: SendNotificationWithRetryCommand
        val requestCredentialWithFlow: RequestCredentialWithFlowCommand
    }
}
