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

import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.impl.mapper.Oid4vciDesignMapper
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SdPolicy
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import kotlinx.serialization.json.JsonElement
import com.sphereon.data.store.credential.design.model.SdPolicy as DesignSdPolicy

/**
 * File-private helper that owns the Task 7.3 pipeline re-execution flow on
 * `/deferred_credential`. Pulled out of [HandleDeferredCredentialRequestCommandImpl] so the
 * command class stays at a single concern (status routing + access-token validation + entry
 * lifecycle) while the multi-step re-execution machinery sits in its own testable unit.
 *
 * Instantiated only when the EDK pipeline commands ([ContributeAttributesCommand] +
 * [EvaluateAttributeCompletenessCommand]) are wired in; the command's `buildReExecutor`
 * gates that decision and passes `null` in pure-IDK deployments.
 */
class DeferredPipelineReExecutor(
    private val deferredStore: DeferredCredentialStore,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val attributeContributor: CredentialAttributeContributor,
    private val contributeAttributesCommand: ContributeAttributesCommand,
    private val evaluateAttributeCompletenessCommand: EvaluateAttributeCompletenessCommand,
    private val issuerConfigProvider: Oid4vciIssuerConfigProvider,
    private val formatHandlers: Set<CredentialFormatHandler>,
    private val credentialDesignService: CredentialDesignService?,
    private val tenantIdProvider: () -> String?,
) {
    /**
     * Run the DEFERRED pipeline phase, re-check completeness, and if every binding is complete
     * and not awaiting approval dispatch the matching [CredentialFormatHandler]. Returns:
     * - `null` when the session has no pipeline binding, or any pipeline step / format-handler
     *   step short-circuits; the caller falls back to the 202 response
     * - `Ok(CredentialResponse)` carrying the credential when re-execution succeeds
     *
     * Re-execution failures (ContributeAttributesCommand returning Err, format handler erroring,
     * the format handler signalling `deferred = true` again) are intentionally swallowed so a
     * transient source-side issue keeps the deferred poll alive.
     */
    suspend fun attempt(
        entry: DeferredCredentialEntry,
        tokenContext: ValidatedTokenContext,
    ): IdkResult<CredentialResponse, IdkError>? {
        val session = sessionStore.get(entry.issuanceSessionId).getOrElse { null }
        val correlationId = session?.pipelineCorrelationId
        return when {
            session == null || correlationId == null -> null
            !runDeferredPhaseAndPipelineIsReady(correlationId) -> null
            else -> issueCredential(entry, tokenContext, session)
        }
    }

    private suspend fun runDeferredPhaseAndPipelineIsReady(correlationId: String): Boolean {
        val contributeResult =
            contributeAttributesCommand.execute(
                ContributeAttributesArgs(
                    correlationId = correlationId,
                    phase = Oid4vciPipelinePhase.DEFERRED,
                ),
            )
        if (contributeResult.isErr) {
            return false
        }
        val verdictsResult =
            evaluateAttributeCompletenessCommand.execute(
                EvaluateAttributeCompletenessArgs(correlationId = correlationId),
            )
        if (verdictsResult.isErr) {
            return false
        }
        return verdictsResult.value.verdicts.none { !it.complete || it.awaitingApproval }
    }

    private suspend fun issueCredential(
        entry: DeferredCredentialEntry,
        tokenContext: ValidatedTokenContext,
        session: IssuanceSession,
    ): IdkResult<CredentialResponse, IdkError>? {
        val inputs = prepareDispatchInputs(entry, tokenContext, session) ?: return null
        val issuanceContext = buildIssuanceContext(inputs, tokenContext)
        return runDispatch(entry, inputs, issuanceContext)
    }

    private suspend fun prepareDispatchInputs(
        entry: DeferredCredentialEntry,
        tokenContext: ValidatedTokenContext,
        session: IssuanceSession,
    ): DispatchInputs? {
        val configId = entry.credentialConfigurationId
        val configuration = issuerConfigProvider.credentialConfigurations[configId]
        val mergedAttributes = configuration?.let { mergeAttributes(session, tokenContext, configId) }
        if (configuration == null || mergedAttributes == null) {
            return null
        }
        val request = CredentialRequest(credentialConfigurationId = configId, format = configuration.format)
        val handler = formatHandlers.firstOrNull { it.canHandle(request, configuration) } ?: return null
        return DispatchInputs(configId, configuration, mergedAttributes, request, handler)
    }

    /**
     * Invoke the resolved format handler, persist the produced credential onto the entry as
     * READY, and return the credential response.
     *
     * Task 7.3 contract: write the produced credential onto the entry as READY. The wallet call
     * that triggered re-execution already returns the credential, but persisting READY (rather
     * than DELIVERED) keeps the existing READY -> DELIVERED transition the single canonical
     * place that marks delivery if the wallet polls again.
     */
    private suspend fun runDispatch(
        entry: DeferredCredentialEntry,
        inputs: DispatchInputs,
        issuanceContext: IssuanceContext,
    ): IdkResult<CredentialResponse, IdkError>? {
        val envelope =
            inputs.handler
                .issueCredential(inputs.request, issuanceContext)
                .getOrElse { return null }
                .takeUnless { it.deferred } ?: return null
        val persisted =
            deferredStore.update(
                entry.copy(
                    status = DeferredCredentialStatus.READY,
                    credentialResponse = envelope.credential,
                    notificationId = envelope.notificationId ?: entry.notificationId,
                ),
            )
        return if (persisted.isErr) {
            null
        } else {
            Ok(
                CredentialResponse(
                    credentials = listOf(CredentialResponseItem(credential = envelope.credential)),
                    notificationId = envelope.notificationId,
                ),
            )
        }
    }

    /**
     * Merge the session-side attribute bag with the contributor's final-phase contributions.
     * Priority mirrors [HandleCredentialRequestCommandImpl]: preSeeded → accumulated → contributed.
     * Returns `null` if the contributor errors so the caller falls back to the 202 path.
     */
    private suspend fun mergeAttributes(
        session: IssuanceSession,
        tokenContext: ValidatedTokenContext,
        configId: String,
    ): Map<String, JsonElement>? {
        val merged = mutableMapOf<String, JsonElement>()
        session.preSeededAttributes?.let { merged.putAll(it) }
        session.accumulatedAttributes?.let { merged.putAll(it) }
        val contributed =
            attributeContributor
                .contribute(session, tokenContext, configId)
                .getOrElse { return null }
        merged.putAll(contributed.attributes)
        return merged
    }

    private suspend fun buildIssuanceContext(
        inputs: DispatchInputs,
        tokenContext: ValidatedTokenContext,
    ): IssuanceContext {
        val signingConfig = issuerConfigProvider.credentialSigningConfigs[inputs.configId]
        val designContext = resolveDesignContext(inputs.configId)
        return IssuanceContext(
            subject = tokenContext.subject,
            clientId = tokenContext.clientId,
            issuerIdentifier = issuerConfigProvider.issuerIdentifier,
            credentialConfigurationId = inputs.configId,
            credentialConfiguration = inputs.configuration,
            // Holder binding key is not stored on the deferred entry; the format handlers
            // all tolerate a null binding (cnf claim is then omitted) which is the right
            // behaviour for re-execution that didn't see a fresh proof.
            holderBindingKey = null,
            attributes = inputs.attributes,
            sdPolicies = designContext.sdPolicies,
            mandatoryClaims = designContext.mandatoryClaims,
            signingKeyAlias = signingConfig?.signingKeyAlias,
            signingKeyMode = signingConfig?.signingKeyMode ?: SigningKeyMode.None,
            signingCertChainPath = signingConfig?.signingCertChainPath,
            issuanceClockSkewInSeconds = issuerConfigProvider.issuanceClockSkewInSeconds,
            expirationInDays = signingConfig?.expirationInDays,
        )
    }

    /**
     * Resolve `sdPolicies` + `mandatoryClaims` from the credential-design store. When no design
     * service is wired in or the design lookup fails the returned context is empty, mirroring
     * the default [IssuanceContext] values [HandleCredentialRequestCommandImpl] uses for the
     * same fallback path.
     */
    private suspend fun resolveDesignContext(configId: String): DesignContext {
        val service = credentialDesignService
        val tenantId = service?.let { tenantIdProvider() }
        val resolved = tenantId?.let { resolveDesign(service, it, configId) }
        return resolved?.let { mapDesignToContext(it.design.claims) } ?: DesignContext.EMPTY
    }

    private suspend fun resolveDesign(
        service: CredentialDesignService,
        tenantId: String,
        configId: String,
    ): com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign? {
        val designs =
            service
                .findCredentialDesignByBindingKey(
                    tenantId,
                    DesignBindingKey.CREDENTIAL_CONFIGURATION_ID,
                    configId,
                ).getOrElse { emptyList() }
        return designs.firstOrNull()?.let { record ->
            service
                .resolveCredentialDesign(
                    tenantId,
                    ResolveCredentialDesignInput(designId = record.id),
                ).getOrNull()
        }
    }

    private fun mapDesignToContext(claims: List<ClaimPresentation>): DesignContext {
        val sdPolicies: Map<String, SdPolicy> =
            claims.associate { claim ->
                val pathStr = Oid4vciDesignMapper.claimPathString(claim)
                val issuerPolicy =
                    when (claim.sdPolicy) {
                        DesignSdPolicy.ALWAYS -> SdPolicy.ALWAYS_DISCLOSED
                        DesignSdPolicy.ALLOWED -> SdPolicy.SELECTIVELY_DISCLOSABLE
                        DesignSdPolicy.NEVER -> SdPolicy.NEVER_DISCLOSED
                    }
                pathStr to issuerPolicy
            }
        val mandatoryClaims: Set<String> =
            claims
                .filter { it.mandatory }
                .map { Oid4vciDesignMapper.claimPathString(it) }
                .toSet()
        return DesignContext(sdPolicies, mandatoryClaims)
    }

    private data class DispatchInputs(
        val configId: String,
        val configuration: CredentialConfigurationSupported,
        val attributes: Map<String, JsonElement>,
        val request: CredentialRequest,
        val handler: CredentialFormatHandler,
    )

    private data class DesignContext(
        val sdPolicies: Map<String, SdPolicy>,
        val mandatoryClaims: Set<String>,
    ) {
        companion object {
            val EMPTY = DesignContext(emptyMap(), emptySet())
        }
    }
}
