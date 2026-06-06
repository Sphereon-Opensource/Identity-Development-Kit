/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.CredentialClaimsBinding
import com.sphereon.credential.issuance.pipeline.DeferralPolicy
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.SemanticAttributeSetRef
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionResult
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.config.DeferralWalletAuthValidator
import com.sphereon.openid.oid4vci.issuer.impl.pipeline.OfferPipelineInitializer
import com.sphereon.openid.oid4vci.issuer.pipeline.PipelineConfigurationResolver
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §6.5 wallet-auth invariant: fail-fast at offer creation.
 *
 * The validator runs the moment a pipeline configuration carrying [DeferralPolicy]-bearing
 * [CredentialClaimsBinding]s is resolved for an offer; misconfiguration must throw before the
 * issuance session is persisted, so wallets never see an offer they could not complete.
 *
 * Invariant matrix (deferral exceeds AS access-token lifetime):
 *
 * | refresh_token | refreshTokenLifetime | fallback enabled | fallback TTL | verdict |
 * |---|---|---|---|---|
 * | enabled | >= max | * | * | OK |
 * | enabled | < max | false | unset | FAIL |
 * | disabled | * | true | >= max | OK |
 * | disabled | * | true | < max | FAIL |
 * | disabled | * | true | unset | FAIL |
 * | disabled | * | false | * | FAIL |
 *
 * These tests exercise the offer-creation entry point; the pure-function verdict matrix lives
 * in `lib-openid-oid4vci-issuer-public`'s `DeferralWalletAuthValidatorTest`. We assert the
 * happy path stays Ok (offer created), the failing paths surface as `IllegalStateException`
 * with messages naming the misconfigured binding, and the no-OAuth2 + no-deferrable-pipeline
 * paths leave the existing pure-IDK behaviour byte-for-behavior unchanged.
 */
class CreateCredentialOfferDeferralWalletAuthInvariantTest {
    private val issuerId = "https://issuer.example.com/oid4vci"
    private val twoDayDeferral = 2L * 24 * 3600
    private val accessTokenLifetimeSeconds = 3600

    // ─────────────────────────────────────────────────────────────────────────
    // Fakes
    // ─────────────────────────────────────────────────────────────────────────

    private class FixedPipelineConfigurationResolver(
        private val config: PipelineConfiguration?,
    ) : PipelineConfigurationResolver {
        override suspend fun resolve(
            issuerId: String,
            credentialConfigurationIds: List<String>,
        ): IdkResult<PipelineConfiguration?, IdkError> = Ok(config)
    }

    private class RecordingInitPipelineSessionCommand(
        private val resultSessionId: String = "ps-1",
        private val resultCorrelationId: String = "corr-1",
    ) : InitPipelineSessionCommand {
        override val commandId: String get() = InitPipelineSessionCommand.COMMAND_ID
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<InitPipelineSessionArgs> = typeToken()
        override val outputTypeToken: TypeToken<InitPipelineSessionResult> = typeToken()

        var invocations: Int = 0

        override suspend fun supports(args: Any): Boolean = args is InitPipelineSessionArgs

        override suspend fun execute(args: InitPipelineSessionArgs): IdkResult<InitPipelineSessionResult, IdkError> {
            invocations += 1
            return Ok(InitPipelineSessionResult(sessionId = resultSessionId, correlationId = resultCorrelationId))
        }
    }

    private class FixedAsConfigProvider(
        private val instance: OAuth2ServerInstanceConfig,
    ) : OAuth2ServersConfigProvider {
        override fun getConfig(): OAuth2ServersConfig = OAuth2ServersConfig()

        override fun getServer(id: String): OAuth2ServerInstanceConfig = instance

        override fun getDefaultServer(): OAuth2ServerInstanceConfig = instance

        override fun resolveIssuer(
            serverId: String,
            tenantId: String,
        ): String = "https://issuer.example.com/oid4vci"
    }

    /** Supplies the two §6.5 keys (fallback-enabled + TTL) and nothing else. */
    private class StaticPropertyResolver(
        private val values: Map<String, Any?>,
    ) : PropertyResolver {
        override fun containsProperty(key: String): Boolean = values[key] != null

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = (values[key] as T?) ?: defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = getProperty(key, targetType, defaultValue) ?: error("missing $key")

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = values[key]?.toString() ?: defaultValue

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = getPropertyAsString(key, defaultValue) ?: error("missing $key")

        override fun getAllProperties(): Map<String, Any> = values.entries.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = values.entries.mapNotNull { (k, v) -> v?.let { k to it.toString() } }.toMap()

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = emptyMap()

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = emptyMap()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun sampleArgs() =
        CreateCredentialOfferArgs(
            issuerId = issuerId,
            credentialConfigurationIds = listOf("PID"),
            preAuthorizedCodeGrant = false,
            authorizationCodeGrant = false,
        )

    private fun pipelineWith(
        bindingId: String,
        policy: DeferralPolicy,
    ): PipelineConfiguration =
        PipelineConfiguration(
            pipelineId = "test-pipeline",
            claimsBindings =
                listOf(
                    CredentialClaimsBinding(
                        id = bindingId,
                        semanticAttributeSetRef = SemanticAttributeSetRef(bundleId = "test"),
                        deferralPolicy = policy,
                    ),
                ),
        )

    private fun asConfig(
        grantTypes: Set<String> = setOf("authorization_code"),
        refreshTokenLifetimeSeconds: Int = 0,
        accessTokenLifetime: Int = accessTokenLifetimeSeconds,
    ): OAuth2ServerInstanceConfig =
        OAuth2ServerInstanceConfig(
            accessTokenLifetimeSeconds = accessTokenLifetime,
            refreshTokenLifetimeSeconds = refreshTokenLifetimeSeconds,
            grantTypesEnabled = grantTypes,
        )

    private fun fallbackProperties(
        enabled: Boolean,
        ttlSeconds: Long?,
    ): PropertyResolver =
        StaticPropertyResolver(
            buildMap {
                put(DeferralWalletAuthValidator.CONFIG_KEY_DEFERRAL_TOKEN_FALLBACK_ENABLED, enabled)
                if (ttlSeconds != null) {
                    put(DeferralWalletAuthValidator.CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS, ttlSeconds)
                }
            },
        )

    private fun command(
        sessionStore: RecordingSessionStore = RecordingSessionStore(),
        pipelineConfig: PipelineConfiguration?,
        oauth2: OAuth2ServersConfigProvider? = null,
        properties: PropertyResolver? = null,
        initCmd: RecordingInitPipelineSessionCommand = RecordingInitPipelineSessionCommand(),
    ): Pair<CreateCredentialOfferCommandImpl, RecordingInitPipelineSessionCommand> =
        CreateCredentialOfferCommandImpl(
            execution = TestSessionExecution(),
            asBridge = NoOpAsBridge(),
            offerStore = NoOpOfferStore(),
            sessionStore = sessionStore,
            pipelineInitializer =
                OfferPipelineInitializer(
                    pipelineConfigurationResolver = FixedPipelineConfigurationResolver(pipelineConfig),
                    initPipelineSessionCommand = initCmd,
                    oauth2ConfigProvider = oauth2,
                    propertyResolver = properties,
                ),
        ) to initCmd

    // ─────────────────────────────────────────────────────────────────────────
    // Happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun deferrablePolicyWithSufficientRefreshTokenLifetimePassesValidation() =
        runTest {
            // Refresh tokens enabled and lifetime covers the deferral window, so the validator
            // must accept this as a valid remediation and the offer must be created.
            val (cmd, initCmd) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 =
                        FixedAsConfigProvider(
                            asConfig(
                                grantTypes = setOf("authorization_code", "refresh_token"),
                                refreshTokenLifetimeSeconds = (twoDayDeferral + 1000).toInt(),
                            ),
                        ),
                    properties = fallbackProperties(enabled = false, ttlSeconds = null),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk, "offer creation must succeed when refresh tokens cover the deferral window")
            assertEquals(1, initCmd.invocations, "pipeline session init must run after the validator passes")
        }

    @Test
    fun deferrablePolicyWithFallbackEnabledAndSufficientTtlPassesValidation() =
        runTest {
            // The deferral-scoped-token fallback is enabled AND the TTL covers the deferral
            // window, so the validator must accept this as a valid remediation.
            val (cmd, initCmd) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 = FixedAsConfigProvider(asConfig()),
                    properties = fallbackProperties(enabled = true, ttlSeconds = twoDayDeferral),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk, "offer creation must succeed when fallback TTL covers the deferral window")
            assertEquals(1, initCmd.invocations)
        }

    @Test
    fun disabledDeferralPolicyAlwaysPassesEvenWithoutAnyRemediation() =
        runTest {
            // A disabled deferral policy never engages the §6.5 invariant: no remediation required.
            val (cmd, _) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = false, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 = FixedAsConfigProvider(asConfig()),
                    properties = fallbackProperties(enabled = false, ttlSeconds = null),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk, "disabled deferral policy must not trip the invariant")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Fail-fast paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun deferrablePolicyExceedingAccessTokenWithoutRemediationFailsFast() =
        runTest {
            // maxDeferralSeconds outlives the access token AND no refresh / no fallback,
            // so operator misconfiguration must throw before the offer is persisted.
            val (cmd, initCmd) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 = FixedAsConfigProvider(asConfig()),
                    properties = fallbackProperties(enabled = false, ttlSeconds = null),
                )

            val ex = assertFailsWith<IllegalStateException> { cmd.execute(sampleArgs()) }
            assertTrue(
                ex.message?.contains("PID") == true,
                "fail-fast message must name the misconfigured credential binding id, got: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("maxDeferralSeconds=$twoDayDeferral") == true,
                "fail-fast message must report maxDeferralSeconds, got: ${ex.message}",
            )
            assertEquals(0, initCmd.invocations, "pipeline session init must NOT run after the validator throws")
        }

    @Test
    fun deferrablePolicyWithFallbackEnabledButShortTtlFailsFast() =
        runTest {
            // Fallback opted in but the configured TTL is shorter than the deferral window,
            // so the minted token expires before the wallet's last poll. Must throw at offer time.
            val shortTtl = twoDayDeferral - 1000L
            val (cmd, initCmd) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 = FixedAsConfigProvider(asConfig()),
                    properties = fallbackProperties(enabled = true, ttlSeconds = shortTtl),
                )

            val ex = assertFailsWith<IllegalStateException> { cmd.execute(sampleArgs()) }
            assertNotNull(ex.message)
            assertTrue(
                ex.message!!.contains("PID"),
                "fail-fast message must name the misconfigured credential binding id, got: ${ex.message}",
            )
            assertTrue(
                ex.message!!.contains(shortTtl.toString()),
                "fail-fast message must report the configured TTL, got: ${ex.message}",
            )
            assertTrue(
                ex.message!!.contains(DeferralWalletAuthValidator.CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS),
                "fail-fast message must point at the TTL config key, got: ${ex.message}",
            )
            assertEquals(0, initCmd.invocations)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure-IDK / graceful-skip paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun noOauth2ProviderSkipsValidationEntirely() =
        runTest {
            // Pure-IDK deployment without OAuth2 wired. There is no token lifetime to reason
            // about, so the validator must be skipped; even a degenerately-misconfigured
            // policy passes through (the deployment will surface the issue elsewhere if it
            // ever wires an AS).
            val (cmd, initCmd) =
                command(
                    pipelineConfig =
                        pipelineWith(
                            bindingId = "PID",
                            policy = DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral),
                        ),
                    oauth2 = null,
                    properties = null,
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk, "no oauth2 wired must skip validation entirely (pure-IDK no-pipeline path)")
            assertEquals(1, initCmd.invocations)
        }

    @Test
    fun nullPipelineConfigSkipsValidationAndInit() =
        runTest {
            // Resolver returns no pipeline at all (no claimsBindings to validate). The §6.5
            // path is a degenerate no-op, the offer is still created, and pipeline init is
            // skipped; behaviour matches the existing no-pipeline coverage.
            val (cmd, initCmd) =
                command(
                    pipelineConfig = null,
                    oauth2 = FixedAsConfigProvider(asConfig()),
                    properties = fallbackProperties(enabled = false, ttlSeconds = null),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertEquals(0, initCmd.invocations, "no resolved pipeline must skip both validation and init")
        }
}
