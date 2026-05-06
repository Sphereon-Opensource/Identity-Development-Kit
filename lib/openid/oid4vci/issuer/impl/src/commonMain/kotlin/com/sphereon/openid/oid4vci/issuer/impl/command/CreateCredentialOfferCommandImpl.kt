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
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.AuthorizationCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.TxCodeConfig
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.CreatedCredentialOffer
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
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
    private val policyResolver: CredentialIssuancePolicyResolver? = null,
    private val eventService: SessionEventService? = null,
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
        if (!result.isOk) return
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

        if (applied.credentialConfigurationIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "At least one credential_configuration_id is required"))
        }

        // Validate grant types against per-credential policy (most restrictive across all configs).
        if (policyResolver != null) {
            for (configId in applied.credentialConfigurationIds) {
                val policy = policyResolver.resolve(configId)

                if (applied.preAuthorizedCodeGrant && !policy.preAuthorizedCodeAllowed) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Pre-authorized code grant is not allowed for credential configuration '$configId'",
                        ),
                    )
                }

                if (applied.authorizationCodeGrant && !policy.authorizationCodeAllowed) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Authorization code grant is not allowed for credential configuration '$configId'",
                        ),
                    )
                }
            }
        }

        val now = Clock.System.now()
        val offerId = Uuid.random().toString()
        val sessionId = Uuid.random().toString()

        // Create session (issuerState = sessionId for auth-code grant linkage).
        // preAuthCode is filled in after the AS bridge registers the code below.
        val session =
            IssuanceSession(
                sessionId = sessionId,
                issuerId = applied.issuerId,
                credentialConfigurationIds = applied.credentialConfigurationIds,
                issuerState =
                    if (applied.authorizationCodeGrant) {
                        sessionId
                    } else {
                        null
                    },
                status = IssuanceSessionStatus.OFFER_CREATED,
                preSeededAttributes = applied.preSeededAttributes,
                boundUsageToken = applied.boundUsageToken,
                postIssuanceHookAllowList = applied.postIssuanceHookAllowList,
                createdAt = now.epochSeconds,
                expiresAt = now.epochSeconds + applied.offerTtlSeconds,
            )
        sessionStore.create(session).getOrElse { return Err(it) }

        // Build grants
        var preAuthGrant: PreAuthorizedCodeOfferGrant? = null
        var authCodeGrant: AuthorizationCodeOfferGrant? = null
        var txCode: String? = null

        if (applied.preAuthorizedCodeGrant) {
            val registered =
                asBridge
                    .registerPreAuthorizedCode(
                        RegisterPreAuthCodeArgs(
                            sessionId = sessionId,
                            credentialConfigurationIds = applied.credentialConfigurationIds,
                            txCodeRequired = applied.txCodeRequired,
                            issuerIdentifier = applied.issuerId,
                            useCredentialIdentifiers = false,
                        ),
                    ).getOrElse { return Err(it) }

            txCode = registered.txCode
            preAuthGrant =
                PreAuthorizedCodeOfferGrant(
                    preAuthorizedCode = registered.code,
                    txCode =
                        if (applied.txCodeRequired) {
                            TxCodeConfig()
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
        }

        if (applied.authorizationCodeGrant) {
            val authContext =
                asBridge
                    .createAuthorizationContext(
                        com.sphereon.openid.oid4vci.issuer.bridge.CreateAuthContextArgs(
                            issuerState = sessionId,
                            credentialConfigurationIds = applied.credentialConfigurationIds,
                        ),
                    ).getOrElse { return Err(it) }

            authCodeGrant =
                AuthorizationCodeOfferGrant(
                    issuerState = authContext.issuerState,
                )
        }

        val grants =
            if (preAuthGrant != null || authCodeGrant != null) {
                CredentialOfferGrants(
                    authorizationCode = authCodeGrant,
                    preAuthorizedCode = preAuthGrant,
                )
            } else {
                null
            }

        val offer =
            CredentialOffer(
                credentialIssuer = applied.issuerId,
                credentialConfigurationIds = applied.credentialConfigurationIds,
                grants = grants,
            )

        offerStore.store(offerId, offer, applied.offerTtlSeconds, sessionId = sessionId).getOrElse { return Err(it) }

        // Per OID4VCI §11.2.2, issuer endpoints are served relative to the Credential Issuer
        // Identifier. The /credentials/offers endpoint is mounted on the protocol adapter whose
        // base path is relative to the issuer identifier (so issuerId is expected to be the
        // full identifier URL, e.g. "${BASE}/oid4vci" when hosted under a sub-path).
        val issuerBase = applied.issuerId.trimEnd('/')
        // Outer deeplink prefix the wallet listens on (OID4VCI 1.0 §4.1.1). Caller-supplied
        // `scheme` lets the demo / production deployment switch between bare-scheme deeplinks
        // (`openid-credential-offer://`, `haip://`) and full universal-link / app-link URLs
        // (`https://wallet.example.com/credential_offer`). When the scheme already carries a
        // query string (e.g. a custom URL with `?source=demo`), append with `&` instead of `?`
        // so the resulting URI stays parseable.
        val scheme = applied.scheme?.takeIf { it.isNotBlank() } ?: "openid-credential-offer://"
        val separator = if (scheme.contains("?")) "&" else "?"
        val offerUri = "$scheme${separator}credential_offer_uri=$issuerBase/credentials/offers/$offerId"

        return Ok(
            CreatedCredentialOffer(
                offerId = offerId,
                sessionId = sessionId,
                offer = offer,
                offerUri = offerUri,
                txCode = txCode,
            ),
        )
    }
}
