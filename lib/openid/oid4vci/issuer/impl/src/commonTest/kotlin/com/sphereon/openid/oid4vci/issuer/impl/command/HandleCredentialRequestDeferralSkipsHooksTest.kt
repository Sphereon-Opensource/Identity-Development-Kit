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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceCommandRegistry
import com.sphereon.core.api.service.SessionScopedCommandRegistry
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
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.hook.PostIssuanceHookArgs
import com.sphereon.openid.oid4vci.issuer.hook.PostIssuanceHookResult
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Pins the §8.3.4 deferral predicate inside [HandleCredentialRequestCommandImpl.doExecute]:
 * post-issuance hooks must NOT fire on a 202 deferral envelope (transactionId set, credentials
 * null). No credential exists yet on a deferral, so notification/webhook hooks have nothing to
 * notify on. The eventual issuance fires its own post-issuance hooks from the
 * `/deferred_credential` poll path.
 *
 * Mirrors the predicate guarded by [HandleCredentialRequestEventEmissionTest] for event
 * emission. Same family of bug as Follow-up H.
 */
class HandleCredentialRequestDeferralSkipsHooksTest {
    private val configId = "TestCredential"

    // ------------------------------------------------------------------------
    // Fakes (mirrors the fixture in HandleCredentialRequestEventEmissionTest).
    // ------------------------------------------------------------------------

    private class FakeAsBridge : Oid4vciAuthorizationServerBridge by NoOpAsBridge() {
        override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> =
            Ok(
                ValidatedTokenContext(
                    subject = "did:example:holder",
                    clientId = "test-client",
                    scope = null,
                    credentialConfigurationIds = listOf("TestCredential"),
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

    /** [JweService] is wired into [CredentialResponseEncryptor] but never reached on these paths. */
    private object ThrowingJweService : JweService {
        override val commands: JweService.Commands get() = throw UnsupportedOperationException("not used")

        override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = throw UnsupportedOperationException("not used")
    }

    private class FakeCompletenessCommand(
        private val verdicts: List<BindingCompletenessVerdict>,
    ) : EvaluateAttributeCompletenessCommand {
        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> = Ok(EvaluateAttributeCompletenessResult(verdicts = verdicts))

        override suspend fun supports(args: Any): Boolean = args is EvaluateAttributeCompletenessArgs
    }

    /**
     * Records every hook command the dispatcher would have invoked. Since the dispatcher always
     * calls [ServiceCommandRegistry.listCommandIds] when it runs at all, a `null` last-resolution
     * snapshot proves the dispatcher was never instantiated. The recorded `dispatched` ids prove
     * which hooks actually ran when it was.
     */
    private class RecordingHook(
        override val commandId: String,
    ) : ServiceCommand<PostIssuanceHookArgs, PostIssuanceHookResult, IdkError> {
        var invocations: Int = 0
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<PostIssuanceHookArgs> = typeToken()
        override val outputTypeToken: TypeToken<PostIssuanceHookResult> = typeToken()

        override suspend fun supports(args: Any): Boolean = args is PostIssuanceHookArgs

        override suspend fun execute(args: PostIssuanceHookArgs,): IdkResult<PostIssuanceHookResult, IdkError> {
            invocations += 1
            return Ok(PostIssuanceHookResult(handled = true))
        }
    }

    /** Counts every `listCommandIds()` invocation so the test can assert the dispatcher ran. */
    private class CountingServiceCommandRegistry(
        private val commandIds: List<String>,
    ) : ServiceCommandRegistry {
        var listCalls: Int = 0

        override fun has(commandId: String): Boolean = commandId in commandIds

        override fun listCommandIds(): List<String> {
            listCalls += 1
            return commandIds
        }
    }

    private class FakeSessionRegistry(
        vararg commands: ServiceCommand<*, *, *>,
    ) : SessionScopedCommandRegistry {
        private val byId: Map<String, ServiceCommand<*, *, *>> = commands.associateBy { it.commandId }

        override fun get(commandId: String): ServiceCommand<*, *, *>? = byId[commandId]

        override fun listCommandIds(): List<String> = byId.keys.toList()
    }

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
        }

    private fun session(pipelineCorrelationId: String?): IssuanceSession =
        IssuanceSession(
            sessionId = "session-1",
            issuerId = "https://test.example/oid4vci",
            credentialConfigurationIds = listOf(configId),
            status = IssuanceSessionStatus.CREDENTIAL_REQUESTED,
            pipelineCorrelationId = pipelineCorrelationId,
            createdAt = 0,
            expiresAt = Long.MAX_VALUE,
        )

    private fun command(
        sessionStore: RecordingSessionStore,
        deferredStore: RecordingDeferredStore,
        formatHandler: CountingFormatHandler,
        completenessCommand: EvaluateAttributeCompletenessCommand?,
        serviceCommandRegistry: ServiceCommandRegistry,
        sessionScopedCommandRegistry: SessionScopedCommandRegistry,
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
            serviceCommandRegistry = serviceCommandRegistry,
            sessionScopedCommandRegistry = sessionScopedCommandRegistry,
            evaluateAttributeCompletenessCommand = completenessCommand,
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
    // (a) Deferral envelope: post-issuance hooks MUST NOT fire.
    // ------------------------------------------------------------------------

    @Test
    fun deferralEnvelopeSkipsPostIssuanceHooks() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val redemptionHook = RecordingHook("hook.post-issuance.redemption-consume")
            val webhook = RecordingHook("hook.post-issuance.webhook-notify")
            val discovery = CountingServiceCommandRegistry(listOf(redemptionHook.commandId, webhook.commandId))
            val sessionCommands = FakeSessionRegistry(redemptionHook, webhook)
            // Force the §6.1 completeness gate to defer: incomplete + deferralRecommended.
            val completeness =
                FakeCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = false,
                            missingRequiredPaths = listOf(AttributePath("given_name")),
                            deferralRecommended = true,
                        ),
                    ),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                    completenessCommand = completeness,
                    serviceCommandRegistry = discovery,
                    sessionScopedCommandRegistry = sessionCommands,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok deferred envelope but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "deferred response must carry a transaction_id")
            assertNull(result.value.credentials, "deferred response must NOT carry credentials")
            assertEquals(0, formatHandler.issueCount, "format handler must not run on the deferral path")

            // The whole point of the fix: the dispatcher must never even be instantiated for a 202.
            assertEquals(
                0,
                discovery.listCalls,
                "dispatcher must NOT inspect the registry when the response is a 202 deferral envelope",
            )
            assertEquals(0, redemptionHook.invocations, "redemption hook must not fire on deferral")
            assertEquals(0, webhook.invocations, "webhook hook must not fire on deferral")
        }

    // ------------------------------------------------------------------------
    // (b) Synchronous issuance still dispatches post-issuance hooks (regression guard).
    // ------------------------------------------------------------------------

    @Test
    fun synchronousIssuanceDispatchesPostIssuanceHooks() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = null))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val redemptionHook = RecordingHook("hook.post-issuance.redemption-consume")
            val webhook = RecordingHook("hook.post-issuance.webhook-notify")
            val discovery = CountingServiceCommandRegistry(listOf(redemptionHook.commandId, webhook.commandId))
            val sessionCommands = FakeSessionRegistry(redemptionHook, webhook)

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                    completenessCommand = null,
                    serviceCommandRegistry = discovery,
                    sessionScopedCommandRegistry = sessionCommands,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok synchronous issuance but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.credentials, "synchronous issuance must carry credentials")
            assertNull(result.value.transactionId, "synchronous issuance must not carry a transaction_id")
            assertEquals(1, formatHandler.issueCount, "format handler must have issued the credential")

            assertTrue(
                discovery.listCalls >= 1,
                "dispatcher must inspect the registry on synchronous issuance",
            )
            assertEquals(1, redemptionHook.invocations, "redemption hook must fire on synchronous issuance")
            assertEquals(1, webhook.invocations, "webhook hook must fire on synchronous issuance")
        }
}
