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

package com.sphereon.wallet.credential

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.WalletSecureComponentWalletBinding
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The per-unit PROFILE configuration (D10 vocabulary): one wallet unit has exactly one profile,
 * and the unit's single wallet-unit instance is what that profile is activated onto. This record
 * is the durable policy root for a wallet unit; it is not itself a device instance or an
 * activation - see [WalletActivation] for the per-device activation state bound to this unit.
 */
@Serializable
data class WalletUnitProfile(
    val id: String,
    val ownerSubjectRef: IdentifierRef,
    val label: String,
    val purpose: WalletProfilePurpose,
    val storageProfileId: String,
    val defaultHolderKeyPolicyId: String,
    val trustDomainId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val profileType: WalletProfilePersona = WalletProfilePersona.PRIVATE_PERSON,
    val holderPartyRoleRef: IdentifierRef? = null,
    val activationPolicyId: String? = null,
    /** Server-controlled, typed signing identifiers keyed by the exact opaque holder-key alias. */
    val holderVerificationMethods: Map<String, WalletHolderVerificationMethod> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "WalletUnitProfile.id must not be blank" }
        require(storageProfileId.isNotBlank()) { "WalletUnitProfile.storageProfileId must not be blank" }
        require(defaultHolderKeyPolicyId.isNotBlank()) { "WalletUnitProfile.defaultHolderKeyPolicyId must not be blank" }
        require(holderVerificationMethods.keys.all(String::isNotBlank)) { "WalletUnitProfile holder verification-method keys must not be blank" }
    }
}

/** Identifier-resolution source explicitly associated with a wallet holder key. */
@Serializable
enum class WalletHolderIdentifierKind {
    DID_VERIFICATION_METHOD,
    JWKS_KID,
    MANAGED_KID,
    X509,
}

/**
 * Public signing identifier admitted by wallet policy for one opaque holder-key alias.
 *
 * The alias is deliberately not represented here: it locates private material inside WSCA, while
 * this value selects the public identifier-resolution module used by a verifier.
 */
@Serializable
data class WalletHolderVerificationMethod(
    val value: String,
    /** Explicit holder/controller identity; independent from verification-method URI spelling. */
    val controller: String,
    val kind: WalletHolderIdentifierKind,
    /** Exact signature algorithm of the associated WSCA key; never inferred from [value]. */
    val signingAlgorithm: SignatureAlgorithm,
    val certificateChain: List<String> = emptyList(),
) {
    init {
        require(value.isNotBlank()) { "Wallet holder verification method must not be blank" }
        require(controller.isNotBlank()) { "Wallet holder verification-method controller must not be blank" }
        require(
            (kind == WalletHolderIdentifierKind.X509 && certificateChain.isNotEmpty() && certificateChain.all(String::isNotBlank)) ||
                (kind != WalletHolderIdentifierKind.X509 && certificateChain.isEmpty()),
        ) { "Only X509 holder identifiers carry a non-empty certificate chain" }
    }
}

@Serializable
enum class WalletProfilePurpose {
    PERSONAL,
    WORK,
    BUSINESS,
}

@Serializable
enum class WalletProfilePersona {
    PRIVATE_PERSON,
    EMPLOYEE_REPRESENTATIVE,
    ORGANIZATION_MANDATE,
}

/**
 * A device-level activation of a [WalletUnitProfile]'s single wallet-unit instance.
 *
 * D10: a wallet unit has exactly ONE WSCA/WSCD binding, shared by every access surface (agent)
 * that acts on behalf of it - see [WalletAgent]. [secureComponentBinding] therefore carries the
 * unit-level binding, not a binding scoped to this individual activation; every [WalletActivation]
 * that shares the same [walletUnitId] carries an identical [secureComponentBinding] value. Earlier
 * revisions of this model held separate `wscaBinding`/`wscdBinding` fields per activation, which
 * incorrectly implied that e.g. a mobile activation and a web activation of the same unit could
 * diverge onto different secure components; that shape was replaced by this single unit-scoped
 * field. This record and its type have no other production usages or persisted wire format yet,
 * so this is a greenfield model correction, not a migration.
 */
@Serializable
data class WalletActivation(
    val id: String,
    val walletUnitId: String,
    val deviceBindingRef: IdentifierRef,
    val secureComponentBinding: WalletSecureComponentWalletBinding,
    val state: WalletActivationState,
    val authorizedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "WalletActivation.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "WalletActivation.walletUnitId must not be blank" }
    }
}

@Serializable
enum class WalletActivationState {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED,
}

/**
 * A registered access surface acting on behalf of a wallet unit's single wallet-unit instance:
 * a browser tab, a native mobile app install, a native desktop app install, or a headless
 * automation client.
 *
 * D10 (binding): every [WalletAgent] of the same [walletUnitId] shares the unit's ONE WSCA/WSCD
 * binding (see [WalletActivation.secureComponentBinding]); an agent owns NO secure component of
 * its own, by construction this type carries no wsca/wscd/key fields. Agent registration NEVER
 * provisions a secure component. Any agent/device/session integrity evidence produced during
 * registration (for example a passkey ceremony credential id) is provider-side authorization
 * context recorded in [authorizationEvidenceRef]; it is not a wallet-unit-attestation (WUA) object
 * and must never be folded into WIA/KA claims.
 */
@Serializable
data class WalletAgent(
    val agentId: String,
    val walletUnitId: String,
    val walletInstanceId: String,
    val kind: WalletAgentKind,
    val registeredAt: Instant,
    val authorizationEvidenceRef: String? = null,
) {
    init {
        require(agentId.isNotBlank()) { "WalletAgent.agentId must not be blank" }
        require(walletUnitId.isNotBlank()) { "WalletAgent.walletUnitId must not be blank" }
        require(walletInstanceId.isNotBlank()) { "WalletAgent.walletInstanceId must not be blank" }
    }
}

@Serializable
enum class WalletAgentKind {
    BROWSER,
    NATIVE_MOBILE,
    NATIVE_DESKTOP,
    HEADLESS,
}

@Serializable
data class StorageProfile(
    val id: String,
    val walletUnitId: String,
    val mode: WalletStorageMode,
    val localStoreRef: StoreRef? = null,
    val remoteVaultRef: StoreRef? = null,
    val encryptionPolicyId: String,
    val syncPolicyId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "StorageProfile.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "StorageProfile.walletUnitId must not be blank" }
        require(encryptionPolicyId.isNotBlank()) { "StorageProfile.encryptionPolicyId must not be blank" }
    }
}

@Serializable
enum class WalletStorageMode {
    LOCAL,
    REMOTE,
    HYBRID,
}

@Serializable
data class StoreRef(
    val id: String,
    val type: String,
) {
    init {
        require(id.isNotBlank()) { "StoreRef.id must not be blank" }
        require(type.isNotBlank()) { "StoreRef.type must not be blank" }
    }
}

@Serializable
data class KeyRef(
    val alias: String,
    val kid: String? = null,
) {
    init {
        require(alias.isNotBlank()) { "KeyRef.alias must not be blank" }
    }
}

@Serializable
data class SecretRef(
    val id: String,
    val storeRef: StoreRef? = null,
) {
    init {
        require(id.isNotBlank()) { "SecretRef.id must not be blank" }
    }
}

@Serializable
data class BodyStorageRef(
    val kind: BodyStorageKind,
    val path: String,
    val storeRef: StoreRef? = null,
) {
    init {
        require(path.isNotBlank()) { "BodyStorageRef.path must not be blank" }
    }
}

@Serializable
enum class BodyStorageKind {
    WALLET_STORE,
    BLOB,
    VAULT,
    HYBRID,
}

@Serializable
data class CredentialTypeRef(
    val format: CredentialFormat,
    val kind: CredentialTypeRefKind,
    val value: String,
    val source: CredentialTypeRefSource,
    val primary: Boolean = false,
    val issuerScoped: Boolean = false,
) {
    init {
        require(value.isNotBlank()) { "CredentialTypeRef.value must not be blank" }
    }

    fun sameReference(other: CredentialTypeRef): Boolean = format == other.format && kind == other.kind && value == other.value
}

@Serializable
enum class CredentialTypeRefKind {
    SD_JWT_VCT,
    MDOC_DOCTYPE,
    W3C_VC_TYPE,
}

@Serializable
enum class CredentialTypeRefSource {
    ISSUER_METADATA,
    CREDENTIAL_PAYLOAD,
    IMPORT_METADATA,
}

@Serializable
enum class CredentialLifecycleState {
    PENDING,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    REVOKED,
    SUPERSEDED,
    DELETED,
}

@Serializable
enum class CredentialValidityState {
    NOT_YET_VALID,
    VALID,
    EXPIRED,
    UNKNOWN,
}

@Serializable
data class CredentialValidityWindow(
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
) {
    fun stateAt(now: Instant): CredentialValidityState =
        when {
            validFrom != null && now < validFrom -> CredentialValidityState.NOT_YET_VALID
            validUntil != null && now > validUntil -> CredentialValidityState.EXPIRED
            validFrom == null && validUntil == null -> CredentialValidityState.UNKNOWN
            else -> CredentialValidityState.VALID
        }
}

@Serializable
data class CredentialStatusSnapshot(
    val status: String,
    val checkedAt: Instant,
    val source: String? = null,
)

@Serializable
data class CredentialBindingRef(
    val verifierRef: IdentifierRef,
    val boundAt: Instant,
    val presentationId: String? = null,
)

@Serializable
data class CredentialDisplayMetadata(
    val issuerDisplay: List<CredentialDisplayProperties> = emptyList(),
    val credentialDisplay: List<CredentialDisplayProperties> = emptyList(),
    val claims: List<CredentialClaimMetadata> = emptyList(),
)

@Serializable
data class IssuanceProvenance(
    val issuanceSessionId: String? = null,
    val credentialIssuerUrl: String,
    val authorizationServerUrl: String? = null,
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val expectedCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val diagnostics: List<IssuanceDiagnostic> = emptyList(),
    val issuedAt: Instant,
    val notificationId: String? = null,
) {
    init {
        require(credentialIssuerUrl.isNotBlank()) { "IssuanceProvenance.credentialIssuerUrl must not be blank" }
        require(credentialConfigurationId.isNotBlank()) { "IssuanceProvenance.credentialConfigurationId must not be blank" }
    }
}

@Serializable
data class IssuanceDiagnostic(
    val code: IssuanceDiagnosticCode,
    val message: String,
    val expectedCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val actualCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val observedAt: Instant,
) {
    init {
        require(message.isNotBlank()) { "IssuanceDiagnostic.message must not be blank" }
    }
}

@Serializable
enum class IssuanceDiagnosticCode {
    CREDENTIAL_TYPE_REF_MISMATCH,
}

@Serializable
data class RefreshState(
    val refreshMethod: CredentialRefreshMethod,
    val refreshEndpoint: String? = null,
    val refreshTokenRef: SecretRef? = null,
    val lastRefreshAt: Instant? = null,
    val nextEligibleRefreshAt: Instant? = null,
    val policy: RefreshPolicy = RefreshPolicy(),
    val diagnostics: List<RefreshDiagnostic> = emptyList(),
)

@Serializable
enum class CredentialRefreshMethod {
    OID4VCI_REISSUANCE,
    ISSUER_REFRESH_ENDPOINT,
    MANUAL_IMPORT,
}

@Serializable
data class RefreshPolicy(
    val lowWatermark: Int = 1,
    val supersedePreviousActiveInstance: Boolean = true,
) {
    init {
        require(lowWatermark >= 0) { "RefreshPolicy.lowWatermark must be non-negative" }
    }
}

@Serializable
data class RefreshDiagnostic(
    val code: RefreshDiagnosticCode,
    val message: String,
    val previousCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val actualCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val observedAt: Instant,
) {
    init {
        require(message.isNotBlank()) { "RefreshDiagnostic.message must not be blank" }
    }
}

@Serializable
enum class RefreshDiagnosticCode {
    CREDENTIAL_TYPE_REF_CHANGED,
}

@Serializable
data class RecordSyncState(
    val localRevision: Long = 0,
    val remoteRevision: String? = null,
    val deviceId: String? = null,
    val lastSyncedAt: Instant? = null,
    val pendingOperationIds: List<String> = emptyList(),
    val tombstone: Boolean = false,
) {
    init {
        require(localRevision >= 0) { "RecordSyncState.localRevision must be non-negative" }
    }
}

@Serializable
data class CredentialInstance(
    val id: String,
    val walletUnitId: String,
    val credentialRecordId: String,
    val format: CredentialFormat,
    val raw: String? = null,
    val bodyStorageRef: BodyStorageRef,
    val holderKeyRef: KeyRef? = null,
    val lifecycleState: CredentialLifecycleState,
    val validity: CredentialValidityWindow = CredentialValidityWindow(),
    val status: CredentialStatusSnapshot? = null,
    val bindingRefs: List<CredentialBindingRef> = emptyList(),
    val replacesInstanceId: String? = null,
    val issuedAt: Instant? = null,
    val storedAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "CredentialInstance.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "CredentialInstance.walletUnitId must not be blank" }
        require(credentialRecordId.isNotBlank()) { "CredentialInstance.credentialRecordId must not be blank" }
        require(raw == null || raw.isNotBlank()) { "CredentialInstance.raw must not be blank when present" }
    }

    fun requireRaw(): String = raw ?: error("CredentialInstance.raw is not loaded; open the credential through WalletCredentialStore.getCredential first")

    fun withoutRaw(): CredentialInstance = copy(raw = null)

    /**
     * True once this instance has been presented to at least one relying party (ARF Method A one-time-use).
     *
     * Consumption is currently RECORDED but NOT enforced: a presented instance remains presentable
     * until one-time-use enforcement and replenishment land as a follow-up task. This flag does not
     * affect [CredentialRecord.presentableInstance] or [CredentialRecord.needsRefresh].
     */
    val isConsumed: Boolean get() = bindingRefs.isNotEmpty()
}

@Serializable
data class CredentialRecord(
    val id: String,
    val walletUnitId: String,
    val issuerRef: IdentifierRef,
    val subjectRefs: List<IdentifierRef> = emptyList(),
    val format: CredentialFormat,
    val credentialTypeRefs: Set<CredentialTypeRef>,
    val display: CredentialDisplayMetadata = CredentialDisplayMetadata(),
    val instances: List<CredentialInstance>,
    val issuanceProvenance: IssuanceProvenance? = null,
    val refreshState: RefreshState? = null,
    val syncState: RecordSyncState = RecordSyncState(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "CredentialRecord.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "CredentialRecord.walletUnitId must not be blank" }
        require(credentialTypeRefs.isNotEmpty()) { "CredentialRecord.credentialTypeRefs must not be empty" }
        require(instances.all { it.walletUnitId == walletUnitId }) {
            "All credential instances must belong to the same walletUnitId"
        }
        require(instances.all { it.credentialRecordId == id }) {
            "All credential instances must belong to the same credential record"
        }
    }

    fun displayName(locale: String? = null): String? = (display.credentialDisplay.firstOrNull { it.locale == locale } ?: display.credentialDisplay.firstOrNull())?.name

    fun withAddedInstance(instance: CredentialInstance): CredentialRecord {
        require(instance.walletUnitId == walletUnitId) { "Instance walletUnitId mismatch" }
        require(instance.credentialRecordId == id) { "Instance credentialRecordId mismatch" }
        return copy(
            instances = instances + instance,
            updatedAt = instance.updatedAt,
            syncState = syncState.copy(localRevision = syncState.localRevision + 1),
        )
    }

    fun withRefreshedInstance(
        instance: CredentialInstance,
        actualTypeRefs: Set<CredentialTypeRef> = emptySet(),
    ): CredentialRecord {
        require(instance.walletUnitId == walletUnitId) { "Instance walletUnitId mismatch" }
        require(instance.credentialRecordId == id) { "Instance credentialRecordId mismatch" }
        require(instance.raw != null) { "Refreshed credential instance must carry a raw body before it is stored" }
        val supersedePrevious = refreshState?.policy?.supersedePreviousActiveInstance ?: true
        val updatedInstances =
            if (supersedePrevious) {
                instances.map {
                    if (it.lifecycleState == CredentialLifecycleState.ACTIVE) {
                        it.copy(
                            lifecycleState = CredentialLifecycleState.SUPERSEDED,
                            updatedAt = instance.updatedAt,
                        )
                    } else {
                        it
                    }
                }
            } else {
                instances
            }
        val effectiveTypeRefs = actualTypeRefs.ifEmpty { setOfNotNull(instance.formatTypeRef()) }
        val refreshDiagnostics = refreshDiagnostics(effectiveTypeRefs, instance.updatedAt)
        return copy(
            credentialTypeRefs = credentialTypeRefs + effectiveTypeRefs,
            instances = updatedInstances + instance,
            updatedAt = instance.updatedAt,
            refreshState =
                refreshState?.copy(
                    lastRefreshAt = instance.updatedAt,
                    diagnostics = refreshState.diagnostics + refreshDiagnostics,
                ),
            syncState = syncState.copy(localRevision = syncState.localRevision + 1),
        )
    }

    fun withInstanceStatus(
        credentialInstanceId: String,
        status: CredentialStatusSnapshot,
        lifecycleState: CredentialLifecycleState? = null,
    ): CredentialRecord {
        require(credentialInstanceId.isNotBlank()) { "credentialInstanceId must not be blank" }
        require(instances.any { it.id == credentialInstanceId }) { "Credential instance '$credentialInstanceId' was not found" }
        return copy(
            instances =
                instances.map { instance ->
                    if (instance.id == credentialInstanceId) {
                        instance.copy(
                            status = status,
                            lifecycleState = lifecycleState ?: instance.lifecycleState,
                            updatedAt = status.checkedAt,
                        )
                    } else {
                        instance
                    }
                },
            updatedAt = status.checkedAt,
            syncState = syncState.copy(localRevision = syncState.localRevision + 1),
        )
    }

    fun presentableInstance(now: Instant): CredentialInstance? =
        instances.firstOrNull {
            it.lifecycleState == CredentialLifecycleState.ACTIVE &&
                it.validity.stateAt(now) != CredentialValidityState.NOT_YET_VALID &&
                it.validity.stateAt(now) != CredentialValidityState.EXPIRED
        }

    val needsRefresh: Boolean
        get() {
            val refresh = refreshState ?: return false
            return instances.count { it.lifecycleState == CredentialLifecycleState.ACTIVE } < refresh.policy.lowWatermark
        }

    /**
     * Active, in-validity instances that have NOT yet been presented (the pool a future one-time-use
     * policy would draw from). Uses the same active/validity predicate as [presentableInstance], plus
     * excluding instances where [CredentialInstance.isConsumed] is true.
     *
     * This is exposed for a FUTURE one-time-use enforcement and replenishment task; it is NOT yet
     * consulted by [presentableInstance] or [needsRefresh].
     */
    fun unusedActiveInstances(now: Instant): List<CredentialInstance> = instances.filter { it.isActiveAndUnconsumedAt(now) }

    /**
     * Count of [unusedActiveInstances]. A future replenishment lower-watermark will compare against
     * this instead of the raw active instance count.
     *
     * This is exposed for a FUTURE one-time-use enforcement and replenishment task; it is NOT yet
     * consulted by [presentableInstance] or [needsRefresh].
     */
    fun unusedActiveInstanceCount(now: Instant): Int = unusedActiveInstances(now).size

    fun metadata(now: Instant): CredentialMetadata {
        val activeInstances = instances.filter { it.lifecycleState == CredentialLifecycleState.ACTIVE }
        val issuedAt = instances.mapNotNull { it.issuedAt ?: it.validity.validFrom }.minOrNull()
        val expiresAt = instances.mapNotNull { it.validity.validUntil }.maxOrNull()
        val lifecycleState = lifecycleSummaryState()

        return CredentialMetadata(
            credentialRecordId = id,
            walletUnitId = walletUnitId,
            issuerRef = issuerRef,
            subjectRefs = subjectRefs,
            format = format,
            credentialTypeRefs = credentialTypeRefs,
            credentialConfigurationId = issuanceProvenance?.credentialConfigurationId,
            display = display,
            lifecycleSummary =
                CredentialLifecycleSummary(
                    lifecycleState = lifecycleState,
                    validityState = validityStateAt(now),
                    tombstone = syncState.tombstone || deletedAt != null,
                ),
            instanceCount = instances.size,
            activeInstanceCount = activeInstances.size,
            boundInstanceCount = instances.count { it.bindingRefs.isNotEmpty() },
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            updatedAt = updatedAt,
        )
    }

    private fun lifecycleSummaryState(): CredentialLifecycleState =
        when {
            syncState.tombstone || deletedAt != null -> CredentialLifecycleState.DELETED
            instances.any { it.lifecycleState == CredentialLifecycleState.ACTIVE } -> CredentialLifecycleState.ACTIVE
            instances.any { it.lifecycleState == CredentialLifecycleState.PENDING } -> CredentialLifecycleState.PENDING
            instances.any { it.lifecycleState == CredentialLifecycleState.SUSPENDED } -> CredentialLifecycleState.SUSPENDED
            instances.any { it.lifecycleState == CredentialLifecycleState.REVOKED } -> CredentialLifecycleState.REVOKED
            instances.any { it.lifecycleState == CredentialLifecycleState.EXPIRED } -> CredentialLifecycleState.EXPIRED
            instances.any { it.lifecycleState == CredentialLifecycleState.SUPERSEDED } -> CredentialLifecycleState.SUPERSEDED
            else -> CredentialLifecycleState.PENDING
        }

    private fun validityStateAt(now: Instant): CredentialValidityState {
        val instanceStates = instances.map { it.validity.stateAt(now) }
        return when {
            instanceStates.any { it == CredentialValidityState.VALID } -> CredentialValidityState.VALID
            instanceStates.any { it == CredentialValidityState.NOT_YET_VALID } -> CredentialValidityState.NOT_YET_VALID
            instanceStates.any { it == CredentialValidityState.EXPIRED } -> CredentialValidityState.EXPIRED
            else -> CredentialValidityState.UNKNOWN
        }
    }

    /**
     * Mirrors the active/validity predicate used by [presentableInstance] exactly (ACTIVE lifecycle
     * state, not NOT_YET_VALID, not EXPIRED), plus the additional one-time-use consumption check. Keep
     * this in sync with [presentableInstance]'s predicate if that predicate ever changes.
     */
    private fun CredentialInstance.isActiveAndUnconsumedAt(now: Instant): Boolean =
        lifecycleState == CredentialLifecycleState.ACTIVE &&
            validity.stateAt(now) != CredentialValidityState.NOT_YET_VALID &&
            validity.stateAt(now) != CredentialValidityState.EXPIRED &&
            !isConsumed

    private fun CredentialInstance.formatTypeRef(): CredentialTypeRef? = credentialTypeRefs.firstOrNull { it.format == format }

    private fun refreshDiagnostics(
        actualTypeRefs: Set<CredentialTypeRef>,
        observedAt: Instant,
    ): List<RefreshDiagnostic> {
        if (actualTypeRefs.isEmpty()) return emptyList()
        if (actualTypeRefs.referenceKeys().all { it in credentialTypeRefs.referenceKeys() }) return emptyList()
        return listOf(
            RefreshDiagnostic(
                code = RefreshDiagnosticCode.CREDENTIAL_TYPE_REF_CHANGED,
                message = "Refreshed credential type references differ from the existing credential record",
                previousCredentialTypeRefs = credentialTypeRefs,
                actualCredentialTypeRefs = actualTypeRefs,
                observedAt = observedAt,
            ),
        )
    }

    private fun Set<CredentialTypeRef>.referenceKeys(): Set<Triple<CredentialFormat, CredentialTypeRefKind, String>> = map { Triple(it.format, it.kind, it.value) }.toSet()
}

@Serializable
data class IssuanceSession(
    val id: String,
    val walletUnitId: String,
    val protocol: IssuanceProtocol = IssuanceProtocol.OID4VCI,
    val issuerRef: IdentifierRef,
    val credentialIssuerUrl: String,
    val authorizationServerUrl: String? = null,
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val expectedCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val holderKeyRef: KeyRef? = null,
    val holderKeyRefs: List<KeyRef> = emptyList(),
    val status: IssuanceSessionStatus,
    val deferred: DeferredIssuanceState? = null,
    val notification: IssuanceNotificationState? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "IssuanceSession.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "IssuanceSession.walletUnitId must not be blank" }
        require(credentialIssuerUrl.isNotBlank()) { "IssuanceSession.credentialIssuerUrl must not be blank" }
        require(credentialConfigurationId.isNotBlank()) { "IssuanceSession.credentialConfigurationId must not be blank" }
    }
}

@Serializable
enum class IssuanceProtocol {
    OID4VCI,
}

@Serializable
enum class IssuanceSessionStatus {
    CREATED,
    TOKEN_GRANTED,
    DEFERRED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class DeferredIssuanceState(
    val transactionId: String,
    val deferredCredentialEndpoint: String,
    val accessTokenRef: SecretRef,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val nextPollAt: Instant? = null,
    val attempts: Int = 0,
    val lastError: DeferredIssuanceError? = null,
) {
    init {
        require(transactionId.isNotBlank()) { "DeferredIssuanceState.transactionId must not be blank" }
        require(deferredCredentialEndpoint.isNotBlank()) { "DeferredIssuanceState.deferredCredentialEndpoint must not be blank" }
        require(attempts >= 0) { "DeferredIssuanceState.attempts must be non-negative" }
    }
}

@Serializable
data class RetryPolicy(
    val initialDelaySeconds: Long = 5,
    val maxDelaySeconds: Long = 300,
    val maxAttempts: Int? = null,
) {
    init {
        require(initialDelaySeconds >= 0) { "RetryPolicy.initialDelaySeconds must be non-negative" }
        require(maxDelaySeconds >= initialDelaySeconds) { "RetryPolicy.maxDelaySeconds must be >= initialDelaySeconds" }
        require(maxAttempts == null || maxAttempts > 0) { "RetryPolicy.maxAttempts must be positive when set" }
    }
}

@Serializable
data class DeferredIssuanceError(
    val code: String,
    val message: String,
    val observedAt: Instant,
)

@Serializable
data class IssuanceNotificationState(
    val notificationId: String? = null,
    val endpoint: String? = null,
    val lastStatus: String? = null,
    val lastNotifiedAt: Instant? = null,
)

@Serializable
data class WalletOperation(
    val id: String,
    val walletUnitId: String,
    val credentialRecordId: String?,
    val operationType: WalletOperationType,
    val baseRemoteRevision: String?,
    val createdByDeviceId: String,
    val createdAt: Instant,
    val payloadRef: BodyStorageRef? = null,
) {
    init {
        require(id.isNotBlank()) { "WalletOperation.id must not be blank" }
        require(walletUnitId.isNotBlank()) { "WalletOperation.walletUnitId must not be blank" }
        require(createdByDeviceId.isNotBlank()) { "WalletOperation.createdByDeviceId must not be blank" }
    }
}

@Serializable
enum class WalletOperationType {
    PUT_CREDENTIAL,
    DELETE_CREDENTIAL,
    UPDATE_METADATA,
    APPEND_PRESENTATION_BINDING,
}
