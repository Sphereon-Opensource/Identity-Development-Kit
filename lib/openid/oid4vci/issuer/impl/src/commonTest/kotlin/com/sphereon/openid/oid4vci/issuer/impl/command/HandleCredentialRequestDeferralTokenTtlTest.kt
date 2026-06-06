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

import com.sphereon.attribute.flow.AttributePath
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.command.BindingCompletenessVerdict
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessResult
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweDecryptionResult
import com.sphereon.crypto.jose.jwe.JweJsonFlattened
import com.sphereon.crypto.jose.jwe.JweJsonGeneral
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.jose.jwe.PreparedJwe
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenCommand
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenResult
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Exercises the §6.5 deferral-scoped token TTL resolution inside
 * [HandleCredentialRequestCommandImpl]:
 *
 * - When the operator sets `oid4vci.issuer.deferral-scoped-token.default-ttl-seconds`,
 *   that value is forwarded to [MintDeferralScopedTokenCommand] as `ttlSeconds`.
 * - When the property is unset, the issuer falls back to the 7-day default that matches
 *   `DeferralPolicy.maxDeferralSeconds`'s default — wallets polling for a 7-day deferral
 *   keep working out of the box.
 *
 * Per §6.5 the deferral-scoped token must outlive `maxDeferralSeconds`, NOT
 * `accessTokenLifetimeSeconds`, so this test pins the new behaviour against the previous
 * (rejected) `accessTokenLifetimeSeconds + 60s` shape.
 */
class HandleCredentialRequestDeferralTokenTtlTest {
    private val configId = "TestCredential"
    private val correlationId = "corr-1"

    // ------------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------------

    private class FakeAsBridge : Oid4vciAuthorizationServerBridge by NoOpAsBridge() {
        override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> =
            Ok(
                ValidatedTokenContext(
                    subject = "did:example:holder",
                    clientId = "test-client",
                    scope = null,
                    credentialConfigurationIds = listOf("TestCredential"),
                    cnfJkt = "jkt-thumbprint",
                ),
            )
    }

    private class CountingFormatHandler : CredentialFormatHandler {
        var issueCount = 0
        override val supportedFormat: String = CredentialFormat.SD_JWT_DC.value

        override suspend fun canHandle(
            request: CredentialRequest,
            configuration: CredentialConfigurationSupported,
        ): Boolean = true

        override suspend fun issueCredential(
            request: CredentialRequest,
            context: IssuanceContext,
        ): IdkResult<CredentialEnvelope, IdkError> {
            issueCount++
            return Ok(
                CredentialEnvelope(
                    credential = JsonPrimitive("issued-credential"),
                    format = supportedFormat,
                ),
            )
        }
    }

    private class NoOpAttributeContributor : CredentialAttributeContributor {
        override suspend fun contribute(
            session: IssuanceSession,
            tokenContext: ValidatedTokenContext,
            credentialConfigurationId: String,
        ): IdkResult<CredentialAttributeContribution, IdkError> = Ok(CredentialAttributeContribution(attributes = emptyMap()))
    }

    private class FakeConfigProvider : Oid4vciIssuerConfigProvider {
        override val issuerIdentifier: String = "https://test.example/oid4vci"
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
        override val authorizationServers: List<String>? = null
        override val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null
    }

    private class NoOpNonceStore : CredentialNonceStore {
        override suspend fun create(
            nonce: String,
            ttlSeconds: Long,
        ): IdkResult<NonceEntry, IdkError> = Ok(NonceEntry(nonce = nonce, createdAt = 0, expiresAt = ttlSeconds))

        override suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> = Ok(null)
    }

    /** [JweService] is wired into [CredentialResponseEncryptor] but never reached on the deferral path. */
    private object ThrowingJweService : JweService {
        override val commands: JweService.Commands get() = throw UnsupportedOperationException("not used")

        override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = throw UnsupportedOperationException("not used")
    }

    /** Force-deferral fixture: returns a single incomplete-but-deferrable verdict. */
    private class DeferringCompletenessCommand(
        private val bindingId: String,
    ) : EvaluateAttributeCompletenessCommand {
        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> =
            Ok(
                EvaluateAttributeCompletenessResult(
                    verdicts =
                        listOf(
                            BindingCompletenessVerdict(
                                bindingId = bindingId,
                                complete = false,
                                missingRequiredPaths = listOf(AttributePath("given_name")),
                                deferralRecommended = true,
                            ),
                        ),
                ),
            )

        override suspend fun supports(args: Any): Boolean = args is EvaluateAttributeCompletenessArgs
    }

    /** Captures every [MintDeferralScopedTokenArgs] passed to [execute] for post-test inspection. */
    private class CapturingMintCommand : MintDeferralScopedTokenCommand {
        val captured = mutableListOf<MintDeferralScopedTokenArgs>()

        override val inputTypeToken: TypeToken<MintDeferralScopedTokenArgs> = typeToken()
        override val outputTypeToken: TypeToken<MintDeferralScopedTokenResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: MintDeferralScopedTokenArgs): IdkResult<MintDeferralScopedTokenResult, IdkError> {
            captured += args
            return Ok(
                MintDeferralScopedTokenResult(
                    accessToken = "test-deferral-token",
                    expiresInSeconds = args.ttlSeconds,
                ),
            )
        }

        override suspend fun supports(args: Any): Boolean = args is MintDeferralScopedTokenArgs
    }

    /** AS provider whose default server has refresh tokens DISABLED so the fallback fires. */
    private class FakeAsConfigProvider(
        private val instance: OAuth2ServerInstanceConfig =
            OAuth2ServerInstanceConfig(
                accessTokenLifetimeSeconds = 3600,
                refreshTokenLifetimeSeconds = 0,
                grantTypesEnabled = setOf("authorization_code"),
            ),
    ) : OAuth2ServersConfigProvider {
        override fun getConfig(): OAuth2ServersConfig = OAuth2ServersConfig()

        override fun getServer(id: String): OAuth2ServerInstanceConfig = instance

        override fun getDefaultServer(): OAuth2ServerInstanceConfig = instance

        override fun resolveIssuer(
            serverId: String,
            tenantId: String,
        ): String = "https://test.example/oid4vci"
    }

    /** Minimal property resolver that only knows about a single typed key/value pair. */
    private class StaticPropertyResolver(
        private val key: String,
        private val value: Any?,
    ) : PropertyResolver {
        override fun containsProperty(key: String): Boolean = key == this.key && value != null

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = if (key == this.key) value as T? else defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = getProperty(key, targetType, defaultValue) ?: error("missing $key")

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = if (key == this.key) value?.toString() else defaultValue

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = getPropertyAsString(key, defaultValue) ?: error("missing $key")

        override fun getAllProperties(): Map<String, Any> = if (value != null) mapOf(key to value) else emptyMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = if (value != null) mapOf(key to value.toString()) else emptyMap()

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

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
        }

    private fun session(): IssuanceSession =
        IssuanceSession(
            sessionId = "session-1",
            issuerId = "https://test.example/oid4vci",
            credentialConfigurationIds = listOf(configId),
            status = IssuanceSessionStatus.CREDENTIAL_REQUESTED,
            pipelineCorrelationId = correlationId,
            createdAt = 0,
            expiresAt = Long.MAX_VALUE,
        )

    private fun command(
        sessionStore: RecordingSessionStore,
        deferredStore: RecordingDeferredStore,
        formatHandler: CountingFormatHandler,
        mintCommand: MintDeferralScopedTokenCommand,
        propertyResolver: PropertyResolver?,
    ): HandleCredentialRequestCommandImpl {
        val configProvider = FakeConfigProvider()
        return HandleCredentialRequestCommandImpl(
            execution = TestSessionExecution(),
            asBridge = FakeAsBridge(),
            proofVerifiers = emptySet<ProofVerifier>(),
            nonceManager = NonceManager(NoOpNonceStore()),
            formatHandlers = setOf(formatHandler),
            attributeContributor = NoOpAttributeContributor(),
            sessionStore = sessionStore,
            deferredStore = deferredStore,
            encryptor = CredentialResponseEncryptor(ThrowingJweService, configProvider),
            issuerConfigProvider = configProvider,
            propertyResolver = propertyResolver,
            evaluateAttributeCompletenessCommand = DeferringCompletenessCommand(configId),
            oauth2ConfigProvider = FakeAsConfigProvider(),
            mintDeferralScopedTokenCommand = mintCommand,
            clock = fixedClock(),
        )
    }

    private fun request(): HandleCredentialRequestArgs =
        HandleCredentialRequestArgs(
            accessToken = "test-access-token",
            credentialRequest =
                CredentialRequest(
                    credentialConfigurationId = configId,
                    format = CredentialFormat.SD_JWT_DC.value,
                ),
            credentialConfigurations =
                mapOf(
                    configId to
                        CredentialConfigurationSupported(
                            format = CredentialFormat.SD_JWT_DC.value,
                            vct = "https://test.example/vct",
                        ),
                ),
        )

    // ------------------------------------------------------------------------
    // (a) configured TTL is forwarded verbatim
    // ------------------------------------------------------------------------

    @Test
    fun configuredTtlIsForwardedToMintCommand() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val mint = CapturingMintCommand()
            val resolver =
                StaticPropertyResolver(
                    key = "oid4vci.issuer.deferral-scoped-token.default-ttl-seconds",
                    value = 600L,
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = CountingFormatHandler(),
                    mintCommand = mint,
                    propertyResolver = resolver,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok deferred response but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "deferred response must carry a transaction_id")
            assertEquals(1, mint.captured.size, "mint command must fire exactly once on the deferral path")
            val args = mint.captured.single()
            assertEquals(600L, args.ttlSeconds, "configured TTL must reach the mint command verbatim")
            assertEquals(correlationId, args.correlationId, "correlationId must come from the issuance session")
            assertEquals("jkt-thumbprint", args.cnfJkt, "DPoP thumbprint must thread through to the mint args")

            val additional = result.value.additionalParameters
            assertNotNull(additional, "deferred 202 must carry additional parameters when fallback fires")
            assertEquals(
                "test-deferral-token",
                additional["deferral_access_token"]?.jsonPrimitive?.content,
                "additional parameters must carry the minted token",
            )
            assertEquals(
                600L,
                additional["deferral_access_token_expires_in"]?.jsonPrimitive?.content?.toLong(),
                "additional parameters must echo the configured TTL",
            )
        }

    // ------------------------------------------------------------------------
    // (b) no config → 7-day default (matches DeferralPolicy.maxDeferralSeconds default)
    // ------------------------------------------------------------------------

    @Test
    fun unsetTtlFallsBackToSevenDayDefault() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val mint = CapturingMintCommand()

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = CountingFormatHandler(),
                    mintCommand = mint,
                    propertyResolver = null,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok deferred response but got Err: ${result.errorOrNull()}")
            assertEquals(1, mint.captured.size, "mint command must fire exactly once on the deferral path")
            val expected = 7L * 24 * 3600
            assertEquals(
                expected,
                mint.captured.single().ttlSeconds,
                "absent config must default to 7 days (DeferralPolicy.maxDeferralSeconds default)",
            )
        }

    // ------------------------------------------------------------------------
    // (c) resolver wired but key unset → also falls back to the default
    // ------------------------------------------------------------------------

    @Test
    fun missingKeyOnWiredResolverFallsBackToDefault() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val mint = CapturingMintCommand()
            val resolver =
                StaticPropertyResolver(
                    key = "some.other.key",
                    value = 42L,
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = CountingFormatHandler(),
                    mintCommand = mint,
                    propertyResolver = resolver,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok deferred response but got Err: ${result.errorOrNull()}")
            val expected = 7L * 24 * 3600
            assertEquals(
                expected,
                mint.captured.single().ttlSeconds,
                "missing key must default to 7 days, ignoring unrelated resolver entries",
            )
        }
}
