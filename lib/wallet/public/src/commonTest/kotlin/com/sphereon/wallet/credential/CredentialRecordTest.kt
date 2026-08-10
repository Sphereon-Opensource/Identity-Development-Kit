package com.sphereon.wallet.credential

import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val WALLET_UNIT_ID = "wallet-personal"

private val NOW = Instant.fromEpochSeconds(1_800_000_000)
private val ISSUER_REF = IdentifierRef(type = IdentifierType.DID, value = "did:ex:issuer")
private val TYPE_REF =
    CredentialTypeRef(
        format = CredentialFormat.SD_JWT_VC,
        kind = CredentialTypeRefKind.SD_JWT_VCT,
        value = "https://credentials.example.com/employee",
        source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
        primary = true,
    )

private fun instance(
    id: String,
    state: CredentialLifecycleState = CredentialLifecycleState.ACTIVE,
) = CredentialInstance(
    id = id,
    walletUnitId = WALLET_UNIT_ID,
    credentialRecordId = "record-1",
    format = CredentialFormat.SD_JWT_VC,
    raw = "raw-$id",
    bodyStorageRef = BodyStorageRef(BodyStorageKind.WALLET_STORE, "wallet-units/$WALLET_UNIT_ID/credentials/record-1/instances/$id/body"),
    holderKeyRef = KeyRef(alias = "key-$id"),
    lifecycleState = state,
    validity = CredentialValidityWindow(validFrom = Instant.fromEpochSeconds(1_700_000_000), validUntil = Instant.fromEpochSeconds(1_900_000_000)),
    issuedAt = Instant.fromEpochSeconds(1_700_000_000),
    storedAt = NOW,
    updatedAt = NOW,
)

private fun record() =
    CredentialRecord(
        id = "record-1",
        walletUnitId = WALLET_UNIT_ID,
        issuerRef = ISSUER_REF,
        format = CredentialFormat.SD_JWT_VC,
        credentialTypeRefs = setOf(TYPE_REF),
        display = CredentialDisplayMetadata(credentialDisplay = listOf(CredentialDisplayProperties(name = "Employee Credential", locale = "en"))),
        instances = listOf(instance("ci-1")),
        issuanceProvenance =
            IssuanceProvenance(
                credentialIssuerUrl = ISSUER_REF.value,
                credentialConfigurationId = "EmployeeCredential",
                expectedCredentialTypeRefs = setOf(TYPE_REF.copy(source = CredentialTypeRefSource.ISSUER_METADATA)),
                issuedAt = NOW,
            ),
        createdAt = NOW,
        updatedAt = NOW,
    )

class CredentialRecordTest {
    @Test
    fun credentialRecordRoundTripsAndKeepsTypeRefsSeparateFromConfigurationId() {
        val record = record()

        val json = Json.encodeToString(record)
        val decoded = Json.decodeFromString<CredentialRecord>(json)

        assertEquals(record, decoded)
        assertEquals("EmployeeCredential", decoded.issuanceProvenance?.credentialConfigurationId)
        assertEquals(setOf(TYPE_REF), decoded.credentialTypeRefs)
        assertEquals("Employee Credential", decoded.displayName("en"))
    }

    @Test
    fun recordHelpersUseCredentialInstances() {
        val record = record()
        val added = record.withAddedInstance(instance("ci-2"))

        assertEquals(2, added.instances.size)
        assertNotNull(added.presentableInstance(NOW))
    }

    @Test
    fun refreshAddsNewInstanceAndSupersedesPreviousActiveInstance() {
        val original =
            record().copy(
                refreshState =
                    RefreshState(
                        refreshMethod = CredentialRefreshMethod.OID4VCI_REISSUANCE,
                        policy = RefreshPolicy(supersedePreviousActiveInstance = true),
                    ),
            )
        val refreshedAt = Instant.fromEpochSeconds(1_800_000_100)
        val refreshed =
            instance("ci-2").copy(
                replacesInstanceId = "ci-1",
                raw = "raw-ci-2-refreshed",
                storedAt = refreshedAt,
                updatedAt = refreshedAt,
            )

        val updated = original.withRefreshedInstance(refreshed)

        assertEquals(2, updated.instances.size)
        assertEquals(CredentialLifecycleState.SUPERSEDED, updated.instances.first { it.id == "ci-1" }.lifecycleState)
        assertEquals(CredentialLifecycleState.ACTIVE, updated.instances.first { it.id == "ci-2" }.lifecycleState)
        assertEquals("raw-ci-1", original.instances.first().raw)
        assertEquals(refreshedAt, updated.refreshState?.lastRefreshAt)
        assertEquals(original.syncState.localRevision + 1, updated.syncState.localRevision)
    }

    @Test
    fun refreshKeepsChangedActualTypeRefsOnSameRecord() {
        val newTypeRef =
            TYPE_REF.copy(
                value = "https://credentials.example.com/employee-v2",
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
            )
        val original =
            record().copy(
                refreshState =
                    RefreshState(
                        refreshMethod = CredentialRefreshMethod.OID4VCI_REISSUANCE,
                    ),
            )
        val refreshed = instance("ci-2").copy(replacesInstanceId = "ci-1")

        val updated = original.withRefreshedInstance(refreshed, actualTypeRefs = setOf(newTypeRef))

        assertEquals(setOf(TYPE_REF, newTypeRef), updated.credentialTypeRefs)
        val diagnostic = updated.refreshState?.diagnostics?.single()
        assertEquals(RefreshDiagnosticCode.CREDENTIAL_TYPE_REF_CHANGED, diagnostic?.code)
        assertEquals(setOf(TYPE_REF), diagnostic?.previousCredentialTypeRefs)
        assertEquals(setOf(newTypeRef), diagnostic?.actualCredentialTypeRefs)
    }

    @Test
    fun statusOnlyUpdateDoesNotCreateRefreshInstance() {
        val statusAt = Instant.fromEpochSeconds(1_800_000_200)
        val status = CredentialStatusSnapshot(status = "revoked", checkedAt = statusAt, source = "status-list")

        val updated = record().withInstanceStatus("ci-1", status, CredentialLifecycleState.REVOKED)

        assertEquals(1, updated.instances.size)
        assertEquals(status, updated.instances.single().status)
        assertEquals(CredentialLifecycleState.REVOKED, updated.instances.single().lifecycleState)
        assertEquals(statusAt, updated.updatedAt)
        assertFalse(updated.needsRefresh)
    }

    @Test
    fun freshInstanceIsNotConsumedUntilABindingRefIsRecorded() {
        val fresh = instance("ci-1")
        assertFalse(fresh.isConsumed)

        val presented =
            fresh.copy(
                bindingRefs =
                    listOf(
                        CredentialBindingRef(
                            verifierRef = IdentifierRef(type = IdentifierType.DID, value = "did:ex:verifier"),
                            boundAt = NOW,
                        ),
                    ),
            )
        assertTrue(presented.isConsumed)
    }

    @Test
    fun unusedActiveInstancesExcludesOnlyThePresentedInstance() {
        val presentedInstance =
            instance("ci-1").copy(
                bindingRefs =
                    listOf(
                        CredentialBindingRef(
                            verifierRef = IdentifierRef(type = IdentifierType.DID, value = "did:ex:verifier"),
                            boundAt = NOW,
                        ),
                    ),
            )
        val unpresentedInstance = instance("ci-2")
        val record = record().copy(instances = listOf(presentedInstance, unpresentedInstance))

        assertEquals(1, record.unusedActiveInstanceCount(NOW))
        assertEquals(listOf(unpresentedInstance), record.unusedActiveInstances(NOW))
    }

    @Test
    fun presentableInstanceStillReturnsAConsumedInstanceProvingNoExclusion() {
        val presentedInstance =
            instance("ci-1").copy(
                bindingRefs =
                    listOf(
                        CredentialBindingRef(
                            verifierRef = IdentifierRef(type = IdentifierType.DID, value = "did:ex:verifier"),
                            boundAt = NOW,
                        ),
                    ),
            )
        val record = record().copy(instances = listOf(presentedInstance))

        assertTrue(presentedInstance.isConsumed)
        assertEquals(presentedInstance, record.presentableInstance(NOW))
    }

    @Test
    fun unusedActiveInstancesExcludesExpiredAndNotYetValidUnpresentedInstances() {
        val expiredInstance =
            instance("ci-2").copy(
                validity = CredentialValidityWindow(validFrom = Instant.fromEpochSeconds(1_600_000_000), validUntil = Instant.fromEpochSeconds(1_700_000_000)),
            )
        val notYetValidInstance =
            instance("ci-3").copy(
                validity = CredentialValidityWindow(validFrom = Instant.fromEpochSeconds(1_900_000_000), validUntil = Instant.fromEpochSeconds(2_000_000_000)),
            )
        val activeUnpresentedInstance = instance("ci-1")
        val record = record().copy(instances = listOf(activeUnpresentedInstance, expiredInstance, notYetValidInstance))

        assertEquals(1, record.unusedActiveInstanceCount(NOW))
        assertEquals(listOf(activeUnpresentedInstance), record.unusedActiveInstances(NOW))
    }
}
