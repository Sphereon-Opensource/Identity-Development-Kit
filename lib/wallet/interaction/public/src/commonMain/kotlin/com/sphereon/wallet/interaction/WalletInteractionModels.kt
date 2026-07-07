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

@Serializable
enum class WalletInteractionExecutionMode {
    LOCAL,
    BACKEND,
    SPLIT,
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
    val walletInstanceId: String,
    val entryPoint: WalletEntryPoint,
    val executionMode: WalletInteractionExecutionMode = WalletInteractionExecutionMode.LOCAL,
    val requestedFlowKinds: List<WalletInteractionFlowKind> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(walletInstanceId.isNotBlank()) { "wallet_interaction_wallet_instance_id_blank" }
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
    ReceivedCredentialReview,
    Sharing,
    Completed,
    Cancelled,
    Failed,
}

@Serializable
data class WalletInteractionState(
    val sessionId: WalletInteractionSessionId,
    val walletInstanceId: String,
    val status: WalletInteractionStatus,
    val revision: Long = 0,
    val flowKind: WalletInteractionFlowKind? = null,
    val activity: WalletInteractionActivitySummary? = null,
    val protocol: WalletProtocol? = null,
    val adapterId: String? = null,
    val entryPoint: WalletEntryPointSummary? = null,
    val implementationChoices: List<WalletImplementationChoice> = emptyList(),
    val counterparty: WalletCounterpartySummary? = null,
    val trust: WalletCounterpartyTrustSummary? = null,
    val credentialOffer: WalletCredentialOfferSummary? = null,
    val credentialPreview: List<WalletCredentialPreview> = emptyList(),
    val credentialSelection: WalletCredentialSelectionRequest? = null,
    val disclosure: WalletDisclosureSummary? = null,
    val securityChallenge: WalletSecurityChallenge? = null,
    val authorizationUrl: String? = null,
    val txCode: WalletTxCodeSpec? = null,
    val deferred: WalletDeferredRetrievalSummary? = null,
    val message: WalletDisplayMessage? = null,
    val error: WalletInteractionError? = null,
    val terminal: Boolean = false,
) {
    init {
        require(walletInstanceId.isNotBlank()) { "wallet_interaction_wallet_instance_id_blank" }
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
                walletInstanceId = input.walletInstanceId,
                status = WalletInteractionStatus.ResolvingEntryPoint,
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
    val metadata: Map<String, String> = emptyMap(),
)

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
    val logoUri: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
)

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
    val requiredClaimPaths: List<List<String>> = emptyList(),
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
    val path: List<String>,
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

@Serializable
enum class WalletSecurityAssurance {
    NONE,
    USER_PRESENT,
    PIN,
    BIOMETRIC,
    HARDWARE_BACKED,
    REMOTE_AUTHORIZED,
}

@Serializable
data class WalletInteractionAction(
    val type: WalletInteractionActionType,
    val value: String? = null,
    val implementationId: String? = null,
    val selection: WalletCredentialSelection? = null,
    val authorizationCallback: String? = null,
    val securityGrant: WalletSecurityGrant? = null,
    val rememberDecision: Boolean = false,
) {
    init {
        when (type) {
            WalletInteractionActionType.SUBMIT_TX_CODE -> {
                require(!value.isNullOrBlank()) { "wallet_interaction_action_tx_code_missing" }
            }

            WalletInteractionActionType.AUTH_CALLBACK -> {
                require(!authorizationCallback.isNullOrBlank()) { "wallet_interaction_action_auth_callback_missing" }
            }

            WalletInteractionActionType.CHOOSE_IMPLEMENTATION -> {
                require(!implementationId.isNullOrBlank()) { "wallet_interaction_action_implementation_id_missing" }
            }

            WalletInteractionActionType.SELECT_CREDENTIALS -> {
                require(selection != null) { "wallet_interaction_action_selection_missing" }
            }

            WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE -> {
                require(securityGrant != null) { "wallet_interaction_action_security_grant_missing" }
            }

            else -> {
                Unit
            }
        }
    }

    companion object {
        fun continueFlow(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CONTINUE)

        fun decline(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.DECLINE)

        fun submitTxCode(value: String): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.SUBMIT_TX_CODE, value = value)

        fun authCallback(callbackUri: String): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.AUTH_CALLBACK, authorizationCallback = callbackUri)

        fun chooseImplementation(adapterId: String): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION, implementationId = adapterId)

        fun selectCredentials(selection: WalletCredentialSelection): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.SELECT_CREDENTIALS, selection = selection)

        fun revealClaimValues(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.REVEAL_CLAIM_VALUES)

        fun approveSecurityChallenge(grant: WalletSecurityGrant): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE, securityGrant = grant)

        fun acceptReceivedCredential(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.ACCEPT_RECEIVED_CREDENTIAL)

        fun declineReceivedCredential(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.DECLINE_RECEIVED_CREDENTIAL)

        fun retryDeferredRetrieval(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.RETRY_DEFERRED_RETRIEVAL)

        fun resumeDeferredRetrieval(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.RESUME_DEFERRED_RETRIEVAL)

        fun cancel(): WalletInteractionAction = WalletInteractionAction(WalletInteractionActionType.CANCEL)
    }
}

@Serializable
enum class WalletInteractionActionType {
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

    @SerialName("accept_received_credential")
    ACCEPT_RECEIVED_CREDENTIAL,

    @SerialName("decline_received_credential")
    DECLINE_RECEIVED_CREDENTIAL,

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
