/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.wallet.interaction.CapturedDigitalCredentialsPayload
import com.sphereon.wallet.interaction.CapturedInteractionInput
import com.sphereon.wallet.interaction.CapturedInteractionLaunchAttributes
import com.sphereon.wallet.interaction.CapturedInteractionPayloadFormat
import com.sphereon.wallet.interaction.CapturedInteractionRuntimePlanResolver
import com.sphereon.wallet.interaction.LockedCapturedInteractionRuntimePlan
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletEntryPointKind
import com.sphereon.wallet.interaction.WalletInteractionCaptureSource
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolMatch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapturedInteractionOwnerBoundaryTest {
    @Test
    fun locksBackendOwnerBeforeFirstSemanticInterpretation() = runTest {
        var ownerLocked = false
        val adapter = RecordingAdapter { check(ownerLocked) { "semantic_interpretation_before_owner_lock" } }
        val engine =
            PolicyLockedCapturedInteractionClient(
                delegate =
                    testWalletInteractionEngine(
                        adapters = listOf(adapter),
                        sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    ),
                runtimePlanResolver =
                    CapturedInteractionRuntimePlanResolver {
                        ownerLocked = true
                        Ok(lockedPlan())
                    },
            )

        val session = engine.startCaptured(capturedInput())

        assertEquals(WalletInteractionStatus.CounterpartyNotice, session.state.status)
        assertEquals(ProtocolExecutionOwner.WALLET_BACKEND, adapter.lastExecutionOwner)
        assertEquals(WalletEntryPointKind.RAW_QR, adapter.lastEntryPoint?.kind)
        assertEquals(2, adapter.semanticCalls)
    }

    @Test
    fun ownerOrDigestFailureNeverInvokesProtocolAdapters() = runTest {
        val adapter = RecordingAdapter()
        val appOwned =
            PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(adapter)),
                CapturedInteractionRuntimePlanResolver { Ok(lockedPlan(ProtocolExecutionOwner.WALLET_APP)) },
            )
        assertFailsWith<IllegalArgumentException> { appOwned.startCaptured(capturedInput()) }

        val wrongDigest =
            PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(adapter)),
                CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
            )
        assertFailsWith<IllegalArgumentException> { wrongDigest.startCaptured(capturedInput().copy(payloadDigest = "sha256:00")) }

        assertEquals(0, adapter.semanticCalls)

        val dcaTamperedAdapter = RecordingAdapter()
        val dcaTamperedEngine = PolicyLockedCapturedInteractionClient(
            testWalletInteractionEngine(adapters = listOf(dcaTamperedAdapter)),
            CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
        )
        val validDca = digitalCredentialsInput(
            CapturedDigitalCredentialsPayload(
                "openid4vp-v1-signed",
                buildJsonObject { put("response", "signed-jws") },
                "https://verifier.example",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            dcaTamperedEngine.startCaptured(validDca.copy(rawPayload = validDca.rawPayload + byteArrayOf(' '.code.toByte())))
        }
        assertEquals(0, dcaTamperedAdapter.semanticCalls)
    }

    @Test
    fun onlineCapturedIngressCannotBecomeBleOrNfc() = runTest {
        val adapter = RecordingAdapter()
        val engine =
            PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(adapter)),
                CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
            )

        engine.startCaptured(capturedInput(source = WalletInteractionCaptureSource.MOBILE_DEEPLINK))

        assertEquals(WalletEntryPointKind.DEEP_LINK, adapter.lastEntryPoint?.kind)
    }

    @Test
    fun typedDigitalCredentialsCapturePreservesPlatformContextAfterOwnerLock() = runTest {
        var ownerLocked = false
        val adapter = RecordingAdapter { check(ownerLocked) { "semantic_interpretation_before_owner_lock" } }
        val payload =
            CapturedDigitalCredentialsPayload(
                protocol = "openid4vp-v1-signed",
                data = buildJsonObject {
                    put("response", "signed-jws-header.signed-jws-payload.signed-jws-signature")
                    put("response_mode", "dc_api.jwt")
                },
                platformVerifierOrigin = "https://verifier.example:8443",
                selectedEntryId = "entry-selected",
                selectedCredentialSetId = "set-selected",
                selectedCredentialIds = listOf("entry-selected", "entry-secondary"),
            )
        val input = digitalCredentialsInput(payload)
        val engine =
            PolicyLockedCapturedInteractionClient(
                delegate = testWalletInteractionEngine(adapters = listOf(adapter), sessionIdGenerator = FixedWalletInteractionSessionIdGenerator()),
                runtimePlanResolver = CapturedInteractionRuntimePlanResolver {
                    ownerLocked = true
                    Ok(lockedPlan())
                },
            )

        engine.startCaptured(input)

        assertEquals(ProtocolExecutionOwner.WALLET_BACKEND, adapter.lastExecutionOwner)
        assertEquals(WalletEntryPointKind.PARSED_OBJECT, adapter.lastEntryPoint?.kind)
        assertEquals(payload.protocol, adapter.lastEntryPoint?.parsedType)
        assertEquals(payload.data, adapter.lastEntryPoint?.parsed)
        assertEquals("https://verifier.example:8443", adapter.lastAttributes["digital_credentials.origin"])
        assertEquals("entry-selected", adapter.lastAttributes["digital_credentials.selected_entry_id"])
        assertEquals("set-selected", adapter.lastAttributes["digital_credentials.selected_credential_set_id"])
        assertEquals(
            "[\"entry-selected\",\"entry-secondary\"]",
            adapter.lastAttributes["digital_credentials.selected_credential_ids"],
        )
        assertEquals(
            "signed-jws-header.signed-jws-payload.signed-jws-signature",
            adapter.lastEntryPoint?.parsed?.let { (it as kotlinx.serialization.json.JsonObject)["response"]?.toString()?.trim('"') },
        )
    }

    @Test
    fun typedDigitalCredentialsCaptureRejectsInvalidShapeWithoutOpaqueFallback() = runTest {
        val invalidPayloads =
            listOf(
                CapturedDigitalCredentialsPayload("openid4vp-v1-unknown", buildJsonObject {}, "https://verifier.example"),
                CapturedDigitalCredentialsPayload("openid4vp-v1-signed", buildJsonObject {}, "https://bad origin"),
                CapturedDigitalCredentialsPayload(
                    "openid4vp-v1-signed", buildJsonObject {}, "https://verifier.example",
                    selectedCredentialIds = listOf("entry-without-set"),
                ),
                CapturedDigitalCredentialsPayload(
                    "openid4vp-v1-signed", buildJsonObject {}, "https://verifier.example",
                    selectedCredentialSetId = "set-1", selectedCredentialIds = listOf("duplicate", "duplicate"),
                ),
            )
        for (payload in invalidPayloads) {
            val adapter = RecordingAdapter()
            val engine = PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(adapter)),
                CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
            )
            assertFailsWith<IllegalArgumentException> { engine.startCaptured(digitalCredentialsInput(payload)) }
            assertEquals(0, adapter.semanticCalls)
        }

        for (source in listOf(WalletInteractionCaptureSource.API, WalletInteractionCaptureSource.WEB_UI)) {
            val sourceMismatchAdapter = RecordingAdapter()
            val sourceMismatchEngine = PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(sourceMismatchAdapter)),
                CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
            )
            assertFailsWith<IllegalArgumentException> {
                sourceMismatchEngine.startCaptured(
                    digitalCredentialsInput(
                        CapturedDigitalCredentialsPayload("openid4vp-v1-signed", buildJsonObject {}, "https://verifier.example"),
                    ).copy(source = source),
                )
            }
            assertEquals(0, sourceMismatchAdapter.semanticCalls)
        }

        val opaqueDigitalCredentialsAdapter = RecordingAdapter()
        val opaqueDigitalCredentialsEngine = PolicyLockedCapturedInteractionClient(
            testWalletInteractionEngine(adapters = listOf(opaqueDigitalCredentialsAdapter)),
            CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
        )
        assertFailsWith<IllegalArgumentException> {
            opaqueDigitalCredentialsEngine.startCaptured(
                capturedInput(source = WalletInteractionCaptureSource.DIGITAL_CREDENTIALS_API),
            )
        }
        assertEquals(0, opaqueDigitalCredentialsAdapter.semanticCalls)

        val unknownFieldAdapter = RecordingAdapter()
        val unknownFieldEngine = PolicyLockedCapturedInteractionClient(
            testWalletInteractionEngine(adapters = listOf(unknownFieldAdapter)),
            CapturedInteractionRuntimePlanResolver { Ok(lockedPlan()) },
        )
        val unknownFieldBytes = """{"protocol":"openid4vp-v1-signed","data":{},"platformVerifierOrigin":"https://verifier.example","unexpected":true}""".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            unknownFieldEngine.startCaptured(digitalCredentialsInput(unknownFieldBytes))
        }
        assertEquals(0, unknownFieldAdapter.semanticCalls)
    }

    @Test
    fun typedPayloadFormatParticipatesInCapturedInputEqualityAndToStringRedactsRequest() {
        val payload = CapturedDigitalCredentialsPayload(
            "openid4vp-v1-signed",
            buildJsonObject { put("response", "signed-jws-sensitive") },
            "https://verifier.example",
            selectedEntryId = "entry-sensitive",
            selectedCredentialSetId = "set-sensitive",
            selectedCredentialIds = listOf("entry-sensitive"),
        )
        val typed = digitalCredentialsInput(payload)
        val opaque = typed.copy(payloadFormat = CapturedInteractionPayloadFormat.OPAQUE_BYTES)

        assertTrue(typed != opaque)
        assertEquals(typed.hashCode(), typed.copy().hashCode())
        assertFalse(typed.toString().contains("signed-jws-sensitive"))
        assertFalse(typed.toString().contains("https://verifier.example"))
        assertFalse(payload.toString().contains("signed-jws-sensitive"))
        assertFalse(payload.toString().contains("openid4vp-v1-signed"))
        assertFalse(payload.toString().contains("https://verifier.example"))
        assertFalse(payload.toString().contains("entry-sensitive"))
        assertFalse(payload.toString().contains("set-sensitive"))
    }

    @Test
    fun capturedPayloadFormatAndTypedBytesSurviveServiceSerialization() {
        val typed = digitalCredentialsInput(
            CapturedDigitalCredentialsPayload(
                "openid4vp-v1-signed",
                buildJsonObject { put("response", "signed-jws") },
                "https://verifier.example",
                selectedEntryId = "entry-1",
                selectedCredentialSetId = "set-1",
                selectedCredentialIds = listOf("entry-1"),
            ),
        )
        val roundTrip = Json.decodeFromString<CapturedInteractionInput>(Json.encodeToString(typed))
        val legacyRoundTrip = Json.decodeFromString<CapturedInteractionInput>(Json.encodeToString(capturedInput()))

        assertEquals(typed, roundTrip)
        assertEquals(CapturedInteractionPayloadFormat.DIGITAL_CREDENTIALS_API_V1, roundTrip.payloadFormat)
        assertEquals(CapturedInteractionPayloadFormat.OPAQUE_BYTES, legacyRoundTrip.payloadFormat)
    }

    @Test
    fun allowlistedLaunchPreferencesSurviveOwnerLockWithoutReplacingCaptureEvidence() = runTest {
        val adapter = RecordingAdapter()
        val engine =
            PolicyLockedCapturedInteractionClient(
                testWalletInteractionEngine(adapters = listOf(adapter)),
                CapturedInteractionRuntimePlanResolver {
                    Ok(
                        lockedPlan().copy(
                            evidence =
                                mapOf(
                                    CapturedInteractionLaunchAttributes.OID4VCI_DPOP_ENABLED to "false",
                                    "lockedEvidence" to "recorded",
                                ),
                        ),
                    )
                },
            )

        engine.startCaptured(
            capturedInput().copy(
                launchAttributes = mapOf(
                    CapturedInteractionLaunchAttributes.OID4VCI_CLIENT_AUTHENTICATION_METHOD to "private_key_jwt",
                    CapturedInteractionLaunchAttributes.OID4VCI_DPOP_ENABLED to "true",
                    CapturedInteractionLaunchAttributes.OID4VCI_CREDENTIAL_BATCH_SIZE to "10",
                ),
            ),
        )

        assertEquals("private_key_jwt", adapter.lastAttributes[CapturedInteractionLaunchAttributes.OID4VCI_CLIENT_AUTHENTICATION_METHOD])
        assertEquals("false", adapter.lastAttributes[CapturedInteractionLaunchAttributes.OID4VCI_DPOP_ENABLED])
        assertEquals("10", adapter.lastAttributes[CapturedInteractionLaunchAttributes.OID4VCI_CREDENTIAL_BATCH_SIZE])
        assertEquals("recorded", adapter.lastAttributes["lockedEvidence"])
        assertEquals("profile-a", adapter.lastAttributes["walletProfileRef"])
    }

    @Test
    fun capturedLaunchAttributesRejectClientIdentityAndSecretOverrides() {
        val identityOverride = assertFailsWith<IllegalArgumentException> {
            capturedInput().copy(launchAttributes = mapOf("oid4vci.client.id" to "unregistered-client"))
        }
        assertEquals("wallet_interaction_capture_launch_attribute_not_allowed", identityOverride.message)

        val secretOverride = assertFailsWith<IllegalArgumentException> {
            capturedInput().copy(launchAttributes = mapOf("oid4vci.iae.code.verifier" to "secret-verifier"))
        }
        assertEquals("wallet_interaction_capture_launch_attribute_not_allowed", secretOverride.message)
    }

    private fun lockedPlan(owner: ProtocolExecutionOwner = ProtocolExecutionOwner.WALLET_BACKEND) =
        LockedCapturedInteractionRuntimePlan(
            walletUnitId = "wallet-unit-a",
            executionOwner = owner,
            policyRevision = 7,
            permittedFlowKinds = setOf(WalletInteractionFlowKind.CredentialReceive, WalletInteractionFlowKind.CredentialPresent),
            providerRefs = setOf("enterprise-holder"),
            evidence = mapOf("manifestSignatureRef" to "signature-a"),
        )

    private fun capturedInput(source: WalletInteractionCaptureSource = WalletInteractionCaptureSource.MOBILE_QR): CapturedInteractionInput {
        val payload = "openid-credential-offer://issuer.example/offer".encodeToByteArray()
        return CapturedInteractionInput(
            source = source,
            walletProfileRef = "profile-a",
            appRegistrationRef = "app-a",
            rawPayload = payload,
            payloadDigest = "sha256:${hash(payload, DigestAlg.SHA256).encodeToHex()}",
            captureBinding = "activation:app-a",
            expectedPolicyRevision = 7,
            idempotencyKey = "capture-a",
        )
    }

    private fun digitalCredentialsInput(payload: CapturedDigitalCredentialsPayload): CapturedInteractionInput =
        digitalCredentialsInput(Json.encodeToString(payload).encodeToByteArray())

    private fun digitalCredentialsInput(payloadBytes: ByteArray): CapturedInteractionInput =
        CapturedInteractionInput(
            source = WalletInteractionCaptureSource.DIGITAL_CREDENTIALS_API,
            walletProfileRef = "profile-a",
            appRegistrationRef = "app-a",
            rawPayload = payloadBytes,
            payloadDigest = "sha256:${hash(payloadBytes, DigestAlg.SHA256).encodeToHex()}",
            captureBinding = "activation:app-a",
            expectedPolicyRevision = 7,
            idempotencyKey = "capture-dca-a",
            payloadFormat = CapturedInteractionPayloadFormat.DIGITAL_CREDENTIALS_API_V1,
        )
}

private class RecordingAdapter(
    private val beforeSemanticCall: () -> Unit = {},
) : WalletInteractionProtocolAdapter {
    var semanticCalls: Int = 0
    var lastEntryPoint: WalletEntryPoint? = null
    var lastExecutionOwner: ProtocolExecutionOwner? = null
    var lastAttributes: Map<String, String> = emptyMap()

    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = "recording-oid4vc",
            protocol = WalletProtocol.OID4VCI,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch {
        beforeSemanticCall()
        semanticCalls += 1
        lastEntryPoint = entryPoint
        return WalletProtocolMatch.strong()
    }

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        beforeSemanticCall()
        semanticCalls += 1
        lastEntryPoint = entryPoint
        lastExecutionOwner = context.executionOwner
        lastAttributes = context.attributes
        return WalletInteractionSession(
            context.sessionId,
            WalletInteractionState(
                sessionId = context.sessionId,
                walletUnitId = context.walletUnitId,
                status = WalletInteractionStatus.CounterpartyNotice,
                protocol = WalletProtocol.OID4VCI,
            ),
        )
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: com.sphereon.wallet.interaction.WalletInteractionAction,
    ): WalletInteractionState = sessionState
}
