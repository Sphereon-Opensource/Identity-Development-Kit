/* Copyright 2026 Sphereon International B.V. */
package com.sphereon.crypto.kms

import at.asitplus.awesn1.crypto.pki.Pkcs10CertificationRequest
import at.asitplus.awesn1.serialization.DER
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedKeyCsrTest {
    private val app = createCryptoTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId(
        "managed-csr-test", principalType = com.sphereon.di.context.PrincipalType.USER,
    )
    private val provider = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
        SoftwareKmsProviderConfig(
            id = "csr-non-exportable",
            cryptographyProvider = CryptographyProvider.Default.name,
            exposePrivateKeysDuringGeneration = false,
            persistKeysDuringGeneration = true,
        ),
        session.asCoreApiServiceGraph().serviceExecution,
    )
    private val keyManager = session.graph.asKeyManagerServiceGraph().keyManagerService
        .also { it.registerProvider(provider, makeDefaultKms = true) }
    private val certificates = CertificateServiceImpl(keyManager)

    @Test
    fun publicManagedSubjectProducesVerifiableCsrWithoutExportingPrivateKey() = runTest {
        assertManagedCsr(KeyVisibility.PUBLIC)
    }

    @Test
    fun privateRequestedManagedSubjectWorksWhenProviderReturnsOnlyPublicMaterial() = runTest {
        assertManagedCsr(KeyVisibility.PRIVATE)
    }

    @Test
    fun signOnlyManagedSubjectDoesNotNeedVerificationPermissionForConsistencyCheck() = runTest {
        assertManagedCsr(KeyVisibility.PUBLIC, arrayOf(KeyOperations.SIGN))
    }

    private suspend fun assertManagedCsr(visibility: KeyVisibility, operations: Array<KeyOperations>? = null) {
        val key = provider.generateKeyAsync(alias = "non-exportable-csr", alg = SignatureAlgorithm.ECDSA_SHA256, keyOperations = operations)
        assertNull(key.jose.privateJwk)
        val managed = key.joseToManagedKeyInfo(visibility)
        val subject = if (operations == null) managed else ManagedKeyInfo.fromKeyInfo(
            KeyInfo.fromDTO(managed).copy(key = key.jose.publicJwk.copy(key_ops = operations.map { it.jose }.toTypedArray())),
        )
        if (operations != null) {
            assertEquals(operations.map { it.jose }, subject.key.key_ops?.toList())
        }
        val csr = certificates.generateCSR(
            subjectKeyInfo = subject,
            distinguishedNameElements = X509DistinguishedNameElements(commonName = "Managed CSR"),
        )
        val parsed = DER.decodeFromByteArray<Pkcs10CertificationRequest>(csr.der)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(DER.encodeToByteArray(parsed.certificationRequestInfo.publicKey)),
        )
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(DER.encodeToByteArray(parsed.certificationRequestInfo))
        assertTrue(verifier.verify(parsed.signatureValue.rawBytes), "CSR signature must verify against its embedded subject key")
    }

    @Test
    fun managedSubjectCannotBorrowAnUnrelatedSigningKeyEvenWithMatchingMetadata() = runTest {
        val subject = provider.generateKeyAsync(alias = "csr-subject", alg = SignatureAlgorithm.ECDSA_SHA256)
        val other = provider.generateKeyAsync(alias = "other-csr-signer", alg = SignatureAlgorithm.ECDSA_SHA256)
        val mismatched = ManagedKeyInfo.fromKeyInfo(
            ResolvedKeyInfo(
                key = subject.jose.publicJwk.copy(kid = other.kid),
                keyVisibility = KeyVisibility.PUBLIC,
                alias = other.alias,
                providerId = provider.id,
                kid = other.kid,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            ),
        )
        val tracked = trackSigning()
        assertFailsWith<IllegalArgumentException> {
            certificates.generateCSR(mismatched, X509DistinguishedNameElements(commonName = "Wrong signer"))
        }
        assertEquals(0, tracked.signCalls, "Subject material must be bound before any signing")
    }

    @Test
    fun conflictingKidIsRejectedBeforeManagedSigning() = runTest {
        val subject = provider.generateKeyAsync(alias = "csr-kid", alg = SignatureAlgorithm.ECDSA_SHA256)
        val mismatched = ManagedKeyInfo.fromKeyInfo(
            KeyInfo.fromDTO(subject.joseToManagedKeyInfo()).copy(kid = "different-key-id"),
        )
        val tracked = trackSigning()
        assertFailsWith<IllegalArgumentException> {
            certificates.generateCSR(mismatched, X509DistinguishedNameElements(commonName = "Wrong kid"))
        }
        assertEquals(0, tracked.signCalls)
    }

    @Test
    fun explicitVerifyOnlyMaterialCannotAuthorizeManagedSigning() = runTest {
        val subject = provider.generateKeyAsync(alias = "csr-verify-only", alg = SignatureAlgorithm.ECDSA_SHA256)
        val restricted = ManagedKeyInfo.fromKeyInfo(
            KeyInfo.fromDTO(subject.joseToManagedKeyInfo()).copy(
                key = subject.jose.publicJwk.copy(key_ops = arrayOf(KeyOperations.VERIFY.jose)),
            ),
        )
        val tracked = trackSigning()
        assertFailsWith<IllegalArgumentException> {
            certificates.generateCSR(restricted, X509DistinguishedNameElements(commonName = "Verify only"))
        }
        assertEquals(0, tracked.signCalls)
    }

    @Test
    fun ordinaryResolvedPublicMaterialDoesNotSelectStoredPrivateKey() = runTest {
        val subject = provider.generateKeyAsync(alias = "csr-plain-public", alg = SignatureAlgorithm.ECDSA_SHA256)
        val publicInfo = ResolvedKeyInfo.fromKeyInfo<com.sphereon.crypto.core.jose.Jwk>(subject.joseToManagedKeyInfo())
        assertFailsWith<IllegalArgumentException> {
            certificates.generateCSR(publicInfo, X509DistinguishedNameElements(commonName = "Plain public"))
        }
    }

    @Test
    fun changedSigningKeyCannotProduceReturnedCsr() = runTest {
        val subject = provider.generateKeyAsync(alias = "csr-original", alg = SignatureAlgorithm.ECDSA_SHA256)
        val replacement = provider.generateKeyAsync(alias = "csr-replacement", alg = SignatureAlgorithm.ECDSA_SHA256)
        val tracked = trackSigning()
        tracked.replacement = KeyInfo<Nothing>(
            alias = replacement.alias, providerId = provider.id,
            kid = replacement.kid, signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        )
        assertFailsWith<IllegalArgumentException> {
            certificates.generateCSR(subject.joseToManagedKeyInfo(), X509DistinguishedNameElements(commonName = "Changed alias"))
        }
        assertEquals(1, tracked.signCalls)
    }

    private fun trackSigning() = RecordingProvider(provider).also { keyManager.registerProvider(it, makeDefaultKms = true) }

    private class RecordingProvider(private val delegate: KmsProvider) : KmsProvider by delegate {
        var signCalls = 0
        var replacement: KeyInfoType<*>? = null
        override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray {
            signCalls += 1
            return delegate.createRawSignature(replacement ?: keyInfo, input, requireX5Chain)
        }
    }
}
