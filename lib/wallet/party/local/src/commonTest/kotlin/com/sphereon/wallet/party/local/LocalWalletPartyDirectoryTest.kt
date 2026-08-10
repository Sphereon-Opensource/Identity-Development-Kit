/* Copyright 2026 Sphereon International B.V. */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.wallet.party.local

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.wallet.party.WalletCounterpartyEvidence
import com.sphereon.wallet.party.WalletOrganizationIdentityRole
import com.sphereon.wallet.party.WalletOrganizationLocalizedBranding
import com.sphereon.wallet.party.WalletPartyIdentifierEvidence
import com.sphereon.wallet.party.WalletPartyDirectoryAuthority
import com.sphereon.wallet.party.WalletPartyScope
import com.sphereon.wallet.party.WalletBusinessUnitProvisioningRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalCoroutinesApi::class)
class LocalWalletPartyDirectoryTest {
    @Test
    fun `localized issuer branding is durable Party contact evidence`() = runTest {
        val store = MemoryDocumentStore()
        val directory = LocalWalletPartyDirectory(store, backgroundScope)
        val scope = directory.localScope()
        val branding =
            listOf(
                WalletOrganizationLocalizedBranding("en", "Example Issuer", "https://issuer.example/logo-en.png", "Issuer logo", "English"),
                WalletOrganizationLocalizedBranding("nl", "Voorbeeld-uitgever", "https://issuer.example/logo-nl.png", "Uitgeverslogo", "Nederlands"),
            )

        val created = directory.resolveOrCreateOrganization(scope, issuer("https://issuer.example").copy(localizedBranding = branding), 100).getOrThrow()
        val reloadedDirectory = LocalWalletPartyDirectory(store, backgroundScope)
        val reloaded = reloadedDirectory.getOrganization(scope, created.organization.party.partyId).getOrThrow()

        assertEquals(branding, reloaded?.localizedBranding)
    }

    @Test
    fun `exact typed identifier creates once and then returns prior encounter history`() = runTest {
        val store = MemoryDocumentStore()
        val directory = LocalWalletPartyDirectory(store, backgroundScope)
        val scope = directory.localScope()
        val evidence = issuer("https://issuer.example")

        val first = directory.resolveOrCreateOrganization(scope, evidence, 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, evidence, 200).getOrThrow()

        assertTrue(first.firstInteraction)
        assertTrue(first.organizationCreated)
        assertFalse(second.firstInteraction)
        assertFalse(second.organizationCreated)
        assertEquals(1, second.previousInteractionCount)
        assertEquals(100, second.lastInteractionAtEpochSeconds)
        assertEquals(first.organization.party.partyId, second.organization.party.partyId)
        assertEquals(PartyType.ORGANIZATION, second.organization.party.partyType)
        assertEquals(null, second.organization.party.uri, "A protocol endpoint is not an Organization website")
        assertEquals(scope.organizationUnitRef.partyId, second.organization.party.organizationUnitId.toString())
        assertEquals(IdentityRole.ISSUER, second.organization.identities.single().identity.identityRole)
        assertEquals(
            setOf(IdentifierType.OID4VCI_ISSUER, IdentifierType.URL),
            second.organization.identities.single().identifiers.map { it.identifierType }.toSet(),
        )
    }

    @Test
    fun `display name never merges different protocol identifiers`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val first = directory.resolveOrCreateOrganization(scope, issuer("https://one.example"), 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, issuer("https://two.example"), 200).getOrThrow()

        assertNotEquals(first.organization.party.partyId, second.organization.party.partyId)
    }

    @Test
    fun `local directory reports local authority`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)

        assertEquals(WalletPartyDirectoryAuthority.LOCAL, directory.authority)
    }

    @Test
    fun `holder identifier resolution is durable and idempotent`() = runTest {
        val store = MemoryDocumentStore()
        val directory = LocalWalletPartyDirectory(store, backgroundScope)
        val scope = directory.localScope()

        val created =
            directory.resolveOrCreateIdentifier(scope, IdentifierType.DID, "did:example:holder", IdentityRole.HOLDER).getOrThrow()
        val reloaded = LocalWalletPartyDirectory(store, backgroundScope)
        val resolved =
            reloaded.resolveOrCreateIdentifier(scope, IdentifierType.DID, "did:example:holder", IdentityRole.HOLDER).getOrThrow()

        assertEquals(created, resolved)
    }

    @Test
    fun `sibling subdomains of one registrable domain are association candidates`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer = directory.resolveOrCreateOrganization(scope, issuer("https://issuer.example.com/offer"), 100).getOrThrow()
        val verifier = directory.resolveOrCreateOrganization(scope, verifier("https://verifier.example.com/response"), 200).getOrThrow()

        assertNotEquals(issuer.organization.party.partyId, verifier.organization.party.partyId)
        assertEquals(issuer.organization.party.partyId, verifier.associationCandidates.single().partyId)
        assertEquals(listOf("verifier.example.com"), verifier.associationCandidates.single().relatedHosts)
    }

    @Test
    fun `different paths on one host are association candidates`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer = directory.resolveOrCreateOrganization(scope, issuer("https://example.com/issuer"), 100).getOrThrow()
        val verifier = directory.resolveOrCreateOrganization(scope, verifier("https://example.com/verifier"), 200).getOrThrow()

        assertEquals(issuer.organization.party.partyId, verifier.associationCandidates.single().partyId)
    }

    @Test
    fun `unrelated co uk registrants are not association candidates`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        directory.resolveOrCreateOrganization(scope, issuer("https://issuer.alpha.co.uk"), 100).getOrThrow()
        val verifier = directory.resolveOrCreateOrganization(scope, verifier("https://verifier.beta.co.uk"), 200).getOrThrow()

        assertTrue(verifier.associationCandidates.isEmpty())
    }

    @Test
    fun `unrelated private suffix registrants are not association candidates`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        directory.resolveOrCreateOrganization(scope, issuer("https://issuer.alpha.github.io"), 100).getOrThrow()
        val verifier = directory.resolveOrCreateOrganization(scope, verifier("https://verifier.beta.github.io"), 200).getOrThrow()

        assertTrue(verifier.associationCandidates.isEmpty())
    }

    @Test
    fun `a role identity can be added to an existing Organization through shared legal identifier`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer = issuer("https://issuer.example", sharedLei = "LEI-123")
        val verifier =
            WalletCounterpartyEvidence(
                role = WalletOrganizationIdentityRole.VERIFIER,
                displayName = "Example Organization",
                identifiers =
                    listOf(
                        WalletPartyIdentifierEvidence(IdentifierType.VERIFIER, "redirect_uri|https://rp.example", primary = true),
                        WalletPartyIdentifierEvidence(IdentifierType.LEI, "LEI-123", verified = true),
                    ),
            )
        val first = directory.resolveOrCreateOrganization(scope, issuer, 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, verifier, 200).getOrThrow()

        assertEquals(first.organization.party.partyId, second.organization.party.partyId)
        assertEquals(setOf(IdentityRole.ISSUER, IdentityRole.VERIFIER), second.organization.identities.map { it.identity.identityRole }.toSet())
        assertTrue(second.firstInteraction, "A new verifier Identity is a first interaction even after issuer contact")
        assertFalse(second.organizationCreated)
    }

    @Test
    fun `verifier identity retains scheme bound id DID and verifier URL`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val encounter =
            directory.resolveOrCreateOrganization(
                scope,
                WalletCounterpartyEvidence(
                    role = WalletOrganizationIdentityRole.VERIFIER,
                    displayName = "Example RP",
                    identifiers =
                        listOf(
                            WalletPartyIdentifierEvidence(IdentifierType.VERIFIER, "did|did:example:rp", primary = true),
                            WalletPartyIdentifierEvidence(IdentifierType.DID, "did:example:rp"),
                            WalletPartyIdentifierEvidence(IdentifierType.URL, "https://rp.example/response"),
                        ),
                ),
                100,
            ).getOrThrow()

        assertEquals(
            setOf(IdentifierType.VERIFIER, IdentifierType.DID, IdentifierType.URL),
            encounter.organization.identities.single().identifiers.map { it.identifierType }.toSet(),
        )
    }

    @Test
    fun `different key fragments of one DID associate automatically`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer =
            WalletCounterpartyEvidence(
                role = WalletOrganizationIdentityRole.ISSUER,
                displayName = "Example Organization",
                identifiers =
                    listOf(
                        WalletPartyIdentifierEvidence(IdentifierType.OID4VCI_ISSUER, "did:example:org#issuer-key", primary = true),
                        WalletPartyIdentifierEvidence(IdentifierType.DID, "did:example:org#issuer-key"),
                    ),
            )
        val verifier =
            WalletCounterpartyEvidence(
                role = WalletOrganizationIdentityRole.VERIFIER,
                displayName = "Example Organization RP",
                identifiers =
                    listOf(
                        WalletPartyIdentifierEvidence(IdentifierType.VERIFIER, "did|did:example:org#rp-key", primary = true),
                        WalletPartyIdentifierEvidence(IdentifierType.DID, "did:example:org#rp-key"),
                    ),
            )

        val first = directory.resolveOrCreateOrganization(scope, issuer, 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, verifier, 200).getOrThrow()

        assertEquals(first.organization.party.partyId, second.organization.party.partyId)
        assertEquals(2, second.organization.identities.size)
        assertTrue(second.firstInteraction)
        assertFalse(second.organizationCreated)
        assertTrue(second.associationCandidates.isEmpty())
        assertEquals(
            setOf("did:example:org#issuer-key", "did:example:org#rp-key"),
            second.organization.identities.flatMap { it.identifiers }.filter { it.identifierType == IdentifierType.DID }.map { it.lookupValue }.toSet(),
        )
    }

    @Test
    fun `same role DID URLs add full identifiers to the existing Identity`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val firstDid = "did:example:org#issuer-key-1"
        val secondDid = "did:example:org/path?service=issuer#issuer-key-2"

        val first = directory.resolveOrCreateOrganization(scope, didIssuer(firstDid), 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, didIssuer(secondDid), 200).getOrThrow()

        assertEquals(first.organization.party.partyId, second.organization.party.partyId)
        assertEquals(1, second.organization.identities.size)
        assertEquals(first.identityId, second.identityId)
        assertFalse(second.firstInteraction)
        assertFalse(second.organizationCreated)
        assertTrue(second.associationCandidates.isEmpty())
        assertEquals(
            setOf(firstDid, secondDid),
            second.organization.identities.single().identifiers.filter { it.identifierType == IdentifierType.DID }.map { it.lookupValue }.toSet(),
        )
    }

    @Test
    fun `DID URL attaches only to the same role Identity with the matching DID subject`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val first = directory.resolveOrCreateOrganization(scope, didIssuer("did:example:first#key", "LEI-DID"), 100).getOrThrow()
        val second = directory.resolveOrCreateOrganization(scope, didIssuer("did:example:second#key-1", "LEI-DID"), 200).getOrThrow()
        val third = directory.resolveOrCreateOrganization(scope, didIssuer("did:example:second/path#key-2"), 300).getOrThrow()

        assertEquals(first.organization.party.partyId, second.organization.party.partyId)
        assertEquals(second.organization.party.partyId, third.organization.party.partyId)
        assertNotEquals(first.identityId, second.identityId)
        assertEquals(second.identityId, third.identityId)
        assertEquals(2, third.organization.identities.size)
        assertFalse(third.organizationCreated)
        assertTrue(third.associationCandidates.isEmpty())
    }

    @Test
    fun `later role evidence cannot overwrite an Organization URI`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer =
            issuer("https://issuer.example/offer", sharedLei = "LEI-URI").copy(
                organizationUri = "https://example.com",
            )
        val verifier =
            verifier("https://verifier.example/response", sharedLei = "LEI-URI").copy(
                organizationUri = "https://other.example",
            )

        directory.resolveOrCreateOrganization(scope, issuer, 100).getOrThrow()
        val encounter = directory.resolveOrCreateOrganization(scope, verifier, 200).getOrThrow()

        assertEquals("https://example.com", encounter.organization.party.uri)
    }

    @Test
    fun `later verifier evidence cannot erase stored issuer logo`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer =
            issuer("https://issuer.example/offer", sharedLei = "LEI-BRANDING").copy(
                localizedBranding =
                    listOf(
                        WalletOrganizationLocalizedBranding(
                            locale = "en",
                            name = "Example issuer",
                            logoUri = "https://example.com/issuer-logo.png",
                        ),
                    ),
            )
        val verifier =
            verifier("https://verifier.example/response", sharedLei = "LEI-BRANDING").copy(
                localizedBranding =
                    listOf(WalletOrganizationLocalizedBranding(locale = "en", name = "Example verifier")),
            )

        val created = directory.resolveOrCreateOrganization(scope, issuer, 100).getOrThrow()
        val updated = directory.resolveOrCreateOrganization(scope, verifier, 200).getOrThrow()

        assertEquals(created.organization.party.partyId, updated.organization.party.partyId)
        assertEquals("https://example.com/issuer-logo.png", updated.organization.localizedBranding.single().logoUri)
    }

    @Test
    fun `association retains branding from both Organization records`() = runTest {
        val directory = LocalWalletPartyDirectory(MemoryDocumentStore(), backgroundScope)
        val scope = directory.localScope()
        val issuer =
            directory.resolveOrCreateOrganization(
                scope,
                issuer("https://issuer.example/offer").copy(
                    localizedBranding =
                        listOf(
                            WalletOrganizationLocalizedBranding(
                                locale = "en",
                                name = "Example issuer",
                                logoUri = "https://example.com/issuer-logo.png",
                            ),
                        ),
                ),
                100,
            ).getOrThrow()
        val verifier = directory.resolveOrCreateOrganization(scope, verifier("https://verifier.example/response"), 200).getOrThrow()

        val associated =
            directory.associateOrganization(
                scope = scope,
                sourcePartyId = issuer.organization.party.partyId,
                targetPartyId = verifier.organization.party.partyId,
            ).getOrThrow()

        assertEquals("https://example.com/issuer-logo.png", associated.localizedBranding.single().logoUri)
    }

    private fun issuer(value: String, sharedLei: String? = null) =
        WalletCounterpartyEvidence(
            role = WalletOrganizationIdentityRole.ISSUER,
            displayName = "Example Organization",
            identifiers =
                buildList {
                    add(WalletPartyIdentifierEvidence(IdentifierType.OID4VCI_ISSUER, value, primary = true, verified = true))
                    add(WalletPartyIdentifierEvidence(IdentifierType.URL, value))
                    sharedLei?.let { add(WalletPartyIdentifierEvidence(IdentifierType.LEI, it, verified = true)) }
                },
        )

    private fun verifier(value: String, sharedLei: String? = null) =
        WalletCounterpartyEvidence(
            role = WalletOrganizationIdentityRole.VERIFIER,
            displayName = "Example Verifier",
            identifiers =
                buildList {
                    add(WalletPartyIdentifierEvidence(IdentifierType.VERIFIER, "redirect_uri|$value", primary = true, verified = true))
                    add(WalletPartyIdentifierEvidence(IdentifierType.URL, value))
                    sharedLei?.let { add(WalletPartyIdentifierEvidence(IdentifierType.LEI, it, verified = true)) }
                },
        )

    private fun didIssuer(didUrl: String, sharedLei: String? = null) =
        WalletCounterpartyEvidence(
            role = WalletOrganizationIdentityRole.ISSUER,
            displayName = "Example Organization",
            identifiers =
                buildList {
                    add(WalletPartyIdentifierEvidence(IdentifierType.OID4VCI_ISSUER, didUrl, primary = true, verified = true))
                    add(WalletPartyIdentifierEvidence(IdentifierType.DID, didUrl))
                    sharedLei?.let { add(WalletPartyIdentifierEvidence(IdentifierType.LEI, it, verified = true)) }
                },
        )

    private suspend fun LocalWalletPartyDirectory.localScope(): WalletPartyScope {
        val businessUnit =
            provisionBusinessUnit(
                WalletBusinessUnitProvisioningRequest("tenant", "wallet-unit", "Personal wallet"),
            ).getOrThrow()
        return WalletPartyScope("tenant", "wallet-unit", businessUnit.ref)
    }

    private class MemoryDocumentStore : WalletPartyDocumentStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun read(scopeKey: String): String? = values[scopeKey]
        override suspend fun write(scopeKey: String, document: String) {
            values[scopeKey] = document
        }
    }
}
