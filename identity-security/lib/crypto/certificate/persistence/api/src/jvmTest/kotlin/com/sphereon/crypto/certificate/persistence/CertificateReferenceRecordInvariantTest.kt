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

import com.sphereon.crypto.core.ResourceControlMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock

class CertificateReferenceRecordInvariantTest {
    private val certificate = byteArrayOf(0x30, 0x03, 0x01, 0x01, 0x00)
    private val issuer = byteArrayOf(0x30, 0x02, 0x05, 0x00)
    private val publicSpki = byteArrayOf(0x30, 0x01, 0x00)

    @Test
    fun enumsUseStableWireValues() {
        val json = Json { encodeDefaults = true }

        assertEquals("\"trusted_certificate\"", json.encodeToString(CertificateReferenceKind.TRUSTED_CERTIFICATE))
        assertEquals("\"key_certificate_chain\"", json.encodeToString(CertificateReferenceKind.KEY_CERTIFICATE_CHAIN))
        assertEquals("\"stored_public_material\"", json.encodeToString(CertificateReferenceSource.STORED_PUBLIC_MATERIAL))
        assertEquals("\"provider_native\"", json.encodeToString(CertificateReferenceSource.PROVIDER_NATIVE))
    }

    @Test
    fun chainEncodingIsVersionedCountedLengthPrefixedAndOrdered() {
        val encoded = CertificateReferenceRecord.encodeCertificateChain(listOf(certificate, issuer))

        assertContentEquals(
            byteArrayOf(
                1,
                0, 0, 0, 2,
                0, 0, 0, certificate.size.toByte(),
                *certificate,
                0, 0, 0, issuer.size.toByte(),
                *issuer,
            ),
            encoded,
        )
        assertEquals(2, CertificateReferenceRecord.decodeCertificateChain(encoded).size)
        assertContentEquals(certificate, CertificateReferenceRecord.decodeCertificateChain(encoded)[0])
        assertContentEquals(issuer, CertificateReferenceRecord.decodeCertificateChain(encoded)[1])
    }

    @Test
    fun chainFramingReadsNonzeroHigherOrderCountAndLengthBytes() {
        val longCertificate = ByteArray(300) { index -> (index and 0xff).toByte() }
        val manyCertificates = List(256) { index -> byteArrayOf(index.toByte()) }

        val longEntry = CertificateReferenceRecord.decodeCertificateChain(
            CertificateReferenceRecord.encodeCertificateChain(listOf(longCertificate)),
        )
        assertEquals(1, longEntry.size)
        assertContentEquals(longCertificate, longEntry.single())

        val manyEntries = CertificateReferenceRecord.decodeCertificateChain(
            CertificateReferenceRecord.encodeCertificateChain(manyCertificates),
        )
        assertEquals(256, manyEntries.size)
        assertContentEquals(manyCertificates[255], manyEntries[255])
    }

    @Test
    fun unframedConcatenatedDerIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            CertificateReferenceRecord.decodeCertificateChain(byteArrayOf(0x30, 0x03, 0x01, 0x01, 0x00, 0x30, 0x02, 0x05, 0x00))
        }
    }

    @Test
    fun fingerprintsAreSha256AndRecordRequiresExactDigestLengths() {
        val certificateFingerprint = CertificateReferenceRecord.certificateFingerprintOf(certificate)
        val publicKeyFingerprint = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(publicSpki)
        assertEquals(32, certificateFingerprint.size)
        assertEquals(32, publicKeyFingerprint.size)
        assertContentEquals(certificateFingerprint, CertificateReferenceRecord.certificateFingerprintOf(certificate.copyOf()))

        assertFailsWith<IllegalArgumentException> {
            validRecord(certificateChainDer = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validRecord(certificateFingerprint = ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            validRecord(publicKeyFingerprint = ByteArray(33))
        }
    }

    @Test
    fun sourceAndKindRulesPreventSecretOrUnboundReferences() {
        val stored = validRecord()
        assertEquals(CertificateReferenceSource.STORED_PUBLIC_MATERIAL, stored.source)
        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, stored.controlMode)

        assertFailsWith<IllegalArgumentException> {
            validRecord(source = CertificateReferenceSource.PROVIDER_NATIVE)
        }
        assertFailsWith<IllegalArgumentException> {
            validRecord(source = CertificateReferenceSource.PROVIDER_NATIVE, certificateChainDer = null)
                .copy(certificateChainDer = CertificateReferenceRecord.encodeCertificateChain(listOf(certificate)))
        }
        assertFailsWith<IllegalArgumentException> {
            validRecord(
                kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                linkedKeyReferenceId = null,
            )
        }
    }

    @Test
    fun trustedCertificateRejectsLinkedKeyReference() {
        val trusted = validRecord(
            kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
            linkedKeyReferenceId = null,
        )
        assertEquals(CertificateReferenceKind.TRUSTED_CERTIFICATE, trusted.kind)

        assertFailsWith<IllegalArgumentException> {
            validRecord(
                kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                linkedKeyReferenceId = "key-reference-1",
            )
        }
    }

    private fun validRecord(
        kind: CertificateReferenceKind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
        source: CertificateReferenceSource = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
        linkedKeyReferenceId: String? = "key-reference-1",
        certificateChainDer: ByteArray? = CertificateReferenceRecord.encodeCertificateChain(listOf(certificate, issuer)),
        certificateFingerprint: ByteArray = CertificateReferenceRecord.certificateFingerprintOf(certificate),
        publicKeyFingerprint: ByteArray = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(publicSpki),
    ): CertificateReferenceRecord = CertificateReferenceRecord(
        id = "certificate-reference-1",
        tenantId = "tenant-1",
        alias = "document-signer",
        providerId = "cloud-kms",
        providerCertificateId = "certificate-version-1",
        kind = kind,
        source = source,
        controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
        linkedKeyReferenceId = linkedKeyReferenceId,
        certificateChainDer = certificateChainDer,
        certificateFingerprint = certificateFingerprint,
        publicKeyFingerprint = publicKeyFingerprint,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )
}
