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
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.AuthorizationCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.TxCodeConfig
import com.sphereon.openid.oid4vci.issuer.bridge.CreateAuthContextArgs
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.CreatedCredentialOffer
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerProtocolConfig
import com.sphereon.openid.oid4vci.issuer.impl.pipeline.OfferPipelineInitializer
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialOfferCommand>())
class CreateCredentialOfferCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val offerStore: CredentialOfferStore,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val eventService: SessionEventService? = null,
    /**
     * Pre-flight coordinator: per-credential grant-policy validation, pipeline-configuration
     * resolution, §6.5 wallet-auth invariant validation, and pipeline-session initialisation.
     * An initializer whose internal collaborators are all absent (pure-IDK / no-pipeline
     * deployment) is a graceful no-op: grant validation accepts everything, no pipeline is
     * resolved, and the wallet-auth invariant is skipped, matching the prior inlined behaviour.
     */
    private val pipelineInitializer: OfferPipelineInitializer,
) : TypedServiceCommandAdapter<CreateCredentialOfferArgs, CreatedCredentialOffer, IdkError>(
        commandId = CreateCredentialOfferCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCredentialOfferArgs>(),
        outputTypeToken = typeToken<CreatedCredentialOffer>(),
    ),
    CreateCredentialOfferCommand {
    override val commandId: String get() = CreateCredentialOfferCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateCredentialOfferArgs

    override suspend fun doExecute(
        args: CreateCredentialOfferArgs,
        applyDuring: (CreateCredentialOfferArgs) -> CreateCredentialOfferArgs,
    ): IdkResult<CreatedCredentialOffer, IdkError> {
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(args, result)
        return result
    }

    private suspend fun emitOutcome(
        args: CreateCredentialOfferArgs,
        result: IdkResult<CreatedCredentialOffer, IdkError>,
    ) {
        if (!result.isOk) {
            return
        }
        val payload =
            buildJsonObject {
                put(
                    "credentialConfigurationIds",
                    buildJsonArray { args.credentialConfigurationIds.forEach { add(it) } },
                )
                put("preAuthorizedCodeGrant", args.preAuthorizedCodeGrant)
                put("authorizationCodeGrant", args.authorizationCodeGrant)
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(EventTypes.OID4VCI_OFFER_CREATED)
                .subsystem(EventSubsystems.OID4VCI)
                .category(EventCategories.OPERATION)
                .origin(CreateCredentialOfferCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun doExecuteInternal(
        args: CreateCredentialOfferArgs,
        applyDuring: (CreateCredentialOfferArgs) -> CreateCredentialOfferArgs,
    ): IdkResult<CreatedCredentialOffer, IdkError> {
        val applied = applyDuring(args)

        validateRequestShape(applied).getOrElse { return Err(it) }
        pipelineInitializer.validateGrants(applied).getOrElse { return Err(it) }

        val now = Clock.System.now()
        val offerId = Uuid.random().toString()
        val sessionId = Uuid.random().toString()

        // Resolve a pipeline configuration and initialise a pipeline session when one is
        // configured. A failure here does not block offer creation; the session is
        // created without a pipeline link instead.
        val pipelineCorrelationId = pipelineInitializer.initializePipeline(applied)

        val session = buildSession(applied, sessionId, pipelineCorrelationId, now.epochSeconds)
        sessionStore.create(session).getOrElse { return Err(it) }

        val grantsAndTxCode = buildGrants(applied, sessionId, session).getOrElse { return Err(it) }

        val offer =
            CredentialOffer(
                credentialIssuer = applied.issuerId,
                credentialConfigurationIds = applied.credentialConfigurationIds,
                grants = grantsAndTxCode.grants,
            )

        offerStore.store(offerId, offer, applied.offerTtlSeconds, sessionId = sessionId).getOrElse { return Err(it) }

        val offerUri = buildOfferUri(applied, offerId)

        return Ok(
            CreatedCredentialOffer(
                offerId = offerId,
                sessionId = sessionId,
                offer = offer,
                offerUri = offerUri,
                txCode = grantsAndTxCode.txCode,
            ),
        )
    }

    private fun validateRequestShape(args: CreateCredentialOfferArgs): IdkResult<Unit, IdkError> {
        if (args.credentialConfigurationIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "At least one credential_configuration_id is required"))
        }
        if (args.uriLifecycle != OfferUriLifecycle.SINGLE_USE && args.rateLimit == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "rate_limit is mandatory for a reusable offer"))
        }
        return Ok(Unit)
    }

    /**
     * Create session (issuerState = sessionId for auth-code grant linkage). `preAuthCode`
     * is filled in after the AS bridge registers the code in [buildGrants].
     */
    private fun buildSession(
        args: CreateCredentialOfferArgs,
        sessionId: String,
        pipelineCorrelationId: String?,
        nowEpochSeconds: Long,
    ): IssuanceSession =
        IssuanceSession(
            sessionId = sessionId,
            issuerId = args.issuerId,
            credentialConfigurationIds = args.credentialConfigurationIds,
            issuerState =
                if (args.authorizationCodeGrant) {
                    sessionId
                } else {
                    null
                },
            status = IssuanceSessionStatus.OFFER_CREATED,
            preSeededAttributes = args.preSeededAttributes,
            boundUsageToken = args.boundUsageToken,
            postIssuanceHookAllowList = args.postIssuanceHookAllowList,
            pipelineCorrelationId = pipelineCorrelationId,
            createdAt = nowEpochSeconds,
            expiresAt = nowEpochSeconds + args.offerTtlSeconds,
        )

    /**
     * Result of [buildGrants]: optional grants envelope and (when pre-authorized-code is
     * issued) the wallet-facing transaction code.
     */
    private data class GrantsAndTxCode(
        val grants: CredentialOfferGrants?,
        val txCode: String?,
    )

    private suspend fun buildGrants(
        args: CreateCredentialOfferArgs,
        sessionId: String,
        session: IssuanceSession,
    ): IdkResult<GrantsAndTxCode, IdkError> {
        val preAuthResult =
            if (args.preAuthorizedCodeGrant) {
                registerPreAuthorizedGrant(args, sessionId, session).getOrElse { return Err(it) }
            } else {
                PreAuthGrantResult(grant = null, txCode = null)
            }
        val authCodeGrant =
            if (args.authorizationCodeGrant) {
                createAuthorizationGrant(args, sessionId).getOrElse { return Err(it) }
            } else {
                null
            }

        val grants =
            if (preAuthResult.grant != null || authCodeGrant != null) {
                CredentialOfferGrants(
                    authorizationCode = authCodeGrant,
                    preAuthorizedCode = preAuthResult.grant,
                )
            } else {
                null
            }
        return Ok(GrantsAndTxCode(grants = grants, txCode = preAuthResult.txCode))
    }

    /**
     * Result of [registerPreAuthorizedGrant]: the registered pre-authorised-code grant and
     * the wallet-facing transaction code (when the AS issued one).
     */
    private data class PreAuthGrantResult(
        val grant: PreAuthorizedCodeOfferGrant?,
        val txCode: String?,
    )

    private suspend fun registerPreAuthorizedGrant(
        args: CreateCredentialOfferArgs,
        sessionId: String,
        session: IssuanceSession,
    ): IdkResult<PreAuthGrantResult, IdkError> {
        val registered =
            asBridge
                .registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = sessionId,
                        credentialConfigurationIds = args.credentialConfigurationIds,
                        txCodeRequired = args.txCodeRequired,
                        txCodeLength = args.txCodeLength,
                        txCodeInputMode = args.txCodeInputMode,
                        issuerIdentifier = args.issuerId,
                        // OID4VCI 1.0 §5.1.2 / §8.2.1.1: when the AS includes
                        // `credential_identifiers` in the token's authorization_details,
                        // the wallet MUST send one in the credential request, so the
                        // issuer can resolve the IssuanceSession by stable identifier
                        // rather than falling back to a single configId→sessionId
                        // index in the session store. The fallback breaks under multi-
                        // recipient batch issuance (each new mint for the same
                        // credential_configuration_id overwrites the previous mapping,
                        // so only the last-minted holder gets the right credential).
                        useCredentialIdentifiers = true,
                    ),
                ).getOrElse { return Err(it) }

        val grant =
            PreAuthorizedCodeOfferGrant(
                preAuthorizedCode = registered.code,
                txCode =
                    if (args.txCodeRequired) {
                        TxCodeConfig(inputMode = args.txCodeInputMode ?: "numeric", length = args.txCodeLength)
                    } else {
                        null
                    },
            )
        // Stash the registered pre-auth code on the session so post-issuance
        // hooks can correlate the signed credential to the code that
        // authorized it (audit + downstream pre-auth lookup).
        sessionStore
            .update(session.copy(preAuthCode = registered.code))
            .getOrElse { return Err(it) }
        return Ok(PreAuthGrantResult(grant = grant, txCode = registered.txCode))
    }

    private suspend fun createAuthorizationGrant(
        args: CreateCredentialOfferArgs,
        sessionId: String,
    ): IdkResult<AuthorizationCodeOfferGrant, IdkError> {
        val authContext =
            asBridge
                .createAuthorizationContext(
                    CreateAuthContextArgs(
                        issuerState = sessionId,
                        credentialConfigurationIds = args.credentialConfigurationIds,
                    ),
                ).getOrElse { return Err(it) }
        return Ok(AuthorizationCodeOfferGrant(issuerState = authContext.issuerState))
    }

    /**
     * Build the wallet-facing offer URI per OID4VCI 1.0 §4.1.1 / §4.1.3 / §11.2.2.
     *
     * - The `/credentials/offers/{offerId}` endpoint is mounted relative to the credential
     *   issuer identifier, which must be an absolute http(s) URL.
     * - The outer scheme (deeplink or universal/app link) carries `credential_offer_uri` as a
     *   percent-encoded query parameter; when the scheme already includes a query string the
     *   separator becomes `&`.
     */
    private fun buildOfferUri(
        args: CreateCredentialOfferArgs,
        offerId: String,
    ): String {
        val issuerBase = args.issuerId.trimEnd('/')
        require(issuerBase.startsWith("http://") || issuerBase.startsWith("https://")) {
            "Issuer identifier must be an absolute http(s) URL to produce a dereferenceable " +
                "credential_offer_uri (OID4VCI 1.0 §4.1.3); got '${args.issuerId}'. " +
                "Configure 'oid4vci.issuer.identifier' (or the equivalent tenant-scoped key)."
        }
        val scheme = args.scheme?.takeIf { it.isNotBlank() } ?: "openid-credential-offer://"
        val separator =
            if (scheme.contains("?")) {
                "&"
            } else {
                "?"
            }
        val protocolBasePath = Oid4vciIssuerProtocolConfig.resolveBasePath(conf.app)
        val protocolBase = Oid4vciIssuerProtocolConfig.appendBasePath(issuerBase, protocolBasePath)
        val offerEndpoint = "$protocolBase/credentials/offers/$offerId"
        return "$scheme${separator}credential_offer_uri=${percentEncodeQueryComponent(offerEndpoint)}"
    }
}
