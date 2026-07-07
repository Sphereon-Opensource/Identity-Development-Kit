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

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class WalletInstance(
    val id: String,
    val ownerSubjectRef: IdentifierRef,
    val label: String,
    val purpose: WalletInstancePurpose,
    val storageProfileId: String,
    val defaultHolderKeyPolicyId: String,
    val trustDomainId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "WalletInstance.id must not be blank" }
        require(storageProfileId.isNotBlank()) { "WalletInstance.storageProfileId must not be blank" }
        require(defaultHolderKeyPolicyId.isNotBlank()) { "WalletInstance.defaultHolderKeyPolicyId must not be blank" }
    }
}

@Serializable
enum class WalletInstancePurpose {
    PERSONAL,
    WORK,
    BUSINESS,
}

@Serializable
data class StorageProfile(
    val id: String,
    val walletInstanceId: String,
    val mode: WalletStorageMode,
    val localStoreRef: StoreRef? = null,
    val remoteVaultRef: StoreRef? = null,
    val encryptionPolicyId: String,
    val syncPolicyId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "StorageProfile.id must not be blank" }
        require(walletInstanceId.isNotBlank()) { "StorageProfile.walletInstanceId must not be blank" }
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
    val walletInstanceId: String,
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
        require(walletInstanceId.isNotBlank()) { "CredentialInstance.walletInstanceId must not be blank" }
        require(credentialRecordId.isNotBlank()) { "CredentialInstance.credentialRecordId must not be blank" }
        require(raw == null || raw.isNotBlank()) { "CredentialInstance.raw must not be blank when present" }
    }

    fun requireRaw(): String = raw ?: error("CredentialInstance.raw is not loaded; open the credential through WalletCredentialStore.getCredential first")

    fun withoutRaw(): CredentialInstance = copy(raw = null)
}

@Serializable
data class CredentialRecord(
    val id: String,
    val walletInstanceId: String,
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
        require(walletInstanceId.isNotBlank()) { "CredentialRecord.walletInstanceId must not be blank" }
        require(credentialTypeRefs.isNotEmpty()) { "CredentialRecord.credentialTypeRefs must not be empty" }
        require(instances.all { it.walletInstanceId == walletInstanceId }) {
            "All credential instances must belong to the same walletInstanceId"
        }
        require(instances.all { it.credentialRecordId == id }) {
            "All credential instances must belong to the same credential record"
        }
    }

    fun displayName(locale: String? = null): String? = (display.credentialDisplay.firstOrNull { it.locale == locale } ?: display.credentialDisplay.firstOrNull())?.name

    fun withAddedInstance(instance: CredentialInstance): CredentialRecord {
        require(instance.walletInstanceId == walletInstanceId) { "Instance walletInstanceId mismatch" }
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
        require(instance.walletInstanceId == walletInstanceId) { "Instance walletInstanceId mismatch" }
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

    fun metadata(now: Instant): CredentialMetadata {
        val activeInstances = instances.filter { it.lifecycleState == CredentialLifecycleState.ACTIVE }
        val issuedAt = instances.mapNotNull { it.issuedAt ?: it.validity.validFrom }.minOrNull()
        val expiresAt = instances.mapNotNull { it.validity.validUntil }.maxOrNull()
        val lifecycleState = lifecycleSummaryState()

        return CredentialMetadata(
            credentialRecordId = id,
            walletInstanceId = walletInstanceId,
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
    val walletInstanceId: String,
    val protocol: IssuanceProtocol = IssuanceProtocol.OID4VCI,
    val issuerRef: IdentifierRef,
    val credentialIssuerUrl: String,
    val authorizationServerUrl: String? = null,
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val expectedCredentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val holderKeyRef: KeyRef? = null,
    val status: IssuanceSessionStatus,
    val deferred: DeferredIssuanceState? = null,
    val notification: IssuanceNotificationState? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "IssuanceSession.id must not be blank" }
        require(walletInstanceId.isNotBlank()) { "IssuanceSession.walletInstanceId must not be blank" }
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
    val walletInstanceId: String,
    val credentialRecordId: String?,
    val operationType: WalletOperationType,
    val baseRemoteRevision: String?,
    val createdByDeviceId: String,
    val createdAt: Instant,
    val payloadRef: BodyStorageRef? = null,
) {
    init {
        require(id.isNotBlank()) { "WalletOperation.id must not be blank" }
        require(walletInstanceId.isNotBlank()) { "WalletOperation.walletInstanceId must not be blank" }
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
