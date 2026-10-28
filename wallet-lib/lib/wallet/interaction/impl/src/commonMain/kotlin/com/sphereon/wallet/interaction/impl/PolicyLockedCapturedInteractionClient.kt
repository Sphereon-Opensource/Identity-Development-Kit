/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.wallet.interaction.CapturedInteractionClient
import com.sphereon.wallet.interaction.CapturedDigitalCredentialsPayload
import com.sphereon.wallet.interaction.CapturedInteractionInput
import com.sphereon.wallet.interaction.CapturedInteractionPayloadFormat
import com.sphereon.wallet.interaction.CapturedInteractionRuntimePlanResolver
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionCaptureSource
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Adds policy-locked opaque capture only when a product assembly supplies an authoritative runtime
 * plan resolver. Ordinary local wallet graphs expose [WalletInteractionClient] without this
 * backend-only capability.
 */
class PolicyLockedCapturedInteractionClient(
    private val delegate: WalletInteractionClient,
    private val runtimePlanResolver: CapturedInteractionRuntimePlanResolver,
) : CapturedInteractionClient {
    override suspend fun start(input: WalletInteractionInput): WalletInteractionSession = delegate.start(input)

    override suspend fun startCaptured(input: CapturedInteractionInput): WalletInteractionSession {
        // This is deliberately the first operation. The resolver is forbidden from inspecting the
        // raw payload and locks the execution owner before this process performs semantic work.
        val lockedPlan = runtimePlanResolver.resolve(input).getOrThrow()
        require(lockedPlan.executionOwner == ProtocolExecutionOwner.WALLET_BACKEND) {
            "wallet_interaction_captured_owner_not_backend"
        }
        require(lockedPlan.policyRevision == input.expectedPolicyRevision) {
            "wallet_interaction_capture_policy_revision_mismatch"
        }
        require(input.rawPayload.size <= MAX_CAPTURE_BYTES) {
            "wallet_interaction_capture_payload_too_large"
        }
        val actualDigest = "sha256:${hash(input.rawPayload, DigestAlg.SHA256).encodeToHex()}"
        require(constantTimeEquals(actualDigest, input.payloadDigest.lowercase())) {
            "wallet_interaction_capture_digest_mismatch"
        }

        // Do not interpret the payload until the backend owner, policy revision, size, and digest
        // have all been checked. A typed DCA payload has no opaque-byte fallback.
        val digitalCredentialsPayload =
            when (input.payloadFormat) {
                CapturedInteractionPayloadFormat.OPAQUE_BYTES -> {
                    require(input.source != WalletInteractionCaptureSource.DIGITAL_CREDENTIALS_API) {
                        "wallet_interaction_capture_digital_credentials_typed_payload_required"
                    }
                    null
                }
                CapturedInteractionPayloadFormat.DIGITAL_CREDENTIALS_API_V1 -> {
                    require(input.source == WalletInteractionCaptureSource.DIGITAL_CREDENTIALS_API) {
                        "wallet_interaction_capture_digital_credentials_source_invalid"
                    }
                    val encoded =
                        try {
                            input.rawPayload.decodeToString(throwOnInvalidSequence = true)
                        } catch (_: Exception) {
                            throw IllegalArgumentException("wallet_interaction_capture_digital_credentials_payload_invalid")
                        }
                    require(encoded.encodeToByteArray().contentEquals(input.rawPayload)) {
                        "wallet_interaction_capture_payload_not_utf8"
                    }
                    val payload =
                        try {
                            DIGITAL_CREDENTIALS_JSON.decodeFromString<CapturedDigitalCredentialsPayload>(encoded)
                        } catch (_: SerializationException) {
                            throw IllegalArgumentException("wallet_interaction_capture_digital_credentials_payload_invalid")
                        }
                    require(DIGITAL_CREDENTIALS_JSON.encodeToString(payload).encodeToByteArray().contentEquals(input.rawPayload)) {
                        "wallet_interaction_capture_digital_credentials_payload_not_canonical"
                    }
                    validateDigitalCredentialsPayload(payload)
                    payload
                }
            }

        // Semantic classification begins only after the authoritative owner and exact payload have
        // been locked. Captured online ingress never introduces BLE, NFC, or offline mdoc paths.
        val entryPoint =
            if (digitalCredentialsPayload != null) {
                WalletEntryPoint.parsed(
                    type = digitalCredentialsPayload.protocol,
                    value = digitalCredentialsPayload.data,
                    source = input.source.name,
                )
            } else {
                val value = input.rawPayload.decodeToString()
                require(value.encodeToByteArray().contentEquals(input.rawPayload)) {
                    "wallet_interaction_capture_payload_not_utf8"
                }
                when (input.source) {
                    WalletInteractionCaptureSource.MOBILE_QR -> WalletEntryPoint.rawQr(value, input.source.name)
                    WalletInteractionCaptureSource.MOBILE_DEEPLINK -> WalletEntryPoint.link(value, input.source.name)
                    WalletInteractionCaptureSource.WEB_UI,
                    WalletInteractionCaptureSource.API,
                    -> if (looksLikeUri(value)) WalletEntryPoint.link(value, input.source.name) else WalletEntryPoint.rawQr(value, input.source.name)
                    WalletInteractionCaptureSource.DIGITAL_CREDENTIALS_API ->
                        throw IllegalArgumentException("wallet_interaction_capture_digital_credentials_typed_payload_required")
                }
            }
        val digitalCredentialsMetadata = digitalCredentialsPayload?.toInteractionMetadata().orEmpty()
        return delegate.start(
            WalletInteractionInput(
                walletUnitId = lockedPlan.walletUnitId,
                entryPoint = entryPoint,
                executionOwner = lockedPlan.executionOwner,
                requestedFlowKinds = lockedPlan.permittedFlowKinds.toList(),
                metadata =
                    input.launchAttributes + lockedPlan.evidence + digitalCredentialsMetadata +
                        mapOf(
                            "captureSource" to input.source.name,
                            "walletProfileRef" to input.walletProfileRef,
                            "appRegistrationRef" to input.appRegistrationRef,
                            "payloadDigest" to actualDigest,
                            "policyRevision" to lockedPlan.policyRevision.toString(),
                            "idempotencyKey" to input.idempotencyKey,
                            "executionOwnerLocked" to lockedPlan.executionOwner.name,
                        ),
            ),
        )
    }

    override suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionSession = delegate.load(sessionId)

    override suspend fun resume(sessionId: WalletInteractionSessionId): WalletInteractionSession = delegate.resume(sessionId)

    override suspend fun dispatch(sessionId: WalletInteractionSessionId, action: WalletInteractionAction) =
        delegate.dispatch(sessionId, action)

    override suspend fun cancel(sessionId: WalletInteractionSessionId) = delegate.cancel(sessionId)

    override fun observe(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionState> = delegate.observe(sessionId)
}

private fun validateDigitalCredentialsPayload(payload: CapturedDigitalCredentialsPayload) {
    require(payload.protocol in DIGITAL_CREDENTIALS_PROTOCOLS) {
        "wallet_interaction_capture_digital_credentials_protocol_unsupported"
    }
    require(
        payload.platformVerifierOrigin.isNotBlank() &&
            payload.platformVerifierOrigin.length <= 2_048 &&
            payload.platformVerifierOrigin.none { it.isWhitespace() },
    ) {
        "wallet_digital_credentials_origin_invalid"
    }
    require(payload.selectedEntryId?.isNotBlank() != false) {
        "digital_credentials_selected_entry_id_invalid"
    }
    require(payload.selectedCredentialSetId?.isNotBlank() != false) {
        "digital_credentials_selected_credential_set_id_invalid"
    }
    require(
        payload.selectedCredentialIds.all { it.isNotBlank() } &&
            payload.selectedCredentialIds.distinct().size == payload.selectedCredentialIds.size,
    ) {
        "digital_credentials_selected_credential_ids_invalid"
    }
    require(payload.selectedCredentialIds.isEmpty() || payload.selectedCredentialSetId != null) {
        "digital_credentials_selected_credential_set_missing"
    }
    require(
        payload.selectedCredentialSetId == null ||
            payload.selectedEntryId == null ||
            payload.selectedEntryId in payload.selectedCredentialIds,
    ) {
        "digital_credentials_selected_entry_not_in_selected_set"
    }
}

private fun CapturedDigitalCredentialsPayload.toInteractionMetadata(): Map<String, String> =
    buildMap {
        put(DIGITAL_CREDENTIAL_ORIGIN_METADATA_KEY, platformVerifierOrigin)
        selectedEntryId?.let { put(DIGITAL_CREDENTIAL_SELECTED_ENTRY_ID_KEY, it) }
        selectedCredentialSetId?.let { put(DIGITAL_CREDENTIAL_SELECTED_CREDENTIAL_SET_ID_KEY, it) }
        if (selectedCredentialSetId != null) {
            put(DIGITAL_CREDENTIAL_SELECTED_CREDENTIAL_IDS_KEY, JsonArray(selectedCredentialIds.map(::JsonPrimitive)).toString())
        }
    }

private val DIGITAL_CREDENTIALS_PROTOCOLS =
    setOf("openid4vp-v1-unsigned", "openid4vp-v1-signed", "openid4vp-v1-multisigned")
private val DIGITAL_CREDENTIALS_JSON = Json { ignoreUnknownKeys = false }
private const val DIGITAL_CREDENTIAL_ORIGIN_METADATA_KEY = "digital_credentials.origin"
private const val DIGITAL_CREDENTIAL_SELECTED_ENTRY_ID_KEY = "digital_credentials.selected_entry_id"
private const val DIGITAL_CREDENTIAL_SELECTED_CREDENTIAL_SET_ID_KEY = "digital_credentials.selected_credential_set_id"
private const val DIGITAL_CREDENTIAL_SELECTED_CREDENTIAL_IDS_KEY = "digital_credentials.selected_credential_ids"

private const val MAX_CAPTURE_BYTES: Int = 65_536

private fun looksLikeUri(value: String): Boolean {
    val separator = value.indexOf(':')
    if (separator !in 1..32) return false
    return value.substring(0, separator).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
}

private fun constantTimeEquals(expected: String, actual: String): Boolean {
    if (expected.length != actual.length) return false
    var difference = 0
    expected.indices.forEach { index -> difference = difference or (expected[index].code xor actual[index].code) }
    return difference == 0
}
