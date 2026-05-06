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

package com.sphereon.openid.oid4vci.rest.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.SessionError
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusInput
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusOutput
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusServiceCommand
import com.sphereon.openid.oid4vci.rest.IssuanceData
import com.sphereon.openid.oid4vci.rest.Oid4vciRestEventTypes
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCredentialOfferStatusServiceCommand>())
class GetCredentialOfferStatusServiceCommandImpl(
    execution: SessionExecution,
    private val credentialOfferSessionStore: CredentialOfferSessionStore,
    private val issuanceSessionStore: CredentialIssuanceSessionStore,
    private val sessionEventService: SessionEventService,
) : TypedServiceCommandAdapter<GetCredentialOfferStatusInput, GetCredentialOfferStatusOutput, IdkError>(
        commandId = GetCredentialOfferStatusServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetCredentialOfferStatusInput>(),
        outputTypeToken = typeToken<GetCredentialOfferStatusOutput>(),
    ),
    GetCredentialOfferStatusServiceCommand {
    override val commandId: String get() = GetCredentialOfferStatusServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetCredentialOfferStatusInput,
        applyDuring: (GetCredentialOfferStatusInput) -> GetCredentialOfferStatusInput,
    ): IdkResult<GetCredentialOfferStatusOutput, IdkError> {
        val input = applyDuring(args)

        val session =
            credentialOfferSessionStore.get(input.correlationId).getOrElse { error ->
                return Err(error)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session not found for correlation ID: ${input.correlationId}"))

        val currentStatus = resolveCurrentStatus(session)
        val now = Clock.System.now().toEpochMilliseconds()

        if (currentStatus != session.status) {
            val updatedSession =
                session.copy(
                    status = currentStatus,
                    lastUpdatedAt = now,
                )
            credentialOfferSessionStore.update(updatedSession)
        }

        val error =
            if (currentStatus == CredentialOfferSessionStatus.ERROR) {
                SessionError(code = "issuance_error", message = "Credential issuance failed")
            } else {
                null
            }

        val issuanceData =
            if (currentStatus == CredentialOfferSessionStatus.CREDENTIAL_ISSUED) {
                buildIssuanceData(session)
            } else {
                null
            }

        emitStatusPolledEvent(input.correlationId, currentStatus)

        return Ok(
            GetCredentialOfferStatusOutput(
                correlationId = input.correlationId,
                status = currentStatus,
                lastUpdated = now,
                error = error,
                issuanceData = issuanceData,
            ),
        )
    }

    private suspend fun resolveCurrentStatus(session: CredentialOfferSession): CredentialOfferSessionStatus {
        val internalSessionId = session.issuanceSessionId ?: return session.status

        val internalSession =
            issuanceSessionStore.get(internalSessionId).getOrElse {
                return session.status
            } ?: return session.status

        return mapInternalStatus(internalSession.status)
    }

    private suspend fun buildIssuanceData(session: CredentialOfferSession): IssuanceData? {
        val internalSessionId = session.issuanceSessionId ?: return null

        val internalSession =
            issuanceSessionStore.get(internalSessionId).getOrElse {
                return null
            } ?: return null

        return IssuanceData(
            credentialConfigurationIds = internalSession.credentialConfigurationIds,
            credentialIdentifiers = internalSession.credentialIdentifiers.ifEmpty { null },
        )
    }

    private suspend fun emitStatusPolledEvent(
        correlationId: String,
        status: CredentialOfferSessionStatus,
    ) {
        try {
            sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(Oid4vciRestEventTypes.STATUS_POLLED)
                    .origin(GetCredentialOfferStatusServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                            put("status", status.name)
                        },
                    ).build(),
            )
        } catch (expected: Exception) {
            log.debug("Failed to emit STATUS_POLLED event: ${expected.message}")
        }
    }

    companion object {
        fun mapInternalStatus(status: IssuanceSessionStatus): CredentialOfferSessionStatus =
            when (status) {
                IssuanceSessionStatus.OFFER_CREATED -> CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED

                IssuanceSessionStatus.OFFER_RECEIVED -> CredentialOfferSessionStatus.CREDENTIAL_OFFER_RETRIEVED

                IssuanceSessionStatus.TOKEN_REQUESTED -> CredentialOfferSessionStatus.TOKEN_REQUESTED

                IssuanceSessionStatus.CREDENTIAL_REQUESTED -> CredentialOfferSessionStatus.CREDENTIAL_REQUESTED

                IssuanceSessionStatus.CREDENTIAL_ISSUED,
                IssuanceSessionStatus.COMPLETED,
                -> CredentialOfferSessionStatus.CREDENTIAL_ISSUED

                IssuanceSessionStatus.DEFERRED -> CredentialOfferSessionStatus.CREDENTIAL_REQUESTED

                IssuanceSessionStatus.EXPIRED,
                IssuanceSessionStatus.FAILED,
                -> CredentialOfferSessionStatus.ERROR
            }
    }
}
