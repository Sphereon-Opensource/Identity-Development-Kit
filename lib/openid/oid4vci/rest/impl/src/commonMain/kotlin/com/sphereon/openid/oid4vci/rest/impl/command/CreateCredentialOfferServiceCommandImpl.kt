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
import com.sphereon.openid.oid4vc.common.QrCodeService
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferInput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferOutput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferServiceCommand
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import com.sphereon.openid.oid4vci.rest.Oid4vciRestEventTypes
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialOfferServiceCommand>())
class CreateCredentialOfferServiceCommandImpl(
    execution: SessionExecution,
    private val createCredentialOfferCommand: CreateCredentialOfferCommand,
    private val credentialOfferSessionStore: CredentialOfferSessionStore,
    private val qrCodeService: QrCodeService,
    private val configProvider: Oid4vciRestConfigProvider,
    private val issuerConfigProvider: Oid4vciIssuerConfigProvider,
    private val sessionEventService: SessionEventService,
) : TypedServiceCommandAdapter<CreateCredentialOfferInput, CreateCredentialOfferOutput>(
        commandId = CreateCredentialOfferServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCredentialOfferInput>(),
        outputTypeToken = typeToken<CreateCredentialOfferOutput>(),
    ),
    CreateCredentialOfferServiceCommand {
    override val commandId: String get() = CreateCredentialOfferServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateCredentialOfferInput,
        applyDuring: (CreateCredentialOfferInput) -> CreateCredentialOfferInput,
    ): IdkResult<CreateCredentialOfferOutput, IdkError> {
        val input = applyDuring(args)

        if (input.credentialConfigurationIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credential_configuration_ids must not be empty"))
        }

        val hasPreAuth = input.grants?.preAuthorizedCode != null
        val hasAuthCode = input.grants?.authorizationCode != null
        val txCodeRequired = input.grants?.preAuthorizedCode?.txCode != null

        // Prefer the Credential Issuer Identifier (OID4VCI §11.2.2) — may include a path
        // component (e.g. "${BASE}/oid4vci"). Fall back to the REST external base URL for
        // backwards compat with deployments that only configure the latter.
        val effectiveIssuerId =
            input.issuerId?.takeIf { it.isNotBlank() }
                ?: issuerConfigProvider.issuerIdentifier.takeIf { it.isNotBlank() }
                ?: configProvider.getConfig().externalBaseUrl
                ?: ""

        val createArgs =
            CreateCredentialOfferArgs(
                issuerId = effectiveIssuerId,
                credentialConfigurationIds = input.credentialConfigurationIds,
                preAuthorizedCodeGrant = hasPreAuth || (!hasPreAuth && !hasAuthCode),
                authorizationCodeGrant = hasAuthCode,
                txCodeRequired = txCodeRequired,
                preSeededAttributes = input.credentialSubjectData,
                offerTtlSeconds = input.ttlSeconds ?: CredentialOfferSessionStore.DEFAULT_TTL_SECONDS,
            )

        val created =
            createCredentialOfferCommand.execute(createArgs).getOrElse { error ->
                return Err(error)
            }

        val correlationId = input.correlationId ?: generateCorrelationId()
        val now = Clock.System.now().toEpochMilliseconds()
        val ttl = input.ttlSeconds ?: CredentialOfferSessionStore.DEFAULT_TTL_SECONDS

        val session =
            CredentialOfferSession(
                correlationId = correlationId,
                offerId = created.offerId,
                issuanceSessionId = created.sessionId,
                status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                callbackConfig = input.callback,
                state = input.state,
                createdAt = now,
                lastUpdatedAt = now,
                expiresAt = now + (ttl * 1000),
            )

        credentialOfferSessionStore.create(session).getOrElse { error ->
            return Err(error)
        }

        val config = configProvider.getConfig()
        val statusUri =
            config.externalBaseUrl?.let {
                "${it.trimEnd('/')}/oid4vci/backend/credential/offers/$correlationId"
            }

        val qrUri =
            input.qrCodeOptions?.let { options ->
                qrCodeService.generateDataUri(created.offerUri, options)
            }

        emitSessionCreatedEvent(correlationId, input.credentialConfigurationIds, created.offerUri)

        return Ok(
            CreateCredentialOfferOutput(
                correlationId = correlationId,
                offerUri = created.offerUri,
                statusUri = statusUri,
                qrUri = qrUri,
                txCode = created.txCode,
            ),
        )
    }

    private fun generateCorrelationId(): String = Uuid.random().toString()

    private suspend fun emitSessionCreatedEvent(
        correlationId: String,
        credentialConfigurationIds: List<String>,
        offerUri: String,
    ) {
        try {
            sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(Oid4vciRestEventTypes.SESSION_CREATED)
                    .origin(CreateCredentialOfferServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                            put("credentialConfigurationIds", credentialConfigurationIds.joinToString(","))
                            put("offerUri", offerUri)
                        },
                    ).build(),
            )
        } catch (expected: Exception) {
            log.warn("Failed to emit SESSION_CREATED event: ${expected.message}")
        }
    }
}
