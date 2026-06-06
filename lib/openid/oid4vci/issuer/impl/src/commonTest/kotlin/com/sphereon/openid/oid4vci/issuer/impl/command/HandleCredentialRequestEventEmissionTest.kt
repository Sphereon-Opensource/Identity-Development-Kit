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
import com.sphereon.core.api.events.EventTypes
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
 * Pins event-emission classification inside [HandleCredentialRequestCommandImpl.emitOutcome]:
 *
 * - Synchronous issuance (Ok with `credentials`) emits exactly one
 *   [EventTypes.OID4VCI_CREDENTIAL_ISSUED].
 * - Deferred envelope (Ok with `transaction_id`, no `credentials`) emits exactly one
 *   [EventTypes.OID4VCI_CREDENTIAL_DEFERRED] — NOT the misclassified
 *   `OID4VCI_CREDENTIAL_ISSUED`. The real issuance event for a deferred flow is
 *   [EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED], emitted later from
 *   `HandleDeferredCredentialRequestCommandImpl` when a poll returns the credential.
 * - Failure (Err) emits exactly one [EventTypes.OID4VCI_CREDENTIAL_FAILED].
 *
 * This test guards against the regression flagged by the Phase 3 Follow-up A run, where the
 * event log showed `oid4vci.credential.issued` firing on a 202 deferral envelope.
 */
class HandleCredentialRequestEventEmissionTest {
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

    /** [JweService] is wired into [CredentialResponseEncryptor] but never reached on these paths. */
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
        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> = Ok(EvaluateAttributeCompletenessResult(verdicts = verdicts))

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
        eventService: RecordingSessionEventService,
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
            eventService = eventService,
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
    // (a) deferred envelope (transactionId set, no credentials) → DEFERRED, not ISSUED
    // ------------------------------------------------------------------------

    @Test
    fun deferralEnvelopeEmitsCredentialDeferredNotCredentialIssued() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val events = RecordingSessionEventService()
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
                command(sessionStore, deferredStore, formatHandler, completeness, events)
                    .execute(request())

            assertTrue(result.isOk, "expected Ok deferred envelope but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "deferred response must carry a transaction_id")
            assertNull(result.value.credentials, "deferred response must NOT carry credentials")
            assertEquals(0, formatHandler.issueCount, "format handler must not run on the deferral path")

            // The whole point of the fix.
            assertEquals(1, events.emitted.size, "exactly one event must be emitted on a deferral envelope")
            val emitted = events.emitted.single()
            assertEquals(
                EventTypes.OID4VCI_CREDENTIAL_DEFERRED,
                emitted.type,
                "deferral must emit OID4VCI_CREDENTIAL_DEFERRED, NOT OID4VCI_CREDENTIAL_ISSUED",
            )
            assertTrue(
                events.emitted.none { it.type == EventTypes.OID4VCI_CREDENTIAL_ISSUED },
                "OID4VCI_CREDENTIAL_ISSUED must never fire when the response is a 202 deferral envelope",
            )
        }

    // ------------------------------------------------------------------------
    // (b) synchronous issuance (credentials set) → ISSUED (regression guard)
    // ------------------------------------------------------------------------

    @Test
    fun synchronousIssuanceEmitsCredentialIssued() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = null))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val events = RecordingSessionEventService()

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                    completenessCommand = null,
                    eventService = events,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok synchronous issuance but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.credentials, "synchronous issuance must carry credentials")
            assertNull(result.value.transactionId, "synchronous issuance must not carry a transaction_id")
            assertEquals(1, formatHandler.issueCount, "format handler must have issued the credential")

            assertEquals(1, events.emitted.size, "exactly one event must be emitted on synchronous issuance")
            assertEquals(
                EventTypes.OID4VCI_CREDENTIAL_ISSUED,
                events.emitted.single().type,
                "synchronous issuance must emit OID4VCI_CREDENTIAL_ISSUED",
            )
        }

    // ------------------------------------------------------------------------
    // (c) failure (Err) → FAILED (regression guard)
    // ------------------------------------------------------------------------

    @Test
    fun failureEmitsCredentialFailed() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session(pipelineCorrelationId = "corr-1"))
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val events = RecordingSessionEventService()
            // Force the §6.1 completeness gate to fail with a non-deferrable missing claim.
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
                command(sessionStore, deferredStore, formatHandler, completeness, events)
                    .execute(request())

            assertTrue(result.isErr, "expected Err for incomplete non-deferrable binding")
            assertEquals(1, events.emitted.size, "exactly one event must be emitted on failure")
            assertEquals(
                EventTypes.OID4VCI_CREDENTIAL_FAILED,
                events.emitted.single().type,
                "failure must emit OID4VCI_CREDENTIAL_FAILED",
            )
        }
}
