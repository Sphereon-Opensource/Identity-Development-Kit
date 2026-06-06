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

package com.sphereon.openid.oid4vci.issuer.impl.pipeline

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
import com.sphereon.openid.oid4vci.issuer.config.DeferralWalletAuthValidator
import com.sphereon.openid.oid4vci.issuer.pipeline.PipelineConfigurationResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Pre-flight checks and pipeline-session bootstrap for credential offer creation.
 *
 * Coordinates the concerns that fire before an issuance session is persisted:
 *
 * 1. [validateGrants] — reject offers whose requested grants are not permitted by per-credential
 *    policy ([CredentialIssuancePolicyResolver]). Skipped when no resolver is wired.
 * 2. [initializePipeline] — resolve a pipeline configuration ([PipelineConfigurationResolver]),
 *    enforce the §6.5 wallet-auth invariant ([DeferralWalletAuthValidator]), and initialise a
 *    pipeline session ([InitPipelineSessionCommand]). Returns the resulting correlation ID or
 *    `null` when no pipeline is wired or none resolves for this offer.
 *
 * Pure-IDK / no-pipeline deployments leave the optional collaborators null, in which case the
 * preflight is a graceful no-op (`Ok(Unit)` validation, `null` pipeline correlation) matching the
 * prior inlined behaviour.
 *
 * Extracted from [com.sphereon.openid.oid4vci.issuer.impl.command.CreateCredentialOfferCommandImpl]
 * to keep that command's constructor inside the detekt `LongParameterList` threshold while
 * preserving Metro DI: each collaborator is still individually injected on this helper, so the
 * graph composition is unchanged.
 */
@Inject
@SingleIn(SessionScope::class)
class OfferPipelineInitializer(
    private val policyResolver: CredentialIssuancePolicyResolver? = null,
    private val pipelineConfigurationResolver: PipelineConfigurationResolver? = null,
    private val initPipelineSessionCommand: InitPipelineSessionCommand? = null,
    private val oauth2ConfigProvider: OAuth2ServersConfigProvider? = null,
    private val propertyResolver: PropertyResolver? = null,
) {
    /**
     * Validate the requested grants against per-credential policy (most restrictive across all
     * configs). Skipped when no policy resolver is wired — pure-IDK / no-policy deployments
     * accept any grant the caller asks for.
     */
    suspend fun validateGrants(args: CreateCredentialOfferArgs): IdkResult<Unit, IdkError> {
        val resolver = policyResolver ?: return Ok(Unit)
        for (configId in args.credentialConfigurationIds) {
            val violation = findGrantPolicyViolation(args, configId, resolver.resolve(configId))
            if (violation != null) {
                return Err(violation)
            }
        }
        return Ok(Unit)
    }

    /**
     * Returns the policy violation for the given credential configuration when the requested
     * grants are not permitted; `null` when both requested grants are accepted.
     */
    private fun findGrantPolicyViolation(
        args: CreateCredentialOfferArgs,
        configId: String,
        policy: CredentialIssuancePolicyConfig,
    ): IdkError? {
        if (args.preAuthorizedCodeGrant && !policy.preAuthorizedCodeAllowed) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Pre-authorized code grant is not allowed for credential configuration '$configId'",
            )
        }
        if (args.authorizationCodeGrant && !policy.authorizationCodeAllowed) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Authorization code grant is not allowed for credential configuration '$configId'",
            )
        }
        return null
    }

    /**
     * Resolve a pipeline configuration for the offer, validate the §6.5 wallet-auth invariant,
     * and initialise a pipeline session. Returns the resulting correlation ID or `null` when no
     * pipeline is wired or none resolves for this offer.
     *
     * @throws IllegalStateException when the wallet-auth invariant fails for the resolved
     *   pipeline configuration — fail-fast before the issuance session is persisted.
     */
    suspend fun initializePipeline(args: CreateCredentialOfferArgs): String? {
        val pipelineConfig = resolvePipelineConfiguration(args) ?: return null
        validateDeferralWalletAuthInvariant(pipelineConfig)
        return initPipelineSessionCommand
            ?.execute(
                InitPipelineSessionArgs(
                    pipelineConfiguration = pipelineConfig,
                    correlationId = null,
                    initialAttributes = emptyList(),
                    initialLookupKeys = args.initialLookupKeys,
                ),
            )?.getOrNull()
            ?.correlationId
    }

    private suspend fun resolvePipelineConfiguration(args: CreateCredentialOfferArgs): PipelineConfiguration? =
        pipelineConfigurationResolver
            ?.resolve(args.issuerId, args.credentialConfigurationIds)
            ?.getOrNull()

    /**
     * §6.5 wallet-auth invariant fail-fast. Skipped entirely when [oauth2ConfigProvider]
     * is null (pure-IDK deployment without OAuth2 wired): there is no access-token
     * lifetime to reason about, so the invariant is vacuously satisfied. The fallback /
     * TTL config keys are read via [propertyResolver] when available; absent, both default
     * to "fallback off / TTL unset", matching the runtime behaviour of
     * [com.sphereon.openid.oid4vci.issuer.impl.command.HandleCredentialRequestCommandImpl]
     * when the operator has not opted in.
     */
    private fun validateDeferralWalletAuthInvariant(pipelineConfig: PipelineConfiguration) {
        val asConfig = oauth2ConfigProvider?.serverConfig ?: return
        val fallbackEnabled =
            propertyResolver
                ?.getProperty(
                    DeferralWalletAuthValidator.CONFIG_KEY_DEFERRAL_TOKEN_FALLBACK_ENABLED,
                    Boolean::class,
                ) ?: false
        val fallbackTtlSeconds =
            propertyResolver?.getProperty(
                DeferralWalletAuthValidator.CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS,
                Long::class,
            )
        val verdict =
            DeferralWalletAuthValidator.validate(
                claimsBindings = pipelineConfig.claimsBindings,
                asConfig = asConfig,
                deferralScopedTokenFallbackEnabled = fallbackEnabled,
                deferralScopedTokenTtlSeconds = fallbackTtlSeconds,
            )
        if (verdict.isErr) {
            // Fail-fast at offer creation. The validator's message names the offending
            // binding id, the policy field that is misconfigured, and what to fix
            // (raise the TTL, enable refresh tokens, or wire the fallback).
            error(verdict.error.message.defaultMessage)
        }
    }
}
