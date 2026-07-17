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
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferInput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferOutput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferServiceCommand
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import com.sphereon.openid.oid4vci.rest.CredentialOfferTemplate
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import com.sphereon.openid.oid4vci.rest.Oid4vciRestEventTypes
import com.sphereon.openid.oid4vci.rest.impl.event.putSessionEventIdentity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
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
    private val instanceIdProvider: Oid4vciIssuerInstanceIdProvider,
    private val sessionEventService: SessionEventService,
) : TypedServiceCommandAdapter<CreateCredentialOfferInput, CreateCredentialOfferOutput, IdkError>(
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
        val instanceId =
            instanceIdProvider.currentInstanceId()?.trim()?.takeIf(String::isNotEmpty)
                ?: return Err(
                    IdkError.INVALID_STATE(
                        message = "An OID4VCI issuer instance must be resolved before creating a credential offer",
                    ),
                )

        if (input.credentialConfigurationIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credential_configuration_ids must not be empty"))
        }

        val hasPreAuth = input.grants?.preAuthorizedCode != null
        val hasAuthCode = input.grants?.authorizationCode != null
        val txCodeConfig = input.grants?.preAuthorizedCode?.txCode
        val txCodeRequired = txCodeConfig != null

        // Prefer the Credential Issuer Identifier (OID4VCI §11.2.2) — may include a path
        // component (e.g. "${BASE}/oid4vci").
        val effectiveIssuerId =
            input.issuerId?.takeIf { it.isNotBlank() }
                ?: issuerConfigProvider.issuerIdentifier.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.INVALID_STATE(message = "Credential issuer identifier is not configured"))

        val createArgs =
            CreateCredentialOfferArgs(
                instanceId = instanceId,
                issuerId = effectiveIssuerId,
                credentialConfigurationIds = input.credentialConfigurationIds,
                preAuthorizedCodeGrant = hasPreAuth || (!hasPreAuth && !hasAuthCode),
                authorizationCodeGrant = hasAuthCode,
                txCodeRequired = txCodeRequired,
                txCodeLength = txCodeConfig?.length,
                txCodeInputMode = txCodeConfig?.inputMode,
                preSeededAttributes = input.credentialSubjectData,
                initialLifecycleFields = input.initialConnectorFields,
                offerTtlSeconds = input.ttlSeconds ?: CredentialOfferSessionStore.DEFAULT_TTL_SECONDS,
                scheme = input.scheme,
                uriLifecycle = input.uriLifecycle,
                rateLimit = input.rateLimit,
            )

        val created =
            createCredentialOfferCommand.execute(createArgs).getOrElse { error ->
                return Err(error)
            }

        val correlationId = input.correlationId ?: generateCorrelationId()
        val now = Clock.System.now().toEpochMilliseconds()
        val ttl = input.ttlSeconds ?: CredentialOfferSessionStore.DEFAULT_TTL_SECONDS

        // Snapshot the replayable creation inputs so the GET handler can mint a fresh inner
        // offer on each fetch of a reusable URI. Populated for every offer (the snapshot is
        // small and harmless for single-use sessions, which simply never replay it).
        val offerTemplate =
            CredentialOfferTemplate(
                issuerId = createArgs.issuerId,
                credentialConfigurationIds = createArgs.credentialConfigurationIds,
                preAuthorizedCodeGrant = createArgs.preAuthorizedCodeGrant,
                authorizationCodeGrant = createArgs.authorizationCodeGrant,
                txCodeRequired = createArgs.txCodeRequired,
                txCodeLength = createArgs.txCodeLength,
                txCodeInputMode = createArgs.txCodeInputMode,
                preSeededAttributes = createArgs.preSeededAttributes,
                initialConnectorFields = createArgs.initialLifecycleFields,
                offerTtlSeconds = createArgs.offerTtlSeconds,
                scheme = createArgs.scheme,
            )

        val session =
            CredentialOfferSession(
                correlationId = correlationId,
                instanceId = created.instanceId,
                offerId = created.offerId,
                issuanceSessionId = created.sessionId,
                status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                callbackConfig = input.callback,
                state = input.state,
                createdAt = now,
                lastUpdatedAt = now,
                expiresAt = now + (ttl * 1000),
                uriLifecycle = input.uriLifecycle,
                rateLimit = input.rateLimit,
                offerTemplate = offerTemplate,
            )

        credentialOfferSessionStore.create(session).getOrElse { error ->
            return Err(error)
        }

        val config = configProvider.getConfig()
        val statusUri =
            config.externalBaseUrl?.let {
                "${it.trimEnd('/')}/api/oid4vci/v1/backend/credential/offers/$correlationId"
            }

        val qrUri =
            input.qrCodeOptions?.let { options ->
                qrCodeService.generateDataUri(created.offerUri, options)
            }

        emitSessionCreatedEvent(created.instanceId, correlationId, created.sessionId, input.credentialConfigurationIds)

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
        instanceId: String,
        correlationId: String,
        protocolSessionId: String,
        credentialConfigurationIds: List<String>,
    ) {
        sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(Oid4vciRestEventTypes.SESSION_CREATED)
                    .origin(CreateCredentialOfferServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                            put("credentialConfigurationIds", buildJsonArray {
                                credentialConfigurationIds.forEach { add(JsonPrimitive(it)) }
                            })
                            putSessionEventIdentity(
                                protocolSessionId = protocolSessionId,
                                instanceId = instanceId,
                                newState = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED.name,
                                creationSnapshot = buildJsonObject {
                                    put("correlationId", correlationId)
                                    put("credentialConfigurationIds", buildJsonArray {
                                        credentialConfigurationIds.forEach { add(JsonPrimitive(it)) }
                                    })
                                },
                                currentResult = buildJsonObject {
                                    put("correlationId", correlationId)
                                    put("sessionId", protocolSessionId)
                                    put("status", CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED.name)
                                },
                            )
                        },
                    ).build(),
            )
    }
}
