/* Copyright 2026 Sphereon International B.V. */

package com.sphereon.wallet.party

import com.sphereon.data.store.party.model.IdentityRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WalletDidSubjectPolicyTest {
    @Test
    fun `DID URLs reduce to the canonical base DID without changing the method specific id`() {
        assertEquals("did:example:organization", canonicalWalletDidSubject("did:example:organization/path?version=2#issuer-key"))
        assertEquals("did:web:example.com:tenant", canonicalWalletDidSubject("did:web:example.com:tenant#rp-key"))
    }

    @Test
    fun `non DIDs and malformed DIDs are not grouping keys`() {
        assertNull(canonicalWalletDidSubject("https://example.com"))
        assertNull(canonicalWalletDidSubject("did::subject"))
        assertNull(canonicalWalletDidSubject("did:Example:subject"))
        assertNull(canonicalWalletDidSubject("did:example:"))
    }

    @Test
    fun `organization interaction roles remain distinct Identity roles`() {
        assertEquals(IdentityRole.ISSUER, WalletOrganizationIdentityRole.ISSUER.toIdentityRole())
        assertEquals(IdentityRole.VERIFIER, WalletOrganizationIdentityRole.VERIFIER.toIdentityRole())
        assertNotEquals(
            WalletOrganizationIdentityRole.VERIFIER.toIdentityRole(),
            WalletOrganizationIdentityRole.MDOC_READER.toIdentityRole(),
        )
    }
}
