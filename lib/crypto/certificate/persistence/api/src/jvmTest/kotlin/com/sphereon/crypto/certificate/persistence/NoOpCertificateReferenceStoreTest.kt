/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.certificate.persistence

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoOpCertificateReferenceStoreTest {
    @Test
    fun noOpStoreIsUnavailableAndDoesNotExposeReferences() = runTest {
        val store: CertificateReferenceStore = NoOpCertificateReferenceStore()

        assertFalse(store.isAvailable)
        assertTrue(store.findAll("tenant-1").value.isEmpty())
        assertTrue(store.findByProviderCertificateId("tenant-1", "provider-1", "certificate-1").value.isEmpty())
        assertTrue(store.findById("tenant-1", "missing").value == null)
        assertTrue(store.deleteById("tenant-1", "missing").value == false)
    }

    @Test
    fun noOpStoreRejectsHistoricalOwnershipLookup() = runTest {
        val store: CertificateReferenceStore = NoOpCertificateReferenceStore()

        val result = store.findLatestByAliasIncludingDeleted(
            tenantId = "tenant-1",
            alias = "certificate-alias",
            providerId = "provider-1",
            kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
        )

        assertTrue(result.isErr)
        assertEquals(
            CertificateReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
            result.error.code,
        )
    }
}
