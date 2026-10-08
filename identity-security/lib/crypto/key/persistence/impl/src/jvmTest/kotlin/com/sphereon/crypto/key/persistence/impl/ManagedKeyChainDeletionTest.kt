/* (c) 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.certificate.persistence.*
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.ManagedKeyStoreModeResolver
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.kms.keystore.managed.ManagedKeyStoreWithProviderLookups
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock

class ManagedKeyChainDeletionTest {
    @Test
    fun managedDeletionRetiresOnlyExactOwnedChainsAfterProviderAndBeforeKey() = runTest {
        val f = Fixture()
        val chain = f.chain
        f.rows = listOf(chain,
            chain.copy(id = "external", controlMode = ResourceControlMode.EXTERNALLY_MANAGED),
            chain.copy(id = "other-tenant", tenantId = "other"),
            chain.copy(id = "other-key", linkedKeyReferenceId = "other"),
            chain.copy(id = "old", deletedAt = Clock.System.now()),
            chain.copy(id = "trusted", kind = CertificateReferenceKind.TRUSTED_CERTIFICATE, linkedKeyReferenceId = null))
        assertTrue(f.delete())
        assertEquals(listOf("provider", "chain:chain", "key"), f.events)
        coVerify(exactly = 1) { f.certificates.findByLinkedKeyReferenceId("tenant", "key-id") }
    }

    @Test
    fun providerRefusalPreservesBothReferences() = runTest {
        val f = Fixture()
        f.providerDeletes = false
        assertFalse(f.delete())
        assertEquals(listOf("provider"), f.events)
    }

    @Test
    fun chainLookupFailurePreventsProviderDeletion() = runTest {
        val f = Fixture()
        coEvery { f.certificates.findByLinkedKeyReferenceId(any(), any()) } returns Err(IdkError.UNKNOWN_ERROR(message = "lookup failed"))
        assertFailsWith<PKIException> { f.delete() }
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun chainPersistenceFailureDoesNotRetireKeyOrReportSuccess() = runTest {
        val f = Fixture()
        coEvery { f.certificates.deleteById(any(), any()) } returns Err(IdkError.UNKNOWN_ERROR(message = "write failed"))
        assertFailsWith<PKIException> { f.delete() }
        assertEquals(listOf("provider"), f.events)
    }

    @Test
    fun unretiredChainDoesNotRetireKeyOrReportSuccess() = runTest {
        val f = Fixture()
        coEvery { f.certificates.deleteById(any(), any()) } returns Ok(false)
        assertFailsWith<PKIException> { f.delete() }
        assertEquals(listOf("provider"), f.events)
    }

    @Test
    fun externalKeyDeletionNeverTouchesProviderOrCertificateReferences() = runTest {
        val f = Fixture(external = true)
        assertTrue(f.delete())
        assertEquals(listOf("key"), f.events)
        coVerify(exactly = 0) { f.certificates.findByLinkedKeyReferenceId(any(), any()) }
        coVerify(exactly = 0) { f.certificates.deleteById(any(), any()) }
    }

    @Test
    fun selfIndexingProviderOwnsCleanupWithoutDuplicateRetirement() = runTest {
        val f = Fixture()
        coEvery { f.providers.maintainsKeyReferenceIndex("provider") } returns true
        assertTrue(f.delete())
        assertEquals(listOf("provider"), f.events)
        coVerify(exactly = 0) { f.certificates.findByLinkedKeyReferenceId(any(), any()) }
    }

    @Test
    fun keyRetirementFailurePropagatesAfterChainRetirement() = runTest {
        val f = Fixture()
        f.failKeyRetirement()
        assertFailsWith<PKIException> { f.delete() }
        assertEquals(listOf("provider", "chain:chain"), f.events)
    }

    private class Fixture(external: Boolean = false) {
        val events = mutableListOf<String>()
        var providerDeletes = true
        private val now = Clock.System.now()
        private val key = KeyReferenceRecord(id = "key-id", tenantId = "tenant", alias = "alias", kid = "kid", providerId = "provider",
            origin = if (external) Origin.EXTERNAL else Origin.MANAGED,
            controlMode = if (external) ResourceControlMode.EXTERNALLY_MANAGED else ResourceControlMode.PLATFORM_MANAGED,
            createdAt = now, updatedAt = now)
        val chain = CertificateReferenceRecord(id = "chain", tenantId = "tenant", alias = "alias", providerId = "provider",
            kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN, source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
            linkedKeyReferenceId = key.id, certificateChainDer = CertificateReferenceRecord.encodeCertificateChain(listOf(byteArrayOf(1))),
            certificateFingerprint = ByteArray(32), publicKeyFingerprint = ByteArray(32), createdAt = now, updatedAt = now)
        var rows = listOf(chain)
        val certificates = mockk<CertificateReferenceStore>()
        val providers = mockk<ManagedKeyStoreWithProviderLookups>()
        private val keys = mockk<KeyReferenceStore>()
        private val registrar = mockk<ManagedKeyReferenceRegistrar>()
        private val execution = mockk<SessionExecution>(relaxed = true)
        private val selector: ManagedKeyStoreSelector
        init {
            every { execution.sessionContext.context.tenant.tenantId } returns "tenant"
            every { keys.isAvailable } returns true
            every { keys.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            coEvery { keys.findAllByAliasIncludingDeleted("tenant", "alias", "provider") } returns Ok(listOf(key))
            coEvery { keys.findAllByKidIncludingDeleted("tenant", "alias", "provider") } returns Ok(emptyList())
            coEvery { keys.delete("tenant", "alias", "provider") } answers { events += "key"; Ok(true) }
            every { certificates.isAvailable } returns true
            coEvery { certificates.findByLinkedKeyReferenceId("tenant", key.id) } answers { Ok(rows) }
            coEvery { certificates.deleteById("tenant", any()) } answers { events += "chain:${secondArg<String>()}"; Ok(true) }
            coEvery { providers.maintainsKeyReferenceIndex(any()) } returns false
            coEvery { providers.deleteKey(any()) } answers { events += "provider"; providerDeletes }
            coEvery { registrar.removeKeyReference(any()) } answers { events += "key"; Ok(true) }
            selector = ManagedKeyStoreSelector(providers, keys, mockk<ManagedKeyStoreModeResolver>(), registrar, execution, certificates)
        }
        fun failKeyRetirement() {
            coEvery { registrar.removeKeyReference(any()) } returns Err(IdkError.UNKNOWN_ERROR(message = "key write failed"))
        }
        suspend fun delete() = selector.deleteKey(KeyInfo<Jwk>(alias = "alias", providerId = "provider"))
    }
}
