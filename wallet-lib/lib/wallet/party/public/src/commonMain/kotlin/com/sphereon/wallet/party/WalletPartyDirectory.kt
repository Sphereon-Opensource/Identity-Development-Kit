/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.wallet.party

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.Identity
import com.sphereon.data.store.party.model.IdentityIdentifier
import com.sphereon.data.store.party.model.IdentityPartyBinding
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.data.store.party.model.Party
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyRef
import com.sphereon.data.store.party.model.PartyType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class WalletPartyDirectoryAuthority {
    LOCAL,
    MANAGED,
}

/**
 * Returns the canonical base DID used only as an automatic Organization grouping key.
 * Full DID and DID URL identifiers remain stored unchanged on the role-specific Identity.
 */
fun canonicalWalletDidSubject(value: String): String? {
    val didUrl = value.trim()
    if (!didUrl.startsWith("did:")) return null
    val methodEnd = didUrl.indexOf(':', startIndex = 4)
    if (methodEnd <= 4) return null
    val method = didUrl.substring(4, methodEnd)
    if (!DID_METHOD.matches(method)) return null
    val subjectEnd = didUrl.indexOfAny(charArrayOf('/', '?', '#'), startIndex = methodEnd + 1).let { if (it < 0) didUrl.length else it }
    val methodSpecificId = didUrl.substring(methodEnd + 1, subjectEnd)
    if (methodSpecificId.isBlank() || methodSpecificId.any(Char::isWhitespace)) return null
    return "did:$method:$methodSpecificId"
}

private val DID_METHOD = Regex("[a-z0-9]+")

/**
 * Explicit Party authority scope. Profiles and agents are access contexts; they are deliberately
 * absent because every agent of one logical Wallet Unit must see the same Business Unit and history.
 */
@Serializable
data class WalletPartyScope(
    val tenantId: String,
    val walletUnitId: String,
    val organizationUnitRef: PartyRef,
) {
    init {
        require(tenantId.isNotBlank()) { "wallet_party_scope_tenant_blank" }
        require(walletUnitId.isNotBlank()) { "wallet_party_scope_wallet_unit_blank" }
        require(organizationUnitRef.type == PartyType.ORGANIZATION_UNIT) { "wallet_party_scope_business_unit_type_invalid" }
        require(organizationUnitRef.partyId.isNotBlank()) { "wallet_party_scope_business_unit_blank" }
    }

    val storageKey: String get() = listOf(tenantId, organizationUnitRef.partyId, walletUnitId).joinToString("|")
}

/** The Business Unit is represented canonically as an Organization Unit Party. */
@Serializable
data class WalletBusinessUnit(
    val party: Party,
) {
    init {
        require(party.partyType == PartyType.ORGANIZATION_UNIT) { "wallet_business_unit_party_type_invalid" }
        require(party.origin == PartyOrigin.MANAGED) { "wallet_business_unit_origin_invalid" }
        require(party.organizationUnitId == null) { "wallet_business_unit_cannot_be_its_own_home_unit" }
    }

    val ref: PartyRef
        get() = PartyRef(partyId = party.partyId.toString(), type = PartyType.ORGANIZATION_UNIT, displayName = party.displayName)
}

/** Provider provisioning input. Local providers create; managed providers validate their assignment. */
@Serializable
data class WalletBusinessUnitProvisioningRequest(
    val tenantId: String,
    val walletUnitId: String,
    val displayName: String,
    val assignedOrganizationUnitRef: PartyRef? = null,
) {
    init {
        require(tenantId.isNotBlank()) { "wallet_business_unit_tenant_blank" }
        require(walletUnitId.isNotBlank()) { "wallet_business_unit_wallet_unit_blank" }
        require(displayName.isNotBlank()) { "wallet_business_unit_display_name_blank" }
        assignedOrganizationUnitRef?.let {
            require(it.type == PartyType.ORGANIZATION_UNIT) { "wallet_business_unit_assignment_type_invalid" }
        }
    }
}

@Serializable
enum class WalletOrganizationIdentityRole {
    ISSUER,
    VERIFIER,
    MDOC_READER,
}

@Serializable
data class WalletPartyIdentifierEvidence(
    val type: IdentifierType,
    /** Exact normalized protocol identifier. It is never replaced by display name or domain. */
    val value: String,
    val primary: Boolean = false,
    val verified: Boolean = false,
) {
    init {
        require(type.value.isNotBlank()) { "wallet_party_identifier_type_blank" }
        require(value.isNotBlank()) { "wallet_party_identifier_value_blank" }
    }
}

/** Verified protocol metadata projected into the provider-neutral Party directory. */
@Serializable
data class WalletCounterpartyEvidence(
    val role: WalletOrganizationIdentityRole,
    val displayName: String,
    /** Canonical Organization website, when protocol metadata explicitly supplies one. */
    val organizationUri: String? = null,
    val jurisdiction: String? = null,
    val logoUri: String? = null,
    val localizedBranding: List<WalletOrganizationLocalizedBranding> = emptyList(),
    val identifiers: List<WalletPartyIdentifierEvidence>,
) {
    init {
        require(displayName.isNotBlank()) { "wallet_counterparty_display_name_blank" }
        require(organizationUri == null || organizationUri.startsWith("https://")) {
            "wallet_counterparty_organization_uri_not_https"
        }
        require(identifiers.isNotEmpty()) { "wallet_counterparty_identifiers_empty" }
        require(identifiers.count { it.primary } == 1) { "wallet_counterparty_primary_identifier_invalid" }
        require(identifiers.distinctBy { it.type to it.value }.size == identifiers.size) {
            "wallet_counterparty_identifiers_duplicate"
        }
    }
}

/** Protocol-derived localized Organization branding, independent of the local or managed Party authority. */
@Serializable
data class WalletOrganizationLocalizedBranding(
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
        require(name.isNotBlank()) { "wallet_party_branding_name_blank" }
        require(locale == null || locale.isNotBlank()) { "wallet_party_branding_locale_blank" }
    }
}

@Serializable
data class WalletKnownOrganizationIdentity(
    val identity: Identity,
    val binding: IdentityPartyBinding,
    val identifiers: List<IdentityIdentifier>,
    val firstEncounterAtEpochSeconds: Long? = null,
    val lastEncounterAtEpochSeconds: Long? = null,
    val encounterCount: Long = 0,
) {
    init {
        require(binding.identityId == identity.identityId) { "wallet_party_identity_binding_identity_mismatch" }
        require(identifiers.all { it.identityId == identity.identityId }) { "wallet_party_identifier_identity_mismatch" }
        require(encounterCount >= 0) { "wallet_party_identity_encounter_count_invalid" }
        require((encounterCount == 0L) == (firstEncounterAtEpochSeconds == null && lastEncounterAtEpochSeconds == null)) {
            "wallet_party_identity_encounter_presence_invalid"
        }
        if (encounterCount > 0) {
            require(requireNotNull(lastEncounterAtEpochSeconds) >= requireNotNull(firstEncounterAtEpochSeconds)) {
                "wallet_party_identity_encounter_order_invalid"
            }
        }
    }
}

@Serializable
data class WalletPartyInteractionRecord(
    val interactionId: String,
    val role: WalletOrganizationIdentityRole,
    val occurredAtEpochSeconds: Long,
    val outcome: WalletPartyInteractionOutcome,
) {
    init {
        require(interactionId.isNotBlank()) { "wallet_party_interaction_id_blank" }
    }
}

@Serializable
enum class WalletPartyInteractionOutcome { STARTED, SUCCEEDED, DECLINED, CANCELLED, FAILED }

@Serializable
data class WalletKnownOrganization(
    val party: Party,
    val identities: List<WalletKnownOrganizationIdentity>,
    val localizedBranding: List<WalletOrganizationLocalizedBranding> = emptyList(),
    /** Prevents later protocol metadata from overwriting a name chosen by the wallet user. */
    val displayNameManagedByUser: Boolean = false,
    val interactions: List<WalletPartyInteractionRecord> = emptyList(),
) {
    init {
        require(party.partyType == PartyType.ORGANIZATION) { "wallet_party_not_organization" }
        require(party.organizationUnitId != null) { "wallet_party_organization_business_unit_missing" }
        require(identities.isNotEmpty()) { "wallet_party_identities_empty" }
        require(identities.all { it.binding.partyId == party.partyId }) { "wallet_party_binding_organization_mismatch" }
    }
}

/** Result is based on directory state immediately before the current encounter is recorded. */
@Serializable
data class WalletPartyEncounter(
    val organization: WalletKnownOrganization,
    val identityId: Uuid,
    val primaryIdentifierId: Uuid,
    /** True only when this encounter created a new Organization Party. */
    val organizationCreated: Boolean,
    val firstInteraction: Boolean,
    val previousInteractionCount: Long,
    val lastInteractionAtEpochSeconds: Long? = null,
    /** Similar URL-host contacts. These are suggestions only and are never merged automatically. */
    val associationCandidates: List<WalletOrganizationAssociationCandidate> = emptyList(),
) {
    init {
        require(previousInteractionCount >= 0) { "wallet_party_previous_interaction_count_invalid" }
        require(firstInteraction == (previousInteractionCount == 0L)) { "wallet_party_first_interaction_mismatch" }
        require((lastInteractionAtEpochSeconds == null) == firstInteraction) { "wallet_party_last_interaction_mismatch" }
    }
}

@Serializable
data class WalletOrganizationAssociationCandidate(
    val partyId: Uuid,
    val displayName: String,
    val relatedHosts: List<String>,
) {
    init {
        require(displayName.isNotBlank()) { "wallet_party_association_candidate_name_blank" }
        require(relatedHosts.isNotEmpty()) { "wallet_party_association_candidate_hosts_empty" }
    }
}

/**
 * Authoritative Party directory port shared by local and managed wallet products.
 *
 * Local implementations are Wallet-Unit-scoped SQLite stores. Managed implementations are
 * VDX-backed. Profiles and agents are access contexts only: callers use this port and cannot
 * observe which provider owns the authoritative Party data or reach either provider directly.
 */
interface WalletPartyDirectory {
    val authority: WalletPartyDirectoryAuthority

    /** Creates a local Business Unit or validates/resolves a managed assignment. */
    suspend fun provisionBusinessUnit(
        request: WalletBusinessUnitProvisioningRequest,
    ): IdkResult<WalletBusinessUnit, IdkError>

    fun observeBusinessUnit(scope: WalletPartyScope): StateFlow<WalletBusinessUnit?>

    fun observeOrganizations(scope: WalletPartyScope): StateFlow<List<WalletKnownOrganization>>

    suspend fun resolveOrCreateOrganization(
        scope: WalletPartyScope,
        evidence: WalletCounterpartyEvidence,
        encounteredAtEpochSeconds: Long,
    ): IdkResult<WalletPartyEncounter, IdkError>

    suspend fun getOrganization(
        scope: WalletPartyScope,
        partyId: Uuid,
    ): IdkResult<WalletKnownOrganization?, IdkError>

    suspend fun recordInteraction(
        scope: WalletPartyScope,
        partyId: Uuid,
        record: WalletPartyInteractionRecord,
    ): IdkResult<WalletKnownOrganization, IdkError>

    /** Explicit user-approved merge of a newly encountered contact into an existing Organization. */
    suspend fun associateOrganization(
        scope: WalletPartyScope,
        sourcePartyId: Uuid,
        targetPartyId: Uuid,
    ): IdkResult<WalletKnownOrganization, IdkError>

    suspend fun renameOrganization(
        scope: WalletPartyScope,
        partyId: Uuid,
        displayName: String,
    ): IdkResult<WalletKnownOrganization, IdkError>
}

fun WalletOrganizationIdentityRole.toIdentityRole(): IdentityRole =
    when (this) {
        WalletOrganizationIdentityRole.ISSUER -> IdentityRole.ISSUER
        WalletOrganizationIdentityRole.VERIFIER -> IdentityRole.VERIFIER
        WalletOrganizationIdentityRole.MDOC_READER -> IdentityRole("mdoc_reader")
    }
