package com.sphereon.wallet.credential

import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val META_WALLET_UNIT_ID = "wallet-work"
private val META_NOW = Instant.fromEpochSeconds(1_800_000_000)
private val META_ISSUER_REF = IdentifierRef(type = IdentifierType.DID, value = "did:ex:issuer")
private val META_SUBJECT_REF = IdentifierRef(type = IdentifierType.DID, value = "did:ex:subject")
private val META_RP_REF = IdentifierRef(type = IdentifierType("client_id"), value = "https://rp.example")
private val META_TYPE_REF =
    CredentialTypeRef(
        format = CredentialFormat.JWT_VC_JSON,
        kind = CredentialTypeRefKind.W3C_VC_TYPE,
        value = "UniversityDegreeCredential",
        source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
        primary = true,
    )

private fun metaInstance(
    id: String,
    state: CredentialLifecycleState,
    bindingRefs: List<CredentialBindingRef> = emptyList(),
) = CredentialInstance(
    id = id,
    walletUnitId = META_WALLET_UNIT_ID,
    credentialRecordId = "record-meta",
    format = CredentialFormat.JWT_VC_JSON,
    raw = "jwt-$id",
    bodyStorageRef = BodyStorageRef(BodyStorageKind.WALLET_STORE, "wallet-units/$META_WALLET_UNIT_ID/credentials/record-meta/instances/$id/body"),
    holderKeyRef = KeyRef(alias = "key-$id"),
    lifecycleState = state,
    validity = CredentialValidityWindow(validFrom = Instant.fromEpochSeconds(1_700_000_000), validUntil = Instant.fromEpochSeconds(1_950_000_000)),
    bindingRefs = bindingRefs,
    issuedAt = Instant.fromEpochSeconds(1_700_000_000),
    storedAt = META_NOW,
    updatedAt = META_NOW,
)

private fun metaRecord() =
    CredentialRecord(
        id = "record-meta",
        walletUnitId = META_WALLET_UNIT_ID,
        issuerRef = META_ISSUER_REF,
        subjectRefs = listOf(META_SUBJECT_REF),
        format = CredentialFormat.JWT_VC_JSON,
        credentialTypeRefs = setOf(META_TYPE_REF),
        instances =
            listOf(
                metaInstance("ci-1", CredentialLifecycleState.ACTIVE),
                metaInstance(
                    "ci-2",
                    CredentialLifecycleState.ACTIVE,
                    bindingRefs = listOf(CredentialBindingRef(META_RP_REF, META_NOW, "presentation-1")),
                ),
                metaInstance("ci-3", CredentialLifecycleState.EXPIRED),
            ),
        issuanceProvenance =
            IssuanceProvenance(
                credentialIssuerUrl = META_ISSUER_REF.value,
                credentialConfigurationId = "UniversityDegree",
                issuedAt = META_NOW,
            ),
        createdAt = META_NOW,
        updatedAt = META_NOW,
    )

class CredentialMetadataTest {
    @Test
    fun metadataDerivedFromCredentialRecordWithoutCredentialBodyClaims() {
        val meta = metaRecord().metadata(META_NOW)

        assertEquals("record-meta", meta.credentialRecordId)
        assertEquals(META_WALLET_UNIT_ID, meta.walletUnitId)
        assertEquals(META_ISSUER_REF, meta.issuerRef)
        assertEquals(listOf(META_SUBJECT_REF), meta.subjectRefs)
        assertEquals(CredentialFormat.JWT_VC_JSON, meta.format)
        assertEquals(setOf(META_TYPE_REF), meta.credentialTypeRefs)
        assertEquals("UniversityDegree", meta.credentialConfigurationId)
        assertEquals(3, meta.instanceCount)
        assertEquals(2, meta.activeInstanceCount)
        assertEquals(1, meta.boundInstanceCount)
        assertEquals(CredentialLifecycleState.ACTIVE, meta.lifecycleSummary.lifecycleState)
        assertEquals(CredentialValidityState.VALID, meta.lifecycleSummary.validityState)
    }

    @Test
    fun metadataJsonRoundTripAndFilterMatching() {
        val meta = metaRecord().metadata(META_NOW)
        val json = Json.encodeToString(meta)
        val decoded = Json.decodeFromString<CredentialMetadata>(json)

        assertEquals(meta, decoded)
        assertEquals(true, decoded.matches(CredentialMetadataFilter(credentialTypeRefs = setOf(META_TYPE_REF))))
        assertEquals(false, decoded.matches(CredentialMetadataFilter(credentialConfigurationId = "OtherConfig")))
    }
}
