/* Copyright 2026 Sphereon International B.V. */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.wallet.party.local

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.Identity
import com.sphereon.data.store.party.model.IdentityIdentifier
import com.sphereon.data.store.party.model.IdentityPartyBinding
import com.sphereon.data.store.party.model.IdentityPrivacyMode
import com.sphereon.data.store.party.model.Party
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.wallet.party.WalletCounterpartyEvidence
import com.sphereon.wallet.party.WalletBusinessUnit
import com.sphereon.wallet.party.WalletBusinessUnitProvisioningRequest
import com.sphereon.wallet.party.WalletKnownOrganization
import com.sphereon.wallet.party.WalletKnownOrganizationIdentity
import com.sphereon.wallet.party.WalletOrganizationAssociationCandidate
import com.sphereon.wallet.party.WalletOrganizationLocalizedBranding
import com.sphereon.wallet.party.WalletPartyDirectory
import com.sphereon.wallet.party.WalletPartyDirectoryAuthority
import com.sphereon.wallet.party.WalletPartyEncounter
import com.sphereon.wallet.party.WalletPartyInteractionRecord
import com.sphereon.wallet.party.WalletPartyScope
import com.sphereon.wallet.party.canonicalWalletDidSubject
import com.sphereon.wallet.party.toIdentityRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Local authoritative Party directory. Persistence is delegated to a local-only document store;
 * no VDX/backend client is present in this module or its dependency graph.
 */
class LocalWalletPartyDirectory(
    private val documents: WalletPartyDocumentStore,
    private val scope: CoroutineScope,
    private val json: Json = walletPartyJson,
    private val registrableDomains: WalletRegistrableDomainResolver = PublicSuffixRegistrableDomainResolver,
) : WalletPartyDirectory {
    override val authority: WalletPartyDirectoryAuthority = WalletPartyDirectoryAuthority.LOCAL

    private val mutex = Mutex()
    private val states = mutableMapOf<String, MutableStateFlow<List<WalletKnownOrganization>>>()
    private val businessUnitStates = mutableMapOf<String, MutableStateFlow<WalletBusinessUnit?>>()
    private val loaded = mutableSetOf<String>()

    override suspend fun provisionBusinessUnit(
        request: WalletBusinessUnitProvisioningRequest,
    ): IdkResult<WalletBusinessUnit, IdkError> =
        runDirectoryOperation("wallet_business_unit_provision_failed") {
            mutex.withLock {
                val key = authorityStorageKey(request.tenantId, request.walletUnitId)
                val stored = loadSnapshotLocked(request.tenantId, request.walletUnitId)
                stored?.businessUnit?.let { existing ->
                    request.assignedOrganizationUnitRef?.let { assigned ->
                        require(assigned.partyId == existing.party.partyId.toString()) {
                            "wallet_business_unit_assignment_mismatch"
                        }
                    }
                    return@withLock existing
                }
                val now = Clock.System.now()
                val partyId = request.assignedOrganizationUnitRef?.partyId?.let(Uuid::parse) ?: Uuid.random()
                val businessUnit =
                    WalletBusinessUnit(
                        Party(
                            partyId = partyId,
                            tenantId = request.tenantId,
                            partyType = PartyType.ORGANIZATION_UNIT,
                            origin = PartyOrigin.MANAGED,
                            displayName = request.displayName,
                            organizationUnitId = null,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                persistSnapshotLocked(key, WalletPartySnapshot(businessUnit = businessUnit))
                businessUnit
            }
        }

    override fun observeBusinessUnit(scope: WalletPartyScope): StateFlow<WalletBusinessUnit?> {
        val state = businessUnitState(scope)
        this.scope.launch { ensureLoaded(scope) }
        return state
    }

    override fun observeOrganizations(scope: WalletPartyScope): StateFlow<List<WalletKnownOrganization>> {
        val state = state(scope)
        this.scope.launch { ensureLoaded(scope) }
        return state
    }

    override suspend fun resolveOrCreateOrganization(
        scope: WalletPartyScope,
        evidence: WalletCounterpartyEvidence,
        encounteredAtEpochSeconds: Long,
    ): IdkResult<WalletPartyEncounter, IdkError> =
        runDirectoryOperation("wallet_party_resolve_failed") {
            mutex.withLock {
                val current = loadLocked(scope).toMutableList()
                val match = findIdentifierMatch(current, evidence)
                val associationCandidates = if (match == null) findAssociationCandidates(current, evidence) else emptyList()
                val previousIdentity = match?.identityIndex?.let { match.organization.identities[it] }
                val previousCount = previousIdentity?.encounterCount ?: 0L
                val previousAt = previousIdentity?.lastEncounterAtEpochSeconds
                val updated =
                    if (match == null) {
                        createOrganization(scope, evidence, encounteredAtEpochSeconds)
                    } else {
                        updateOrganization(scope, match, evidence, encounteredAtEpochSeconds)
                    }
                if (match == null) current += updated else current[match.organizationIndex] = updated
                persistLocked(scope, current)
                val identity =
                    if (match?.identityIndex == null) updated.identities.last() else updated.identities[match.identityIndex]
                val primaryValue = evidence.identifiers.single { it.primary }
                val primary = identity.identifiers.single { it.identifierType == primaryValue.type && it.lookupValue == primaryValue.value }
                WalletPartyEncounter(
                    organization = updated,
                    identityId = identity.identity.identityId,
                    primaryIdentifierId = primary.identityIdentifierId,
                    organizationCreated = match == null,
                    firstInteraction = previousCount == 0L,
                    previousInteractionCount = previousCount,
                    lastInteractionAtEpochSeconds = previousAt,
                    associationCandidates = associationCandidates,
                )
            }
        }

    override suspend fun getOrganization(
        scope: WalletPartyScope,
        partyId: Uuid,
    ): IdkResult<WalletKnownOrganization?, IdkError> =
        runDirectoryOperation("wallet_party_get_failed") {
            mutex.withLock { loadLocked(scope).firstOrNull { it.party.partyId == partyId } }
        }

    override suspend fun recordInteraction(
        scope: WalletPartyScope,
        partyId: Uuid,
        record: WalletPartyInteractionRecord,
    ): IdkResult<WalletKnownOrganization, IdkError> =
        runDirectoryOperation("wallet_party_record_interaction_failed") {
            mutex.withLock {
                val current = loadLocked(scope).toMutableList()
                val index = current.indexOfFirst { it.party.partyId == partyId }
                require(index >= 0) { "wallet_party_organization_not_found" }
                val organization = current[index]
                val existing = organization.interactions.indexOfFirst { it.interactionId == record.interactionId }
                val interactions = organization.interactions.toMutableList()
                if (existing >= 0) interactions[existing] = record else interactions += record
                val updated = organization.copy(interactions = interactions.sortedBy { it.occurredAtEpochSeconds })
                current[index] = updated
                persistLocked(scope, current)
                updated
            }
        }

    override suspend fun associateOrganization(
        scope: WalletPartyScope,
        sourcePartyId: Uuid,
        targetPartyId: Uuid,
    ): IdkResult<WalletKnownOrganization, IdkError> =
        runDirectoryOperation("wallet_party_associate_failed") {
            mutex.withLock {
                require(sourcePartyId != targetPartyId) { "wallet_party_association_same_party" }
                val current = loadLocked(scope).toMutableList()
                val sourceIndex = current.indexOfFirst { it.party.partyId == sourcePartyId }
                val targetIndex = current.indexOfFirst { it.party.partyId == targetPartyId }
                require(sourceIndex >= 0 && targetIndex >= 0) { "wallet_party_association_party_not_found" }
                val source = current[sourceIndex]
                val target = current[targetIndex]
                val expectedBusinessUnitId = Uuid.parse(scope.organizationUnitRef.partyId)
                require(source.party.organizationUnitId == expectedBusinessUnitId && target.party.organizationUnitId == expectedBusinessUnitId) {
                    "wallet_party_association_business_unit_mismatch"
                }
                val rebound =
                    source.identities.map { identity ->
                        identity.copy(binding = identity.binding.copy(partyId = target.party.partyId))
                    }
                val merged =
                    target.copy(
                        party = target.party.copy(updatedAt = Clock.System.now()),
                        identities = target.identities + rebound,
                        localizedBranding = mergeLocalizedBranding(target.localizedBranding, source.localizedBranding),
                        interactions =
                            (target.interactions + source.interactions)
                                .distinctBy { it.interactionId }
                                .sortedBy { it.occurredAtEpochSeconds },
                    )
                current[targetIndex] = merged
                current.removeAt(sourceIndex)
                persistLocked(scope, current)
                merged
            }
        }

    override suspend fun renameOrganization(
        scope: WalletPartyScope,
        partyId: Uuid,
        displayName: String,
    ): IdkResult<WalletKnownOrganization, IdkError> =
        runDirectoryOperation("wallet_party_rename_failed") {
            mutex.withLock {
                val name = displayName.trim()
                require(name.isNotBlank()) { "wallet_party_display_name_blank" }
                val current = loadLocked(scope).toMutableList()
                val index = current.indexOfFirst { it.party.partyId == partyId }
                require(index >= 0) { "wallet_party_organization_not_found" }
                val updated =
                    current[index].copy(
                        party = current[index].party.copy(displayName = name, updatedAt = Clock.System.now()),
                        displayNameManagedByUser = true,
                    )
                current[index] = updated
                persistLocked(scope, current)
                updated
            }
        }

    private fun state(scope: WalletPartyScope): MutableStateFlow<List<WalletKnownOrganization>> =
        states.getOrPut(authorityStorageKey(scope.tenantId, scope.walletUnitId)) { MutableStateFlow(emptyList()) }

    private fun businessUnitState(scope: WalletPartyScope): MutableStateFlow<WalletBusinessUnit?> =
        businessUnitStates.getOrPut(authorityStorageKey(scope.tenantId, scope.walletUnitId)) { MutableStateFlow(null) }

    private suspend fun ensureLoaded(scope: WalletPartyScope) {
        runCatching { mutex.withLock { loadLocked(scope) } }
    }

    private suspend fun loadLocked(scope: WalletPartyScope): List<WalletKnownOrganization> {
        val stored = loadSnapshotLocked(scope.tenantId, scope.walletUnitId)
        val businessUnit = requireNotNull(stored?.businessUnit) { "wallet_business_unit_not_provisioned" }
        require(businessUnit.ref.partyId == scope.organizationUnitRef.partyId) { "wallet_business_unit_scope_mismatch" }
        return state(scope).value
    }

    private suspend fun persistLocked(scope: WalletPartyScope, organizations: List<WalletKnownOrganization>) {
        val businessUnit = requireNotNull(businessUnitState(scope).value) { "wallet_business_unit_not_provisioned" }
        persistSnapshotLocked(
            authorityStorageKey(scope.tenantId, scope.walletUnitId),
            WalletPartySnapshot(businessUnit = businessUnit, organizations = organizations),
        )
    }

    private suspend fun loadSnapshotLocked(tenantId: String, walletUnitId: String): WalletPartySnapshot? {
        val key = authorityStorageKey(tenantId, walletUnitId)
        if (key !in loaded) {
            val stored = documents.read(key)?.let { json.decodeFromString<WalletPartySnapshot>(it) }
            stored?.let {
                businessUnitStates.getOrPut(key) { MutableStateFlow(null) }.value = it.businessUnit
                states.getOrPut(key) { MutableStateFlow(emptyList()) }.value = sortOrganizations(it.organizations)
            }
            loaded += key
            return stored
        }
        val businessUnit = businessUnitStates[key]?.value ?: return null
        return WalletPartySnapshot(businessUnit, states[key]?.value.orEmpty())
    }

    private suspend fun persistSnapshotLocked(key: String, snapshot: WalletPartySnapshot) {
        documents.write(key, json.encodeToString(snapshot))
        businessUnitStates.getOrPut(key) { MutableStateFlow(null) }.value = snapshot.businessUnit
        states.getOrPut(key) { MutableStateFlow(emptyList()) }.value = sortOrganizations(snapshot.organizations)
        loaded += key
    }

    private fun sortOrganizations(organizations: List<WalletKnownOrganization>): List<WalletKnownOrganization> =
        organizations.sortedWith(
            compareByDescending<WalletKnownOrganization> { organization ->
                organization.identities.mapNotNull { it.lastEncounterAtEpochSeconds }.maxOrNull() ?: Long.MIN_VALUE
            }.thenBy { it.party.displayName },
        )

    private fun authorityStorageKey(tenantId: String, walletUnitId: String): String = "$tenantId|$walletUnitId"

    private fun findIdentifierMatch(
        organizations: List<WalletKnownOrganization>,
        evidence: WalletCounterpartyEvidence,
    ): IdentifierMatch? {
        val primary = evidence.identifiers.single { it.primary }
        val requestedRole = evidence.role.toIdentityRole()
        organizations.forEachIndexed { organizationIndex, organization ->
            organization.identities.forEachIndexed { identityIndex, identity ->
                if (
                    identity.identity.identityRole == requestedRole &&
                    identity.identifiers.any { it.identifierType == primary.type && it.lookupValue == primary.value }
                ) {
                    return IdentifierMatch(organizationIndex, identityIndex, organization)
                }
            }
        }
        val didSubjects =
            evidence.identifiers
                .filter { it.type == com.sphereon.data.store.party.model.IdentifierType.DID }
                .mapNotNullTo(linkedSetOf()) { canonicalWalletDidSubject(it.value) }
        if (didSubjects.isNotEmpty()) {
            organizations.forEachIndexed { organizationIndex, organization ->
                val matchingIdentityIndexes =
                    organization.identities.mapIndexedNotNull { identityIndex, identity ->
                        identityIndex.takeIf {
                            identity.identifiers.any {
                                it.identifierType == com.sphereon.data.store.party.model.IdentifierType.DID &&
                                    canonicalWalletDidSubject(it.lookupValue) in didSubjects
                            }
                        }
                    }
                if (matchingIdentityIndexes.isNotEmpty()) {
                    val sameRoleIdentity = matchingIdentityIndexes.firstOrNull { organization.identities[it].identity.identityRole == requestedRole }
                    return IdentifierMatch(organizationIndex, sameRoleIdentity, organization)
                }
            }
        }
        val verifiedOrganizationKeys =
            evidence.identifiers
                .filter { it.verified && it.type in ORGANIZATION_IDENTIFIER_TYPES }
                .mapTo(linkedSetOf()) { it.type to it.value }
        if (verifiedOrganizationKeys.isNotEmpty()) {
            organizations.forEachIndexed { organizationIndex, organization ->
                if (organization.identities.any { identity ->
                        identity.identifiers.any {
                            it.isVerified && (it.identifierType to it.lookupValue) in verifiedOrganizationKeys
                        }
                    }
                ) {
                    return IdentifierMatch(organizationIndex, null, organization)
                }
            }
        }
        return null
    }

    private fun findAssociationCandidates(
        organizations: List<WalletKnownOrganization>,
        evidence: WalletCounterpartyEvidence,
    ): List<WalletOrganizationAssociationCandidate> {
        val encounteredHosts =
            evidence.identifiers
                .filter { it.type == com.sphereon.data.store.party.model.IdentifierType.URL }
                .mapNotNull { urlHost(it.value) }
                .toSet()
        if (encounteredHosts.isEmpty()) return emptyList()
        return organizations.mapNotNull { organization ->
            val existingHosts =
                organization.identities
                    .flatMap { it.identifiers }
                    .filter { it.identifierType == com.sphereon.data.store.party.model.IdentifierType.URL }
                    .mapNotNull { urlHost(it.lookupValue) }
                    .toSet()
            val related = encounteredHosts.filter { candidate -> existingHosts.any { hostsAreRelated(candidate, it) } }.sorted()
            related.takeIf { it.isNotEmpty() }?.let {
                WalletOrganizationAssociationCandidate(
                    partyId = organization.party.partyId,
                    displayName = organization.party.displayName,
                    relatedHosts = it,
                )
            }
        }
    }

    private fun urlHost(value: String): String? {
        val schemeIndex = value.indexOf("://")
        if (schemeIndex <= 0) return null
        val authority = value.substring(schemeIndex + 3).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val host =
            if (authority.startsWith('[')) {
                authority.substringAfter('[').substringBefore(']')
            } else {
                authority.substringBefore(':')
            }
        return host.lowercase().trimEnd('.').takeIf { it.isNotBlank() }
    }

    private fun hostsAreRelated(left: String, right: String): Boolean {
        if (left == right) return true
        val leftRegistrable = registrableDomains.registrableDomain(left) ?: return false
        val rightRegistrable = registrableDomains.registrableDomain(right) ?: return false
        return leftRegistrable == rightRegistrable
    }

    private fun createOrganization(
        scope: WalletPartyScope,
        evidence: WalletCounterpartyEvidence,
        encounteredAt: Long,
    ): WalletKnownOrganization {
        val instant = Instant.fromEpochSeconds(encounteredAt)
        val partyId = Uuid.random()
        val identity = createIdentity(scope, partyId, evidence, instant)
        return WalletKnownOrganization(
            party =
                Party(
                    partyId = partyId,
                    tenantId = scope.tenantId,
                    partyType = PartyType.ORGANIZATION,
                    origin = PartyOrigin.EXTERNAL,
                    displayName = evidence.displayName,
                    uri = evidence.organizationUri,
                    jurisdiction = evidence.jurisdiction,
                    organizationUnitId = Uuid.parse(scope.organizationUnitRef.partyId),
                    createdAt = instant,
                    updatedAt = instant,
                ),
            identities = listOf(identity),
            localizedBranding = evidence.localizedBranding,
            interactions = emptyList(),
        )
    }

    private fun updateOrganization(
        scope: WalletPartyScope,
        match: IdentifierMatch,
        evidence: WalletCounterpartyEvidence,
        encounteredAt: Long,
    ): WalletKnownOrganization {
        val instant = Instant.fromEpochSeconds(encounteredAt)
        val organization = match.organization
        val identities = organization.identities.toMutableList()
        if (match.identityIndex == null) {
            identities += createIdentity(scope, organization.party.partyId, evidence, instant)
        } else run {
            val roleIndex = match.identityIndex
            val current = identities[roleIndex]
            val existingKeys = current.identifiers.mapTo(linkedSetOf()) { it.identifierType to it.lookupValue }
            val additions =
                evidence.identifiers.filter { (it.type to it.value) !in existingKeys }.map { identifier ->
                    IdentityIdentifier(
                        identityIdentifierId = Uuid.random(),
                        identityId = current.identity.identityId,
                        tenantId = scope.tenantId,
                        identifierType = identifier.type,
                        lookupValue = identifier.value,
                        isPrimary = identifier.primary && current.identifiers.none { it.isPrimary },
                        isVerified = identifier.verified,
                        verifiedAt = instant.takeIf { identifier.verified },
                        validFrom = instant,
                        createdAt = instant,
                        updatedAt = instant,
                    )
                }
            identities[roleIndex] =
                current.copy(
                    identifiers = current.identifiers + additions,
                    lastEncounterAtEpochSeconds = encounteredAt,
                    encounterCount = current.encounterCount + 1,
                )
        }
        return organization.copy(
            party =
                organization.party.copy(
                    displayName = if (organization.displayNameManagedByUser) organization.party.displayName else evidence.displayName,
                    uri = organization.party.uri ?: evidence.organizationUri,
                    updatedAt = instant,
                ),
            identities = identities,
            localizedBranding = mergeLocalizedBranding(organization.localizedBranding, evidence.localizedBranding),
        )
    }

    private fun createIdentity(
        scope: WalletPartyScope,
        organizationId: Uuid,
        evidence: WalletCounterpartyEvidence,
        instant: Instant,
    ): WalletKnownOrganizationIdentity {
        val identityId = Uuid.random()
        val identity =
            Identity(
                partyId = identityId,
                tenantId = scope.tenantId,
                identityRole = evidence.role.toIdentityRole(),
                isDefault = true,
                privacyMode = IdentityPrivacyMode.PARTY_PROFILED,
                createdAt = instant,
                updatedAt = instant,
            )
        return WalletKnownOrganizationIdentity(
            identity = identity,
            binding = IdentityPartyBinding(identityId, organizationId, PartyType.ORGANIZATION, "organization_identity", instant),
            identifiers =
                evidence.identifiers.map { identifier ->
                    IdentityIdentifier(
                        identityIdentifierId = Uuid.random(),
                        identityId = identityId,
                        tenantId = scope.tenantId,
                        identifierType = identifier.type,
                        lookupValue = identifier.value,
                        isPrimary = identifier.primary,
                        isVerified = identifier.verified,
                        verifiedAt = instant.takeIf { identifier.verified },
                        validFrom = instant,
                        createdAt = instant,
                        updatedAt = instant,
                    )
                },
            firstEncounterAtEpochSeconds = instant.epochSeconds,
            lastEncounterAtEpochSeconds = instant.epochSeconds,
            encounterCount = 1,
        )
    }

    private suspend fun <T> runDirectoryOperation(
        code: String,
        operation: suspend () -> T,
    ): IdkResult<T, IdkError> =
        try {
            Ok(operation())
        } catch (expected: Exception) {
            Err(IdkError.fromString(code = code, message = expected.message ?: code, exception = expected))
        }

    private data class IdentifierMatch(
        val organizationIndex: Int,
        val identityIndex: Int?,
        val organization: WalletKnownOrganization,
    )

    private companion object {
        val ORGANIZATION_IDENTIFIER_TYPES =
            setOf(
                com.sphereon.data.store.party.model.IdentifierType.VAT,
                com.sphereon.data.store.party.model.IdentifierType.NTR,
                com.sphereon.data.store.party.model.IdentifierType.PSD,
                com.sphereon.data.store.party.model.IdentifierType.LEI,
                com.sphereon.data.store.party.model.IdentifierType.LEGAL_LOCAL,
                com.sphereon.data.store.party.model.IdentifierType.EORI,
                com.sphereon.data.store.party.model.IdentifierType.EUID,
                com.sphereon.data.store.party.model.IdentifierType.VATIN,
                com.sphereon.data.store.party.model.IdentifierType.LEGAL_TIN,
                com.sphereon.data.store.party.model.IdentifierType.ISO6523_ORG_ID,
                com.sphereon.data.store.party.model.IdentifierType.VLEI,
            )
    }
}

/**
 * Branding is Organization evidence, not Identity evidence. Adding an issuer/verifier Identity
 * must therefore enrich the Organization branding instead of replacing it with a later protocol
 * encounter that happens to omit a logo or localized field.
 */
private fun mergeLocalizedBranding(
    existing: List<WalletOrganizationLocalizedBranding>,
    encountered: List<WalletOrganizationLocalizedBranding>,
): List<WalletOrganizationLocalizedBranding> {
    if (encountered.isEmpty()) return existing
    val merged = existing.toMutableList()
    encountered.forEach { incoming ->
        val index = merged.indexOfFirst { it.locale == incoming.locale }
        if (index < 0) {
            merged += incoming
        } else {
            val current = merged[index]
            merged[index] =
                incoming.copy(
                    logoUri = incoming.logoUri ?: current.logoUri,
                    logoAltText = incoming.logoAltText ?: current.logoAltText,
                    description = incoming.description ?: current.description,
                    backgroundImageUri = incoming.backgroundImageUri ?: current.backgroundImageUri,
                    backgroundColor = incoming.backgroundColor ?: current.backgroundColor,
                    textColor = incoming.textColor ?: current.textColor,
                )
        }
    }
    return merged
}

@Serializable
private data class WalletPartySnapshot(
    val businessUnit: WalletBusinessUnit,
    val organizations: List<WalletKnownOrganization> = emptyList(),
)
