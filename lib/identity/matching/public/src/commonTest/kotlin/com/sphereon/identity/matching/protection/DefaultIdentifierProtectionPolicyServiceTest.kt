/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.identity.matching.protection

import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultIdentifierProtectionPolicyServiceTest {
    private val service = DefaultIdentifierProtectionPolicyService()

    @Test
    fun didIsPublicProtocolMaterial() = runTest {
        val policy = service.policyFor("tenant-a", IdentifierType.DID)

        assertEquals(IdentifierProtectionMode.PLAINTEXT, policy.mode)
        assertEquals(NormalizationProfile.DID, policy.normalization)
    }

    @Test
    fun emailRemainsSearchableEncryptedPii() = runTest {
        val policy = service.policyFor("tenant-a", IdentifierType.EMAIL)

        assertEquals(IdentifierProtectionMode.SEARCHABLE_ENCRYPTED, policy.mode)
        assertEquals(NormalizationProfile.EMAIL, policy.normalization)
    }
}
