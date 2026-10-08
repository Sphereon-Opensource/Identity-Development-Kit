/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.*
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import com.sphereon.wallet.wsca.WscaPreparedSigning
import com.sphereon.wallet.wsca.WscaPreparedSigningFactory
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.*
import com.sphereon.openid.oid4vp.holder.*
import com.sphereon.wallet.interaction.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class Oid4vpListedJwtPresentationTest {
    @Test fun jwtAndJwtLdFreezeBytesBeforeApprovalAndRestartWithoutPreparingAgain() = runTest {
        for (format in listOf(CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC_JSON_LD)) {
            val f = Fixture(format)
            val preparation = assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
            assertEquals(listOf(WalletApprovalOperation("credential-key", "test-digest")), preparation.operations)
            assertTrue(f.wsca.signCalls.isEmpty())
            val saved = f.saved()
            f.now += 15
            assertEquals(preparation, f.executor().preparePresentation(f.context, f.state))
            assertEquals(1, f.holder.preparations)
            assertIs<Oid4vpPresentationExecutionResult.Submitted>(f.executor().submitPresentation(f.context, f.state))
            assertEquals(saved.signing.single().protectedSegment + "." + saved.signing.single().payloadSegment,
                f.wsca.signCalls.single().signingInput.decodeToString())
            assertTrue(f.holder.finalResponsePersistedBeforePost)
            assertEquals(1, f.holder.submissions)
        }
    }

    @Test fun mixedSdJwtAndJwtShareOneListedApprovalAndRetainFixedKeyBindingTime() = runTest {
        val f = Fixture()
        f.credentials += SelectedCredential("sd", "sd-credential", JsonPrimitive("sdjwt~"), CredentialFormat.SD_JWT_VC)
        val approval = assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
        assertEquals(listOf("sd-key", "credential-key"), approval.operations.map { it.keyRef })
        val fixedTime = f.sdPreparedAt
        f.now += 20
        assertIs<Oid4vpPresentationExecutionResult.Submitted>(f.executor().submitPresentation(f.context, f.state))
        assertEquals(fixedTime, f.sdAppliedAt)
        assertEquals(listOf(CredentialFormat.JWT_VC_JSON, CredentialFormat.SD_JWT_VC), f.holder.composed.map { it.credentialFormat })
        assertTrue(f.holder.composed.last().sdJwtKeyBindingApplied)
    }

    @Test fun realSdJwtAndJwtSignExactlyTheApprovedHashesWithOriginalKeyBindingIat() = runTest {
        val f = Fixture(realSd = true)
        f.holder.resolved = f.holder.resolved.copy(dcqlQuery = com.sphereon.openid.oid4vp.dcql.DcqlQuery(credentials = listOf(
            com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery(id = "sd", format = "dc+sd-jwt", meta = com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta("urn:test:sdjwt")),
        )))
        val jwk = Json.parseToJsonElement(Fixture.SD_KEY)
        val sdHeader = "{\"alg\":\"ES256\",\"typ\":\"dc+sd-jwt\"}".encodeToByteArray().encodeToBase64Url()
        val sdPayload = buildJsonObject { put("iss", "https://issuer.example"); put("sub", "subject"); put("cnf", buildJsonObject { put("jwk", jwk) }) }
            .toString().encodeToByteArray().encodeToBase64Url()
        f.credentials += SelectedCredential("sd", "sd-credential", JsonPrimitive("$sdHeader.$sdPayload.issuer-signature~"),
            CredentialFormat.SD_JWT_VC, holderKeyRef = "sd-key")
        val approval = assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
        assertEquals(2, approval.operations.size)
        assertEquals(2, approval.operations.map { it.operationHash }.distinct().size)
        assertTrue(f.wsca.signCalls.isEmpty())
        f.digestWsca.approved += approval.operations
        val frozenIat = f.saved().issuedAt
        f.now += 30
        assertIs<Oid4vpPresentationExecutionResult.Submitted>(f.executor().submitPresentation(f.context, f.state))
        val actuallySigned = f.wsca.signCalls.map { call ->
            WalletApprovalOperation(call.keyRef.keyRef ?: call.keyRef.keyId, "sha256:" + hash(call.signingInput, DigestAlg.SHA256).encodeToHex())
        }
        assertEquals(approval.operations, actuallySigned)
        assertTrue(f.digestWsca.approved.isEmpty())
        val kbPayload = Json.parseToJsonElement(f.wsca.signCalls.first().signingInput.decodeToString().split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        assertEquals(frozenIat, kbPayload.getValue("iat").jsonPrimitive.long)
        assertEquals("nonce", kbPayload.getValue("nonce").jsonPrimitive.content)
        assertEquals("verifier", kbPayload.getValue("aud").jsonPrimitive.content)
        assertTrue(f.holder.finalResponsePersistedBeforePost)
        assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
        assertEquals(2, f.wsca.signCalls.size)
        assertEquals(1, f.holder.submissions)
    }

    @Test fun missingCorruptOrExpiredPrivatePreparationNeverFallsThroughToSigning() = runTest {
        for (mutation in listOf<(Fixture) -> Unit>(
            { it.values.remove("jwt_vp.prepared") },
            { it.values["jwt_vp.prepared"] = "corrupt" },
            { it.now += 120 },
            { it.values.clear() },
        )) {
            val f = Fixture()
            f.executor().preparePresentation(f.context, f.state)
            mutation(f)
            assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
            assertTrue(f.wsca.signCalls.isEmpty())
            assertEquals(0, f.holder.ordinaryCreations)
            assertEquals(0, f.holder.submissions)
        }
    }

    @Test fun changedSelectionKeyAndOwnerAreRejectedBeforeSigning() = runTest {
        for (mutation in listOf<(Fixture) -> Unit>(
            { it.credentials = it.credentials.map { c -> c.copy(credentialId = "changed") } },
            { it.credentials = it.credentials.map { c -> c.copy(holderKeyRef = "another-key") } },
            { it.context = it.context.copy(walletUnitId = "other-unit") },
            { it.values["selected_credential_ids_by_requirement"] = "{\"jwt\":[\"different\"]}" },
        )) {
            val f = Fixture()
            f.executor().preparePresentation(f.context, f.state)
            mutation(f)
            assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
            assertTrue(f.wsca.signCalls.isEmpty())
        }
    }

    @Test fun incompletePreparationCannotRegenerateAndMissingBackendSignerFailsClosed() = runTest {
        val f = Fixture()
        f.values["jwt_vp.intent"] = "true"
        assertIs<Oid4vpPresentationPreparation.Failed>(f.executor().preparePresentation(f.context, f.state))
        assertEquals(0, f.holder.preparations)
        val missing = Fixture()
        assertIs<Oid4vpPresentationPreparation.Failed>(missing.executor(withSigner = false).preparePresentation(missing.context, missing.state))
        assertTrue(missing.wsca.ensureAliases.isEmpty())
    }

    @Test fun replayCannotSignOrPostAgain() = runTest {
        val f = Fixture()
        f.executor().preparePresentation(f.context, f.state)
        assertIs<Oid4vpPresentationExecutionResult.Submitted>(f.executor().submitPresentation(f.context, f.state))
        assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
        assertEquals(1, f.wsca.signCalls.size)
        assertEquals(1, f.holder.submissions)
        assertIs<Oid4vpPresentationPreparation.Failed>(f.executor().preparePresentation(f.context, f.state))
    }

    @Test fun interruptedPreparationSigningAndDispatchAreTerminalAndDiagnosed() = runTest {
        for (phase in listOf("intent", "signing", "dispatch")) {
            val f = Fixture()
            if (phase == "intent") f.values["jwt_vp.intent"] = "true" else {
                assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
                f.values[if (phase == "signing") "jwt_vp.signing_started" else "jwt_vp.dispatch_started"] = "true"
            }
            // Known terminal state must be classified before a transient resolver error can offer retry.
            f.resolutionFailure = IllegalStateException("resolver must not be called")
            val preparation = assertIs<Oid4vpPresentationPreparation.Failed>(f.executor().preparePresentation(f.context, f.state))
            val submission = assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
            assertFalse(preparation.retryable)
            assertFalse(submission.retryable)
            assertEquals(2, f.diagnostics.size)
            assertTrue(f.diagnostics.all { it.second["cause.type"] == "IllegalStateException" })
            assertTrue(f.diagnostics.none { it.second["cause.message"] == "resolver must not be called" })
            assertTrue(f.wsca.signCalls.isEmpty())
            assertEquals(0, f.holder.submissions)
        }
    }

    @Test fun thrownResolverFailuresRetainTheirCauseAndCancellationEscapes() = runTest {
        val f = Fixture()
        f.resolutionFailure = IllegalStateException("resolution failed", IllegalArgumentException("invalid local fixture"))
        assertIs<Oid4vpPresentationPreparation.Failed>(f.executor().preparePresentation(f.context, f.state))
        assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
        assertEquals(2, f.diagnostics.size)
        assertTrue(f.diagnostics.all { it.first == "oid4vp.resolve_selected_credentials_failed" })
        assertTrue(f.diagnostics.all { it.second["cause.cause1.type"] == "IllegalArgumentException" })
        f.diagnostics.clear()
        val cancelled = CancellationException("cancel fixture")
        f.resolutionFailure = cancelled
        assertSame(cancelled, assertFailsWith<CancellationException> { f.executor().preparePresentation(f.context, f.state) })
        assertSame(cancelled, assertFailsWith<CancellationException> { f.executor().submitPresentation(f.context, f.state) })
        assertTrue(f.diagnostics.isEmpty(), "cancellation must not be mapped or logged as a protocol failure")
        assertTrue(f.wsca.signCalls.isEmpty())
        assertEquals(0, f.holder.submissions)
    }

    @Test fun plainSdJwtUnexpectedFailuresKeepTheirOriginalPropagationAndCanBeRetried() = runTest {
        for (phase in listOf("prepare", "create", "post")) {
            val f = Fixture(CredentialFormat.SD_JWT_VC)
            val failure = IllegalStateException("temporary presentation transport failure")
            f.failurePhase = phase
            f.injectedFailure = failure
            if (phase == "prepare") {
                assertSame(failure, assertFailsWith<IllegalStateException> { f.executor().preparePresentation(f.context, f.state) })
            } else {
                assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
                assertSame(failure, assertFailsWith<IllegalStateException> { f.executor().submitPresentation(f.context, f.state) })
            }
            assertEquals(1, f.diagnostics.size)
            assertEquals(failure.message, f.diagnostics.single().second["cause.message"])
            assertNull(f.values["jwt_vp.intent"])
            assertNull(f.values["jwt_vp.dispatch_started"])
            f.injectedFailure = null
            assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
            assertIs<Oid4vpPresentationExecutionResult.Submitted>(f.executor().submitPresentation(f.context, f.state))
        }
    }

    @Test fun stagedJwtPostFailureRemainsTerminalAndCannotRepeatSigningOrDispatch() = runTest {
        val f = Fixture()
        assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
        f.failurePhase = "post"
        f.injectedFailure = IllegalStateException("ambiguous staged dispatch failure")
        assertFalse(assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state)).retryable)
        val signs = f.wsca.signCalls.size
        val posts = f.holder.submissions
        assertEquals(1, signs)
        assertEquals(1, posts)
        f.injectedFailure = null
        assertFalse(assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state)).retryable)
        assertEquals(signs, f.wsca.signCalls.size)
        assertEquals(posts, f.holder.submissions)
    }

    @Test fun requestExpiryCapsPrivatePreparation() = runTest {
        val f = Fixture()
        f.holder.resolved = f.holder.resolved.copy(request = f.holder.resolved.request.copy(additionalParameters = mapOf("exp" to JsonPrimitive(f.now + 10))))
        assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(f.executor().preparePresentation(f.context, f.state))
        assertEquals(f.now + 10, f.saved().expiresAt)
        f.now += 10
        assertIs<Oid4vpPresentationExecutionResult.Failed>(f.executor().submitPresentation(f.context, f.state))
        assertTrue(f.wsca.signCalls.isEmpty())
    }

    @Test fun sdJwtOnlyAndWalletAppRetainExistingExecutionPaths() = runTest {
        val sd = Fixture(CredentialFormat.SD_JWT_VC)
        assertIs<Oid4vpPresentationPreparation.ApprovalRequired>(sd.executor(withSigner = false).preparePresentation(sd.context, sd.state))
        assertIs<Oid4vpPresentationExecutionResult.Submitted>(sd.executor(withSigner = false).submitPresentation(sd.context, sd.state))
        assertEquals(1, sd.holder.ordinaryCreations)
        assertNull(sd.values["jwt_vp.intent"])
        val app = Fixture()
        app.context = app.context.copy(executionOwner = ProtocolExecutionOwner.WALLET_APP)
        assertIs<Oid4vpPresentationPreparation.Ready>(app.executor(withSigner = false).preparePresentation(app.context, app.state))
        assertIs<Oid4vpPresentationExecutionResult.Submitted>(app.executor(withSigner = false).submitPresentation(app.context, app.state))
        assertEquals(1, app.holder.ordinaryCreations)
        assertNull(app.values["jwt_vp.intent"])
    }

    private class Fixture(format: CredentialFormat = CredentialFormat.JWT_VC_JSON, val realSd: Boolean = false) {
        var now = 1234L
        val values = mutableMapOf("entry_point.raw" to "request", Oid4vpWalletInteractionProtocolAdapter.SECURITY_OPERATION_BINDING_PRIVATE_KEY to "binding")
        val store = object : WalletInteractionPrivateSessionStore {
            override suspend fun put(sessionId: WalletInteractionSessionId, data: WalletInteractionPrivateSessionData) { values.clear(); values.putAll(data.values) }
            override suspend fun get(sessionId: WalletInteractionSessionId, namespace: String) = WalletInteractionPrivateSessionData(namespace, values.toMap())
            override suspend fun remove(sessionId: WalletInteractionSessionId, namespace: String) { values.clear() }
            override suspend fun removeSession(sessionId: WalletInteractionSessionId) { values.clear() }
        }
        val diagnostics = mutableListOf<Pair<String, Map<String, String>>>()
        var resolutionFailure: Exception? = null
        var failurePhase: String? = null
        var injectedFailure: Exception? = null
        var context = WalletInteractionContext(WalletInteractionSessionId("session"), "unit", ProtocolExecutionOwner.WALLET_BACKEND,
            privateSessionStore = store, diagnostics = object : WalletInteractionDiagnostics {
                override fun warn(event: String, sessionId: WalletInteractionSessionId?, details: Map<String, String>) {
                    diagnostics += event to details
                }
            })
        val state = WalletInteractionState(sessionId = context.sessionId, walletUnitId = "unit", status = WalletInteractionStatus.Sharing,
            flowKind = WalletInteractionFlowKind.CredentialPresent, protocol = WalletProtocol.OID4VP, adapterId = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID)
        var credentials = listOf(SelectedCredential("jwt", "credential", JsonPrimitive("credential-compact"), format,
            holderKeyRef = "credential-key", holderId = "did:example:holder",
            holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.ManagedKid("public-kid"), holderSigningAlgorithm = SignatureAlgorithm.ED25519))
        val wsca = RecordingWsca(resolvedKeyId = "internal-id-distinct-from-reference", publicKeyJwks = mapOf("sd-key" to SD_KEY))
        val digestWsca = DigestWsca(wsca)
        val signingSurface: Wsca = if (realSd) digestWsca else wsca
        val holder = Holder(this)
        var sdPreparedAt: Long? = null
        var sdAppliedAt: Long? = null
        val sd = object : Oid4vpSdJwtHolderBindingProvider {
            override suspend fun prepareHolderBinding(request: Oid4vpSdJwtHolderBindingRequest): IdkResult<List<WalletApprovalOperation>, IdkError> {
                if (failurePhase == "prepare") injectedFailure?.let { throw it }
                sdPreparedAt = request.issuedAtEpochSeconds
                return Ok(if (request.selectedCredentials.any { it.credentialFormat.isSdJwt }) listOf(WalletApprovalOperation("sd-key", "sd-hash")) else emptyList())
            }
            override suspend fun applyHolderBinding(request: Oid4vpSdJwtHolderBindingRequest): IdkResult<List<SelectedCredential>, IdkError> {
                sdAppliedAt = request.issuedAtEpochSeconds
                return Ok(request.selectedCredentials.map { if (it.credentialFormat.isSdJwt) it.copy(sdJwtKeyBindingApplied = true) else it })
            }
        }
        fun executor(withSigner: Boolean = true) = Oid4vpHolderPresentationExecutor(holder,
            object : Oid4vpSelectedCredentialResolver {
                override suspend fun resolveSelectedCredentials(context: WalletInteractionContext, state: WalletInteractionState,
                    resolvedRequest: ResolvedOid4vpRequest, selectedCredentialIdsByRequirement: Map<String, List<String>>): List<SelectedCredential> {
                    resolutionFailure?.let { throw it }
                    return credentials
                }
            }, if (realSd) SecureComponentOid4vpSdJwtHolderBindingProvider(signingSurface) else sd,
            preparedJwtSigningProvider = if (withSigner) SecureComponentOid4vpJwtVpSigningProvider(signingSurface) else null,
            nowEpochSeconds = { now })
        fun saved(): Oid4vpPreparedJwtPresentation = Json.decodeFromString(values.getValue("jwt_vp.prepared"))
        companion object { const val SD_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"first-x\",\"y\":\"first-y\"}" }
    }

    /** Computes real signing-input digests and refuses any signature absent from the returned approval. */
    private class DigestWsca(private val delegate: RecordingWsca) : Wsca by delegate {
        private val factory = WscaPreparedSigningFactory.create()
        val approved = mutableListOf<WalletApprovalOperation>()
        override suspend fun prepareSign(request: WscaSigningRequest): IdkResult<WscaPreparedSigning, IdkError> = Ok(factory.mint(
            walletUnitId = request.walletUnitId, keyRef = request.keyRef, walletAccountId = request.walletAccountId,
            operationBinding = request.operationBinding, operationType = "test.sign",
            digestBinding = "sha256:" + hash(request.signingInput, DigestAlg.SHA256).encodeToHex(),
            nonce = request.nonce ?: "test-nonce", audience = request.audience ?: request.operationBinding, signingInput = request.signingInput,
        ))
        override suspend fun sign(prepared: WscaPreparedSigning, request: WscaSigningRequest): IdkResult<ByteArray, IdkError> {
            val entry = WalletApprovalOperation(request.keyRef.keyRef ?: request.keyRef.keyId,
                "sha256:" + hash(request.signingInput, DigestAlg.SHA256).encodeToHex())
            check(approved.remove(entry)) { "Signing bytes or key were not in the approved list" }
            return delegate.sign(prepared, request)
        }
    }

    private class Holder(val fixture: Fixture) : Oid4vpHolderService {
        var preparations = 0
        var submissions = 0
        var ordinaryCreations = 0
        var finalResponsePersistedBeforePost = false
        var composed = emptyList<SelectedCredential>()
        var resolved = ResolvedOid4vpRequest(AuthorizationRequest(clientId = "verifier", responseType = "vp_token", nonce = "nonce"),
            verifierInfo = VerifierInfo("verifier", ClientIdScheme.PRE_REGISTERED))
        override suspend fun parseAuthorizationRequest(requestUri: String, walletConfig: WalletConfig?) = Ok(resolved.request)
        override suspend fun parseDigitalCredentialsAuthorizationRequest(request: DigitalCredentialsAuthorizationRequest, walletConfig: WalletConfig?) = Ok(resolved.request)
        override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest) = Ok(resolved)
        override suspend fun prepareJwtVpResponse(request: ResolvedOid4vpRequest, selectedCredentials: List<SelectedCredential>): IdkResult<HolderPreparedJwtVpResponse, IdkError> {
            preparations++
            return Ok(HolderPreparedJwtVpResponse(request.request.state, selectedCredentials.map { credential ->
                HolderPreparedJwtVp(credential.credentialQueryId, credential.credentialId,
                    HolderJwtVpSigningRequest(payload = buildJsonObject { put("iat", fixture.now); put("aud", "verifier"); put("nonce", "nonce"); put("credential", credential.presentation) },
                        keyReference = credential.holderKeyRef!!, signatureAlgorithm = credential.holderSigningAlgorithm!!,
                        identifier = credential.holderJwtVpSigningIdentifier!!, protectedHeader = buildJsonObject { put("typ", "JWT") },
                        walletUnitId = credential.holderJwtVpWalletUnitId, operationBinding = credential.holderJwtVpOperationBinding))
            }))
        }
        override suspend fun createAuthorizationResponse(request: ResolvedOid4vpRequest, selectedCredentials: List<SelectedCredential>, preparedPresentations: List<PreparedPresentation>): IdkResult<AuthorizationResponse, IdkError> {
            ordinaryCreations++
            if (fixture.failurePhase == "create") fixture.injectedFailure?.let { throw it }
            return Ok(AuthorizationResponse(code = ""))
        }
        override suspend fun createAuthorizationResponseWithPreparedJwtVp(request: ResolvedOid4vpRequest, selectedCredentials: List<SelectedCredential>,
            preparedPresentations: List<PreparedPresentation>, preparedJwtVp: HolderPreparedJwtVpResponse, signatures: List<HolderJwtVpSigningResult>): IdkResult<AuthorizationResponse, IdkError> {
            composed = selectedCredentials
            assertEquals(preparedJwtVp.presentations.size, signatures.size)
            return Ok(AuthorizationResponse(code = "", additionalParameters = mapOf("vp_token" to JsonPrimitive(signatures.single().compactJws))))
        }
        override suspend fun submitAuthorizationResponse(resolvedRequest: ResolvedOid4vpRequest, response: AuthorizationResponse, responseMode: ResponseMode?, jarmOptions: JarmOptions?): IdkResult<SubmissionResult, IdkError> {
            submissions++
            if (fixture.failurePhase == "post") fixture.injectedFailure?.let { throw it }
            finalResponsePersistedBeforePost = fixture.values["jwt_vp.final_response"]?.let { Json.decodeFromString<AuthorizationResponse>(it) == response } == true
            return Ok(SubmissionResult.Success())
        }
    }
}
