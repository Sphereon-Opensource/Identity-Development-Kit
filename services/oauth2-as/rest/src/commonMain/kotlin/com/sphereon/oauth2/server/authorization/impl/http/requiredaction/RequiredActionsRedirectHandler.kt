/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.http.requiredaction

import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.sourceAs
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.idv.model.IdvExecution
import com.sphereon.identity.idv.model.IdvSubjectRef
import com.sphereon.identity.idv.model.RedirectAction
import com.sphereon.identity.idv.model.UserInputAction
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredAction
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionsExecutionStrategy
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Translates a [AuthorizationServerError.RequiredActionsPending] refusal from the
 * AS gate into a 302 redirect into the IDV runner. Picks the
 * [RequiredActionsExecutionStrategy] off the strategy multibinding via per-client
 * `additionalMetadata` → tenant config → app default, then dispatches based on the
 * IDV execution's first pending action:
 *
 *  - [RedirectAction] → 302 to the action's URL (provider-driven flows like a
 *    federated OIDC bounce).
 *  - [UserInputAction] → 302 to the configurable runner UI path with the IDV
 *    execution id appended (and `session_id` round-tripped as a query parameter
 *    so the AS callback can resume on completion).
 *
 * **Failure modes** (each returns null so the caller falls back to JSON
 * `interaction_required`):
 *  - The AS session is gone (TTL expired between gate refusal and handler call).
 *  - No strategy matches the resolved id (configuration drift).
 *  - The strategy itself returns Err (composer found no providers; stored use
 *    case id missing).
 *  - The IDV execution surfaced no pending action (race between start and read).
 *
 * **No EDK leakage:** the handler depends only on IDK SPIs (the strategy
 * multibinding, the IDV execution model, the OAuth2 session/client registry).
 */
interface RequiredActionsRedirectHandler {
    /**
     * Render the next-step HTTP response when [error] looks like a
     * [AuthorizationServerError.RequiredActionsPending] payload. The HTTP layer hands
     * over the raw [IdkError] (the typed error is mapped through
     * `IdkError.fromDTO` further upstream and the discriminator is the `code` +
     * `meta` map, not the runtime class).
     *
     * Returns null when:
     *  - the error is not a required-actions payload, or
     *  - the orchestrator cannot proceed (no strategy, no provider, no pending action).
     *
     * In either case the caller falls back to the standard OIDC JSON error response.
     */
    suspend fun handle(
        error: IdkError,
        sessionId: String,
        baseUrl: String,
    ): GenericHttpResponse?
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultRequiredActionsRedirectHandler(
    private val strategies: Set<RequiredActionsExecutionStrategy>,
    private val sessionStorage: SessionStorage,
    private val clientRegistry: ClientRegistry,
    private val tenantConfig: TenantConfigService,
    private val execution: SessionExecution,
) : RequiredActionsRedirectHandler {
    override suspend fun handle(
        error: IdkError,
        sessionId: String,
        baseUrl: String,
    ): GenericHttpResponse? {
        // Typed extraction: IdkError.fromDTO preserves the original sealed AuthorizationServerError
        // subtype on `IdkError.source`, so we recover the typed payload (typed actionIds /
        // actionLabels / actionMetadata fields) instead of digging through `meta` magic-strings.
        // The wire-shape fallback below covers tests and any caller that constructs the IdkError
        // directly without going through fromDTO.
        val typed = error.sourceAs<AuthorizationServerError.RequiredActionsPending>()
        val unmet =
            if (typed != null) {
                reconstructActions(typed)
            } else {
                // Wire-shape fallback: dispatch off `code` + `meta` for IdkErrors built by hand
                // (mostly tests). If the code doesn't match it's not our error to handle.
                if (error.code != REQUIRED_ACTIONS_ERROR_CODE) return null
                reconstructActionsFromMeta(error.meta)
            }
        if (unmet.isEmpty()) return null

        val sessionResult = sessionStorage.getSession(sessionId)
        if (!sessionResult.isOk) return null
        val session = sessionResult.value ?: return null

        val authenticatedUserId = session.authenticatedUserId ?: return null
        val tenantId =
            runCatching { execution.tenantId }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != "anonymous" }
                ?: return null

        val client = lookupClient(session.clientId) ?: return null
        val strategyId = resolveStrategyId(client)
        val strategy = strategies.firstOrNull { it.id == strategyId } ?: return null

        // Round-trip `session_id` so the runner knows which AS callback to resume after the
        // execution completes. The runner is expected to issue a request to
        // `${baseUrl}/authorize/callback?session_id=…` once the IDV nodes finish.
        val callbackBaseUrl = "$baseUrl/authorize/callback?session_id=$sessionId"

        val started =
            strategy.start(
                unmet = unmet,
                tenantId = tenantId,
                subject =
                    IdvSubjectRef(
                        identityId = authenticatedUserId,
                        targetPartyType = PartyType.NATURAL_PERSON,
                    ),
                callbackBaseUrl = callbackBaseUrl,
                correlationId = sessionId,
            )
        if (!started.isOk) return null
        return renderNextStep(started.value, baseUrl, sessionId)
    }

    /** Typed-source path: read the action triplet straight off the sealed error subtype. */
    private fun reconstructActions(typed: AuthorizationServerError.RequiredActionsPending): List<RequiredAction> =
        typed.actionIds.mapIndexed { index, id ->
            RequiredAction(
                actionId = id,
                displayName = typed.actionLabels.getOrNull(index) ?: id,
                metadata = typed.actionMetadata.getOrNull(index) ?: emptyMap(),
            )
        }

    /**
     * Wire-shape fallback for IdkErrors that lost their typed source (e.g. constructed by
     * hand). The keys here MUST match what
     * [AuthorizationServerError.RequiredActionsPending] writes into `meta`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun reconstructActionsFromMeta(meta: Map<String, Any?>): List<RequiredAction> {
        val ids = (meta[META_ACTION_IDS] as? List<String>).orEmpty()
        val labels = (meta[META_ACTION_LABELS] as? List<String>).orEmpty()
        val metas = (meta[META_ACTION_METADATA] as? List<Map<String, String>>).orEmpty()
        return ids.mapIndexed { index, id ->
            RequiredAction(
                actionId = id,
                displayName = labels.getOrNull(index) ?: id,
                metadata = metas.getOrNull(index) ?: emptyMap(),
            )
        }
    }

    private suspend fun lookupClient(clientId: String): ClientRegistration? {
        val lookup = clientRegistry.getClient(clientId)
        if (!lookup.isOk) return null
        return lookup.value
    }

    /**
     * Per-client `additionalMetadata["required_actions.strategy"]` wins; otherwise
     * the tenant-config cascade picks up `oauth2.required_actions.strategy`;
     * otherwise the IDK ad-hoc default.
     */
    private fun resolveStrategyId(client: ClientRegistration): String {
        val perClient = client.additionalMetadata[CLIENT_META_STRATEGY]?.toString()?.takeIf { it.isNotBlank() }
        if (perClient != null) return perClient
        return tenantConfig
            .getProperty(STRATEGY_CONFIG_KEY, String::class)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_STRATEGY_ID
    }

    private fun renderNextStep(
        execution: IdvExecution,
        baseUrl: String,
        sessionId: String
    ): GenericHttpResponse? {
        val pending = execution.currentPendingActions.firstOrNull()?.action ?: return null
        val location =
            when (pending) {
                is RedirectAction -> {
                    pending.url
                }

                is UserInputAction -> {
                    // Strip any leading slash off the configured path; defensive against
                    // operators setting `/idv/run` or `idv/run` interchangeably.
                    val runnerPath =
                        (
                            tenantConfig.getProperty(RUNNER_PATH_CONFIG_KEY, String::class)
                                ?: DEFAULT_RUNNER_PATH
                        ).trimStart('/')
                    "${baseUrl.trimEnd('/')}/$runnerPath/${execution.executionId.value}?session_id=$sessionId"
                }

                else -> {
                    return null
                } // poll / wait-for-callback / completed are not directly user-routable
            }
        return GenericHttpResponse(
            statusCode = 302,
            headers =
                mapOf(
                    "Location" to location,
                    "Cache-Control" to "no-store",
                    "Pragma" to "no-cache",
                ),
            body = "",
        )
    }

    companion object {
        const val CLIENT_META_STRATEGY: String = "required_actions.strategy"
        const val STRATEGY_CONFIG_KEY: String = "oauth2.required_actions.strategy"
        const val RUNNER_PATH_CONFIG_KEY: String = "oauth2.required_actions.runner_path"
        const val DEFAULT_STRATEGY_ID: String = "adhoc"
        const val DEFAULT_RUNNER_PATH: String = "idv/run"

        // Wire-shape keys from AuthorizationServerError.RequiredActionsPending.code/meta.
        // Kept here (not re-imported) so this handler doesn't pull a hard dependency on the
        // sealed error class — any deployment that produces an IdkError with the same
        // discriminator can fire this orchestration path.
        internal const val REQUIRED_ACTIONS_ERROR_CODE: String = "interaction_required"
        internal const val META_ACTION_IDS: String = "required_action_ids"
        internal const val META_ACTION_LABELS: String = "required_action_labels"
        internal const val META_ACTION_METADATA: String = "required_action_metadata"
    }
}
