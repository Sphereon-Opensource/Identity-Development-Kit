/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.interaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class WalletInteractionSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "wallet_interaction_session_id_blank" }
    }
}

/**
 * The wallet component that is the first semantic interpreter of captured interaction input and
 * owns the complete protocol exchange. Secure-component placement and credential storage are
 * deliberately independent from this decision.
 */
@Serializable
enum class ProtocolExecutionOwner {
    WALLET_APP,
    WALLET_BACKEND,
}

@Serializable
enum class WalletInteractionCaptureSource {
    MOBILE_QR,
    MOBILE_DEEPLINK,
    WEB_UI,
    API,
}

/**
 * Opaque handoff used when protocol ownership is resolved before any parsing or dereferencing.
 * The raw bytes are intentionally separate from [WalletEntryPoint], whose kind is already a
 * semantic classification made by a wallet protocol host.
 */
@Serializable
data class CapturedInteractionInput(
    val source: WalletInteractionCaptureSource,
    val walletProfileRef: String,
    val appRegistrationRef: String,
    val rawPayload: ByteArray,
    val payloadDigest: String,
    val captureBinding: String,
    val expectedPolicyRevision: Long,
    val idempotencyKey: String,
) {
    init {
        require(walletProfileRef.isNotBlank()) { "wallet_interaction_profile_ref_blank" }
        require(appRegistrationRef.isNotBlank()) { "wallet_interaction_app_registration_ref_blank" }
        require(rawPayload.isNotEmpty()) { "wallet_interaction_capture_payload_empty" }
        require(payloadDigest.isNotBlank()) { "wallet_interaction_capture_digest_blank" }
        require(captureBinding.isNotBlank()) { "wallet_interaction_capture_binding_blank" }
        require(expectedPolicyRevision > 0) { "wallet_interaction_policy_revision_invalid" }
        require(idempotencyKey.isNotBlank()) { "wallet_interaction_idempotency_key_blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedInteractionInput) return false
        return source == other.source &&
            walletProfileRef == other.walletProfileRef &&
            appRegistrationRef == other.appRegistrationRef &&
            rawPayload.contentEquals(other.rawPayload) &&
            payloadDigest == other.payloadDigest &&
            captureBinding == other.captureBinding &&
            expectedPolicyRevision == other.expectedPolicyRevision &&
            idempotencyKey == other.idempotencyKey
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + walletProfileRef.hashCode()
        result = 31 * result + appRegistrationRef.hashCode()
        result = 31 * result + rawPayload.contentHashCode()
        result = 31 * result + payloadDigest.hashCode()
        result = 31 * result + captureBinding.hashCode()
        result = 31 * result + expectedPolicyRevision.hashCode()
        result = 31 * result + idempotencyKey.hashCode()
        return result
    }
}

/**
 * Policy result created without interpreting captured protocol bytes. The owner and policy
 * revision are immutable inputs to the later semantic interaction.
 */
@Serializable
data class LockedCapturedInteractionRuntimePlan(
    val walletUnitId: String,
    val executionOwner: ProtocolExecutionOwner,
    val policyRevision: Long,
    val permittedFlowKinds: Set<WalletInteractionFlowKind>,
    val providerRefs: Set<String>,
    val evidence: Map<String, String> = emptyMap(),
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_locked_unit_id_blank" }
        require(policyRevision > 0) { "wallet_interaction_locked_policy_revision_invalid" }
        require(permittedFlowKinds.isNotEmpty()) { "wallet_interaction_locked_flow_kinds_empty" }
        require(providerRefs.isNotEmpty() && providerRefs.none(String::isBlank)) {
            "wallet_interaction_locked_provider_refs_empty"
        }
    }
}

@Serializable
enum class WalletInteractionFlowKind {
    CredentialReceive,
    CredentialPresent,
    AttendedPresent,
}

@Serializable
enum class WalletProtocol {
    OID4VCI,
    OID4VP,
    ISO18013,
    CUSTOM,
}

@Serializable
enum class WalletEntryPointKind {
    RAW_QR,
    DEEP_LINK,
    UNIVERSAL_LINK,
    HTTPS_LINK,
    NFC_HANDOVER,
    BLE_HANDOVER,
    WIFI_AWARE_HANDOVER,
    PARSED_OBJECT,
}

@Serializable
data class WalletInteractionInput(
    val walletUnitId: String,
    val entryPoint: WalletEntryPoint,
    val executionOwner: ProtocolExecutionOwner = ProtocolExecutionOwner.WALLET_APP,
    val requestedFlowKinds: List<WalletInteractionFlowKind> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_wallet_unit_id_blank" }
    }
}

@Serializable
data class WalletEntryPoint(
    val kind: WalletEntryPointKind,
    val raw: String? = null,
    val bytes: ByteArray? = null,
    val parsedType: String? = null,
    val parsed: JsonElement? = null,
    val source: String? = null,
) {
    fun summary(): WalletEntryPointSummary =
        WalletEntryPointSummary(
            kind = kind,
            source = source,
            scheme = raw?.substringBefore(":", missingDelimiterValue = "")?.takeIf { it.isNotBlank() },
            fingerprint = stableFingerprint(raw ?: bytes?.decodeToString() ?: parsed?.toString() ?: parsedType.orEmpty()),
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletEntryPoint) return false
        return kind == other.kind &&
            raw == other.raw &&
            bytes.contentEquals(other.bytes) &&
            parsedType == other.parsedType &&
            parsed == other.parsed &&
            source == other.source
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (raw?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (parsedType?.hashCode() ?: 0)
        result = 31 * result + (parsed?.hashCode() ?: 0)
        result = 31 * result + (source?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun rawQr(
            contents: String,
            source: String? = null
        ): WalletEntryPoint = WalletEntryPoint(kind = WalletEntryPointKind.RAW_QR, raw = contents, source = source)

        fun link(
            uri: String,
            source: String? = null
        ): WalletEntryPoint {
            val kind =
                when {
                    uri.startsWith("https://", ignoreCase = true) -> WalletEntryPointKind.HTTPS_LINK
                    uri.startsWith("http://", ignoreCase = true) -> WalletEntryPointKind.HTTPS_LINK
                    else -> WalletEntryPointKind.DEEP_LINK
                }
            return WalletEntryPoint(kind = kind, raw = uri, source = source)
        }

        fun nfc(
            payload: ByteArray,
            source: String? = null
        ): WalletEntryPoint = WalletEntryPoint(kind = WalletEntryPointKind.NFC_HANDOVER, bytes = payload, source = source)

        fun ble(
            payload: ByteArray,
            source: String? = null
        ): WalletEntryPoint = WalletEntryPoint(kind = WalletEntryPointKind.BLE_HANDOVER, bytes = payload, source = source)

        fun wifiAware(
            payload: ByteArray,
            source: String? = null
        ): WalletEntryPoint = WalletEntryPoint(kind = WalletEntryPointKind.WIFI_AWARE_HANDOVER, bytes = payload, source = source)

        fun parsed(
            type: String,
            value: JsonElement,
            source: String? = null
        ): WalletEntryPoint = WalletEntryPoint(kind = WalletEntryPointKind.PARSED_OBJECT, parsedType = type, parsed = value, source = source)
    }
}

@Serializable
data class WalletEntryPointSummary(
    val kind: WalletEntryPointKind,
    val source: String? = null,
    val scheme: String? = null,
    val fingerprint: String? = null,
)

@Serializable
data class WalletInteractionSession(
    val sessionId: WalletInteractionSessionId,
    val state: WalletInteractionState,
)

@Serializable
enum class WalletInteractionStatus {
    ResolvingEntryPoint,
    ImplementationChoiceRequired,
    UnsupportedEntryPoint,
    CounterpartyNotice,
    TrustReview,
    CredentialOfferReview,
    AuthorizationRequired,
    TxCodeRequired,
    CredentialPreview,
    CredentialSelection,
    DisclosureConsent,
    SecurityUnlockRequired,
    DeferredRetrievalPending,
    Sharing,
    Completed,
    Cancelled,
    Failed,
}

@Serializable
data class WalletInteractionState(
    val sessionId: WalletInteractionSessionId,
    val walletUnitId: String,
    val status: WalletInteractionStatus,
    /** Locked before semantic interpretation and immutable for the lifetime of the interaction. */
    val executionOwner: ProtocolExecutionOwner = ProtocolExecutionOwner.WALLET_APP,
    val revision: Long = 0,
    val flowKind: WalletInteractionFlowKind? = null,
    val activity: WalletInteractionActivitySummary? = null,
    val protocol: WalletProtocol? = null,
    val adapterId: String? = null,
    val entryPoint: WalletEntryPointSummary? = null,
    val implementationChoices: List<WalletImplementationChoice> = emptyList(),
    val counterparty: WalletCounterpartySummary? = null,
    val counterpartyEncounter: WalletCounterpartyEncounterResult? = null,
    val trust: WalletCounterpartyTrustSummary? = null,
    val credentialOffer: WalletCredentialOfferSummary? = null,
    val selectedCredentialConfigurationIds: List<String> = emptyList(),
    val credentialPreview: List<WalletCredentialPreview> = emptyList(),
    /** Credentials actually returned and accepted during this interaction; never offer previews. */
    val receivedCredentialPreview: List<WalletCredentialPreview> = emptyList(),
    val credentialSelection: WalletCredentialSelectionRequest? = null,
    val disclosure: WalletDisclosureSummary? = null,
    val securityChallenge: WalletSecurityChallenge? = null,
    val authorizationHandoffRef: WalletInteractionSensitiveInputRef? = null,
    /** Optional post-completion user-agent handoff, kept opaque outside the private session store. */
    val completionHandoffRef: WalletInteractionSensitiveInputRef? = null,
    val txCode: WalletTxCodeSpec? = null,
    val deferred: WalletDeferredRetrievalSummary? = null,
    val message: WalletDisplayMessage? = null,
    val error: WalletInteractionError? = null,
    val terminal: Boolean = false,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_wallet_unit_id_blank" }
    }

    fun next(
        status: WalletInteractionStatus = this.status,
        revision: Long = this.revision + 1,
        terminal: Boolean = false,
        message: WalletDisplayMessage? = this.message,
        error: WalletInteractionError? = null,
    ): WalletInteractionState = copy(status = status, revision = revision, terminal = terminal, message = message, error = error)

    companion object {
        fun resolving(
            sessionId: WalletInteractionSessionId,
            input: WalletInteractionInput,
        ): WalletInteractionState =
            WalletInteractionState(
                sessionId = sessionId,
                walletUnitId = input.walletUnitId,
                status = WalletInteractionStatus.ResolvingEntryPoint,
                executionOwner = input.executionOwner,
                entryPoint = input.entryPoint.summary(),
            )
    }
}

@Serializable
data class WalletInteractionActivitySummary(
    val type: WalletInteractionActivityType,
    val counterparty: WalletCounterpartySummary? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
enum class WalletInteractionActivityType {
    CREDENTIAL_RECEIVE,
    CREDENTIAL_PRESENTATION,
    LOGIN,
    ATTENDED_PRESENTATION,
}

@Serializable
data class WalletDisplayMessage(
    val titleKey: String? = null,
    val textKey: String? = null,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        requireWalletInteractionLocalizationKey("titleKey", titleKey)
        requireWalletInteractionLocalizationKey("textKey", textKey)
    }
}

@Serializable
data class WalletInteractionError(
    val code: String,
    val messageKey: String? = null,
    val retryable: Boolean = false,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        require(code.isNotBlank()) { "wallet_interaction_error_code_blank" }
        requireWalletInteractionLocalizationKey("messageKey", messageKey)
    }
}

@Serializable
data class WalletImplementationChoice(
    val adapterId: String,
    val protocol: WalletProtocol,
    val flowKinds: List<WalletInteractionFlowKind>,
    val labelKey: String,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        require(adapterId.isNotBlank()) { "wallet_interaction_adapter_id_blank" }
        requireWalletInteractionLocalizationKey("labelKey", labelKey)
    }
}

@Serializable
data class WalletCounterpartySummary(
    val role: WalletCounterpartyRole,
    val identifier: String,
    val displayName: String? = null,
    val logoUri: String? = null,
    /** Complete protocol-supplied display metadata. [displayName]/[logoUri] are the resolved active-locale face. */
    val localizedBranding: List<WalletCounterpartyLocalizedBranding> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    /** Stable Party identifier assigned by the wallet Party registry after resolution. */
    val partyId: String? = null,
) {
    init {
        require(identifier.isNotBlank()) { "wallet_counterparty_identifier_blank" }
        require(partyId == null || partyId.isNotBlank()) { "wallet_counterparty_party_id_blank" }
    }
}

/** Provider-neutral localized issuer/RP branding retained with the wallet Party contact. */
@Serializable
data class WalletCounterpartyLocalizedBranding(
    val locale: String? = null,
    val name: String,
    val logoUri: String? = null,
    val logoAltText: String? = null,
    val description: String? = null,
    val backgroundImageUri: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
) {
    init {
        require(name.isNotBlank()) { "wallet_counterparty_branding_name_blank" }
        require(locale == null || locale.isNotBlank()) { "wallet_counterparty_branding_locale_blank" }
    }
}

@Serializable
enum class WalletCounterpartyRole {
    ISSUER,
    VERIFIER,
    MDOC_READER,
}

@Serializable
data class WalletCredentialOfferSummary(
    val issuer: WalletCounterpartySummary,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCodeAvailable: Boolean = false,
    val authorizationCodeAvailable: Boolean = false,
    val txCodeRequired: Boolean = false,
    val branding: List<WalletCredentialBranding> = emptyList(),
)

@Serializable
data class WalletCredentialBranding(
    val credentialConfigurationId: String,
    val name: String? = null,
    val locale: String? = null,
    val description: String? = null,
    val logoUri: String? = null,
    val backgroundImageUri: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    /** Pre-issuance display names only. Credential values never belong in an offer summary. */
    val info: List<WalletCredentialOfferInfoDescriptor> = emptyList(),
)

@Serializable
data class WalletCredentialOfferInfoDescriptor(
    val path: List<String>,
    val displayName: String,
) {
    init {
        require(path.isNotEmpty() && path.none(String::isBlank)) { "wallet_credential_offer_info_path_invalid" }
        require(displayName.isNotBlank()) { "wallet_credential_offer_info_name_blank" }
    }
}

@Serializable
data class WalletTxCodeSpec(
    val inputMode: String? = null,
    val length: Int? = null,
    val descriptionKey: String? = null,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        requireWalletInteractionLocalizationKey("descriptionKey", descriptionKey)
    }
}

@Serializable
data class WalletCredentialPreview(
    val id: String,
    val name: String? = null,
    val format: String? = null,
    val issuer: WalletCounterpartySummary? = null,
    val claims: List<WalletClaimDescriptor> = emptyList(),
    /** Domain-resolved face from the accepted credential record, not renderer fallback data. */
    val branding: WalletCredentialBranding? = null,
)

@Serializable
data class WalletCredentialSelectionRequest(
    val requirements: List<WalletCredentialRequirement>,
    val satisfiable: Boolean,
    val credentialSets: List<WalletCredentialSetRequirement> = emptyList(),
)

@Serializable
data class WalletCredentialRequirement(
    val id: String,
    val format: String? = null,
    val multipleAllowed: Boolean = false,
    val requiredClaimPaths: List<List<JsonElement>> = emptyList(),
    val candidateCredentialIds: List<String> = emptyList(),
)

@Serializable
data class WalletCredentialSetRequirement(
    val id: String,
    val required: Boolean,
    val options: List<WalletCredentialSetOption>,
)

@Serializable
data class WalletCredentialSetOption(
    val requirementIds: List<String>,
    val satisfiable: Boolean,
)

@Serializable
data class WalletCredentialSelection(
    val selectedCredentialIdsByRequirement: Map<String, List<String>>,
)

@Serializable
data class WalletDisclosureSummary(
    val verifier: WalletCounterpartySummary? = null,
    val requestedClaims: List<WalletClaimDescriptor> = emptyList(),
    val selectedCredentialIds: List<String> = emptyList(),
    val claimValuesRevealed: Boolean = false,
)

@Serializable
data class WalletClaimDescriptor(
    val path: List<JsonElement>,
    val labelKey: String? = null,
    val intentToRetain: Boolean? = null,
    val valueAvailable: Boolean = false,
) {
    init {
        requireWalletInteractionLocalizationKey("labelKey", labelKey)
    }
}

@Serializable
data class WalletDeferredRetrievalSummary(
    val intervalSeconds: Int? = null,
    val attempt: Int? = null,
    val resumable: Boolean = true,
)

@Serializable
data class WalletSecurityChallenge(
    val challengeId: String,
    val kind: WalletSecurityChallengeKind,
    val reasonKey: String,
    val arguments: Map<String, String> = emptyMap(),
    val requiredAssurance: WalletSecurityAssurance = WalletSecurityAssurance.USER_PRESENT,
) {
    init {
        require(challengeId.isNotBlank()) { "wallet_interaction_security_challenge_id_blank" }
        requireWalletInteractionLocalizationKey("reasonKey", reasonKey)
    }
}

@Serializable
enum class WalletSecurityChallengeKind {
    PASSKEY,
    PIN,
    BIOMETRIC,
    LOCAL_HSM_UNLOCK,
    LOCAL_TEE_UNLOCK,
    REMOTE_HSM_UNLOCK,
    REMOTE_KEY_AUTHORIZATION,
    WALLET_UNIT_POLICY,
}

@Serializable
data class WalletSecurityGrant(
    val grantId: String,
    val assurance: WalletSecurityAssurance,
    val expiresAtEpochSeconds: Long? = null,
    val evidence: Map<String, String> = emptyMap(),
)

/** Typed result for one-time attended grant validation at the protocol boundary. */
sealed interface WalletSecurityGrantValidation {
    data object Valid : WalletSecurityGrantValidation

    data class Invalid(val reasonKey: String) : WalletSecurityGrantValidation
}

/**
 * Validates the consumed grant against the exact outstanding challenge before protocol state can
 * advance. Consumption provides one-time use; this function adds challenge, expiry, assurance,
 * Wallet Unit, audience and operation binding checks.
 */
fun WalletSecurityGrant.validateFor(
    challenge: WalletSecurityChallenge?,
    walletUnitId: String,
    audience: String?,
    nowEpochSeconds: Long,
): WalletSecurityGrantValidation {
    challenge ?: return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.challenge_missing")
    if (grantId != challenge.challengeId || evidence["challenge_id"] != challenge.challengeId) {
        return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.challenge_binding_invalid")
    }
    val expiry = expiresAtEpochSeconds
        ?: return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_expiry_missing")
    if (expiry <= nowEpochSeconds) return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_expired")
    if (!assurance.satisfies(challenge.requiredAssurance)) {
        return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_assurance_insufficient")
    }
    challenge.arguments["wallet_unit_id"]?.let { expected ->
        if (expected != walletUnitId || evidence["wallet_unit_id"] != expected) {
            return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_wallet_unit_invalid")
        }
    }
    challenge.arguments["audience"]?.let { expected ->
        if (expected != audience || evidence["audience"] != expected) {
            return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_audience_invalid")
        }
    }
    challenge.arguments["operation_binding"]?.let { expected ->
        if (evidence["operation_binding"] != expected) {
            return WalletSecurityGrantValidation.Invalid("wallet.interaction.security.grant_operation_binding_invalid")
        }
    }
    return WalletSecurityGrantValidation.Valid
}

private fun WalletSecurityAssurance.satisfies(required: WalletSecurityAssurance): Boolean =
    when (required) {
        WalletSecurityAssurance.NONE -> true
        WalletSecurityAssurance.USER_PRESENT -> this != WalletSecurityAssurance.NONE
        WalletSecurityAssurance.PASSKEY -> this == WalletSecurityAssurance.PASSKEY
        WalletSecurityAssurance.PIN -> this == WalletSecurityAssurance.PASSKEY || this == WalletSecurityAssurance.PIN || this == WalletSecurityAssurance.BIOMETRIC
        WalletSecurityAssurance.BIOMETRIC -> this == WalletSecurityAssurance.PASSKEY || this == WalletSecurityAssurance.BIOMETRIC
        WalletSecurityAssurance.HARDWARE_BACKED -> this == WalletSecurityAssurance.HARDWARE_BACKED || this == WalletSecurityAssurance.REMOTE_AUTHORIZED
        WalletSecurityAssurance.REMOTE_AUTHORIZED -> this == WalletSecurityAssurance.REMOTE_AUTHORIZED
    }

@Serializable
enum class WalletSecurityAssurance {
    NONE,
    USER_PRESENT,
    PASSKEY,
    PIN,
    BIOMETRIC,
    HARDWARE_BACKED,
    REMOTE_AUTHORIZED,
}

@Serializable
data class WalletInteractionAction(
    val type: WalletInteractionActionType,
    val implementationId: String? = null,
    val selection: WalletCredentialSelection? = null,
    val sensitiveInputRef: WalletInteractionSensitiveInputRef? = null,
    val rememberDecision: Boolean = false,
    val counterpartyAssociation: WalletCounterpartyAssociationDecision? = null,
) {
    init {
        when (type) {
            WalletInteractionActionType.SUBMIT_TX_CODE -> {
                require(sensitiveInputRef != null) { "wallet_interaction_action_tx_code_ref_missing" }
            }

            WalletInteractionActionType.AUTH_CALLBACK -> {
                require(sensitiveInputRef != null) { "wallet_interaction_action_auth_callback_ref_missing" }
            }

            WalletInteractionActionType.CHOOSE_IMPLEMENTATION -> {
                require(!implementationId.isNullOrBlank()) { "wallet_interaction_action_implementation_id_missing" }
            }

            WalletInteractionActionType.SELECT_CREDENTIALS -> {
                require(selection != null) { "wallet_interaction_action_selection_missing" }
            }

            WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE -> {
                require(sensitiveInputRef != null) { "wallet_interaction_action_security_grant_ref_missing" }
            }

            WalletInteractionActionType.RESOLVE_COUNTERPARTY_CONTACT -> {
                require(counterpartyAssociation != null) { "wallet_interaction_action_counterparty_association_missing" }
            }

            else -> {
                Unit
            }
        }
    }

    companion object {
        fun continueFlow(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CONTINUE)

        fun resolveCounterpartyContact(decision: WalletCounterpartyAssociationDecision): WalletInteractionAction =
            WalletInteractionAction(WalletInteractionActionType.RESOLVE_COUNTERPARTY_CONTACT, counterpartyAssociation = decision)

        fun decline(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.DECLINE)

        fun submitTxCode(ref: WalletInteractionSensitiveInputRef): WalletInteractionAction =
            WalletInteractionAction(WalletInteractionActionType.SUBMIT_TX_CODE, sensitiveInputRef = ref)

        fun authCallback(ref: WalletInteractionSensitiveInputRef): WalletInteractionAction =
            WalletInteractionAction(WalletInteractionActionType.AUTH_CALLBACK, sensitiveInputRef = ref)

        fun chooseImplementation(adapterId: String): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION, implementationId = adapterId)

        fun selectCredentials(selection: WalletCredentialSelection): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.SELECT_CREDENTIALS, selection = selection)

        fun revealClaimValues(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.REVEAL_CLAIM_VALUES)

        fun approveSecurityChallenge(ref: WalletInteractionSensitiveInputRef): WalletInteractionAction =
            WalletInteractionAction(WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE, sensitiveInputRef = ref)

        fun retryDeferredRetrieval(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.RETRY_DEFERRED_RETRIEVAL)

        fun resumeDeferredRetrieval(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.RESUME_DEFERRED_RETRIEVAL)

        fun cancel(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CANCEL)
    }
}

@Serializable
enum class WalletInteractionActionType {
    @SerialName("resolve_counterparty_contact")
    RESOLVE_COUNTERPARTY_CONTACT,

    @SerialName("continue")
    CONTINUE,

    @SerialName("decline")
    DECLINE,

    @SerialName("submit_tx_code")
    SUBMIT_TX_CODE,

    @SerialName("auth_callback")
    AUTH_CALLBACK,

    @SerialName("choose_implementation")
    CHOOSE_IMPLEMENTATION,

    @SerialName("select_credentials")
    SELECT_CREDENTIALS,

    @SerialName("reveal_claim_values")
    REVEAL_CLAIM_VALUES,

    @SerialName("approve_security_challenge")
    APPROVE_SECURITY_CHALLENGE,

    @SerialName("retry_deferred_retrieval")
    RETRY_DEFERRED_RETRIEVAL,

    @SerialName("resume_deferred_retrieval")
    RESUME_DEFERRED_RETRIEVAL,

    @SerialName("cancel")
    CANCEL,
}

private fun stableFingerprint(value: String): String? {
    if (value.isBlank()) return null
    var hash = 1125899906842597L
    value.forEach { hash = 31 * hash + it.code }
    return hash.toString(16)
}

internal fun requireWalletInteractionLocalizationKey(
    fieldName: String,
    value: String?,
) {
    if (value == null) return
    require(walletInteractionLocalizationKeyPattern.matches(value)) {
        "wallet_interaction_localization_key_invalid"
    }
}

private val walletInteractionLocalizationKeyPattern = Regex("""[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)+""")
