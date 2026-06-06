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
 * Exercises the §6.1 completeness-driven deferral decision tree inside
 * [HandleCredentialRequestCommandImpl] (Task 7.2): the second, earlier deferral trigger that
 * runs after the attribute contributor and before the format handler.
 */
class HandleCredentialRequestCompletenessDeferralTest {
    private val configId = "TestCredential"

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

    /** [JweService] is injected into [CredentialResponseEncryptor] but never reached on the issuance path. */
    private object ThrowingJweService : JweService {
        override val commands: JweService.Commands get() = throw UnsupportedOperationException("not used")

        override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = throw UnsupportedOperationException("not used")
    }

    /** Returns a fixed set of verdicts, mirroring the EDK completeness command. */
    private class FakeCompletenessCommand(
        private val verdicts: List<BindingCompletenessVerdict>,
    ) : EvaluateAttributeCompletenessCommand {
        var calls = 0

        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> {
            calls++
            return Ok(EvaluateAttributeCompletenessResult(verdicts = verdicts))
        }

        override suspend fun supports(args: Any): Boolean = args is EvaluateAttributeCompletenessArgs
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
    // (a) all bindings complete, no approval → happy path, no deferred entry
    // ------------------------------------------------------------------------

    @Test
    fun allBindingsCompleteIssuesNormallyWithoutDeferredEntry() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val completeness =
                FakeCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )

            val result =
                command(sessionStore, deferredStore, formatHandler, completeness)
                    .execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.credentials, "synchronous issuance returns credentials")
            assertNull(result.value.transactionId, "no deferral so no transaction_id")
            assertEquals(1, formatHandler.issueCount, "format handler must have issued the credential")
            assertTrue(deferredStore.created.isEmpty(), "no DeferredCredentialEntry must be created")
            assertEquals(
                IssuanceSessionStatus.CREDENTIAL_ISSUED,
                sessionStore.updated.last().status,
                "session ends in CREDENTIAL_ISSUED",
            )
        }

    // ------------------------------------------------------------------------
    // (b) incomplete + deferralRecommended → deferred (202), session DEFERRED
    // ------------------------------------------------------------------------

    @Test
    fun incompleteDeferrableBindingDefers() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
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
                command(sessionStore, deferredStore, formatHandler, completeness)
                    .execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            val transactionId = result.value.transactionId
            assertNotNull(transactionId, "deferred response must carry a transaction_id")
            assertNotNull(result.value.interval, "deferred response must carry a poll interval")
            assertNull(result.value.credentials, "deferred response must not carry credentials")
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched when deferring")
            assertEquals(1, deferredStore.created.size, "exactly one DeferredCredentialEntry must be created")
            assertEquals(transactionId, deferredStore.created.single().transactionId)
            assertEquals(
                IssuanceSessionStatus.DEFERRED,
                sessionStore.updated.last().status,
                "session status becomes DEFERRED",
            )
        }

    // ------------------------------------------------------------------------
    // (c) incomplete + NOT deferralRecommended → invalid_credential_request Err
    // ------------------------------------------------------------------------

    @Test
    fun incompleteNonDeferrableBindingFails() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val completeness =
                FakeCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = false,
                            missingRequiredPaths = listOf(AttributePath("given_name")),
                            deferralRecommended = false,
                        ),
                    ),
                )

            val result =
                command(sessionStore, deferredStore, formatHandler, completeness)
                    .execute(request())

            assertTrue(result.isErr, "expected Err for incomplete non-deferrable binding")
            assertEquals("invalid_credential_request", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("given_name"),
                "error message should name the missing claim",
            )
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched")
            assertTrue(deferredStore.created.isEmpty(), "no DeferredCredentialEntry on failure")
        }

    // ------------------------------------------------------------------------
    // (d) pure-IDK: no completeness command / no pipelineCorrelationId → unchanged
    // ------------------------------------------------------------------------

    @Test
    fun pureIdkWithNoCompletenessCommandIssuesNormally() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()

            val result =
                command(sessionStore, deferredStore, formatHandler, completenessCommand = null)
                    .execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.credentials, "synchronous issuance returns credentials")
            assertEquals(1, formatHandler.issueCount, "format handler must have issued the credential")
            assertTrue(deferredStore.created.isEmpty(), "no completeness check, no deferred entry")
        }

    @Test
    fun sessionWithoutPipelineCorrelationIdSkipsCompletenessCheck() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = null))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            // A completeness command IS wired, but the session has no pipeline join key,
            // so the decision tree must not run.
            val completeness =
                FakeCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = false,
                            deferralRecommended = false,
                        ),
                    ),
                )

            val result =
                command(sessionStore, deferredStore, formatHandler, completeness)
                    .execute(request())

            assertTrue(result.isOk, "no pipelineCorrelationId means the decision tree is skipped")
            assertEquals(0, completeness.calls, "completeness command must not be invoked")
            assertEquals(1, formatHandler.issueCount, "format handler issues normally")
        }

    // ------------------------------------------------------------------------
    // (e) all complete but awaitingApproval → deferred (202)
    // ------------------------------------------------------------------------

    @Test
    fun completeBindingAwaitingApprovalDefers() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val completeness =
                FakeCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = true,
                            awaitingApproval = true,
                        ),
                    ),
                )

            val result =
                command(sessionStore, deferredStore, formatHandler, completeness)
                    .execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "approval-gated issuance must defer with a transaction_id")
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched while awaiting approval")
            assertEquals(1, deferredStore.created.size, "a DeferredCredentialEntry must be created")
            assertEquals(
                IssuanceSessionStatus.DEFERRED,
                sessionStore.updated.last().status,
                "session status becomes DEFERRED",
            )
        }
}
