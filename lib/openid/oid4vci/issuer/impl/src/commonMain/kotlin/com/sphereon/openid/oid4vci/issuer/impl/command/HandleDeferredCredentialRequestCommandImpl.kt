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
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Handles deferred credential requests per OID4VCI 1.0 Section 9.1 / 1.1 Section 10.
 *
 * Flow:
 * 1. Validate access token via AS bridge
 * 2. Look up deferred entry by transaction ID
 * 3. If READY -> deliver credential, mark as DELIVERED
 * 4. If PENDING -> return issuance_pending error with interval
 * 5. If FAILED/EXPIRED/DELIVERED -> return invalid_transaction_id error
 * 6. Apply encryption if requested
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleDeferredCredentialRequestCommand>())
class HandleDeferredCredentialRequestCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val deferredStore: DeferredCredentialStore,
    private val encryptor: CredentialResponseEncryptor,
    private val nonceManager: NonceManager,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<HandleDeferredCredentialRequestArgs, CredentialResponse, IdkError>(
        commandId = HandleDeferredCredentialRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleDeferredCredentialRequestArgs>(),
        outputTypeToken = typeToken<CredentialResponse>(),
    ),
    HandleDeferredCredentialRequestCommand {
    override val commandId: String get() = HandleDeferredCredentialRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleDeferredCredentialRequestArgs

    override suspend fun doExecute(
        args: HandleDeferredCredentialRequestArgs,
        applyDuring: (HandleDeferredCredentialRequestArgs) -> HandleDeferredCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(args, result)
        return result
    }

    private suspend fun emitOutcome(
        args: HandleDeferredCredentialRequestArgs,
        result: IdkResult<CredentialResponse, IdkError>,
    ) {
        val type =
            if (result.isOk) {
                EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED
            } else {
                EventTypes.OID4VCI_CREDENTIAL_FAILED
            }
        val category = if (result.isOk) EventCategories.OPERATION else EventCategories.ERROR
        val payload =
            buildJsonObject {
                put("transactionId", args.deferredRequest.transactionId)
                if (!result.isOk) put("operation", "deferredCredential")
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OID4VCI)
                .category(category)
                .origin(HandleDeferredCredentialRequestCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun doExecuteInternal(
        args: HandleDeferredCredentialRequestArgs,
        applyDuring: (HandleDeferredCredentialRequestArgs) -> HandleDeferredCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val applied = applyDuring(args)
        val deferredRequest = applied.deferredRequest

        // 1. Validate access token
        asBridge
            .validateAccessToken(
                ValidateAccessTokenArgs(
                    accessToken = applied.accessToken,
                    dpopProof = applied.dpopProof,
                    httpUrl = applied.httpUrl,
                    httpMethod = applied.httpMethod,
                ),
            ).getOrElse { return Err(it) }

        // 2. Look up deferred entry
        val transactionId = deferredRequest.transactionId
        val entry =
            deferredStore.get(transactionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "invalid_transaction_id"))

        // 3. Handle based on status
        return when (entry.status) {
            DeferredCredentialStatus.READY -> {
                // Mark as delivered
                deferredStore
                    .update(entry.copy(status = DeferredCredentialStatus.DELIVERED))
                    .getOrElse { return Err(it) }

                // Build credential response. OID4VCI 1.0 §8.3 always uses the `credentials`
                // array form, whether the deferred result holds a single credential or a batch.
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
                Ok(
                    CredentialResponse(
                        credentials = items,
                        notificationId = entry.notificationId,
                    ),
                )
            }

            DeferredCredentialStatus.PENDING -> {
                // OID4VCI 1.1 Section 10.2: return Ok with transaction_id + interval so the
                // HTTP adapter can respond 202 with the correct body (not an error response).
                Ok(
                    CredentialResponse(
                        transactionId = transactionId,
                        interval = entry.retryAfterSeconds,
                    ),
                )
            }

            DeferredCredentialStatus.FAILED,
            DeferredCredentialStatus.EXPIRED,
            DeferredCredentialStatus.DELIVERED,
            -> {
                Err(IdkError.NOT_FOUND_ERROR(message = "invalid_transaction_id"))
            }
        }
    }
}
