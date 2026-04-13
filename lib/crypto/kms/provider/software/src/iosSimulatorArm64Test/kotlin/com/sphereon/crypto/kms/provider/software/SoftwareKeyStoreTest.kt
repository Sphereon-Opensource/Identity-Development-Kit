package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyStoreConfigImpl
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.kms.keystore.software.SoftwareKeyStoreService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SoftwareKeyStoreTest {
    private lateinit var softwareStoreService: SoftwareKeyStoreService

    private lateinit var softwareKmsProvider: SoftwareKmsProvider

    val app = createNativeSoftwareProviderTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("test")

    private val storeConfig = KeyStoreConfigImpl(
        keyStoreType = PredefinedKeyStoreTypes.APPLE.keyStoreType,
        id = "apple-keychain",
        keyVisibility = KeyVisibility.PRIVATE.keyVisibility
    )
    val kmsProviderConfig = SoftwareKmsProviderConfig(
        id = "softwareKeyStoreTest",
        exposePrivateKeysDuringGeneration = true,
        persistKeysDuringGeneration = false,
        keyStore = storeConfig
    )

    private val certBase64 = "MIIByTCCAW6gAwIBAgIQF+SpO+EPJj42boA4WHPL2zAKBggqhkjOPQQDAjBAMT4wPAYDVQQDEzVBY2MgS2l3YSBEaWdpdGFsIENlcnRpZmljYXRpb24gV2FsbGV0IEludGVybWVkaWF0ZSBDQTAeFw0yNTA2MjUxMzA4MzJaFw0yNjA2MjUxMzA4MzJaMDYxNDAyBgNVBAMTK3dhbGxldC02NDA2MzA3OS02ZmM3LTRhZTgtOWEwMi0yNTE4YTRhYmQyZmIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASnaLC4eYSs/8XkDz8rDANemjmyI+N5g0yIJIiScgCnjZogmlDGGQ8HYCdvs9SJJJ3c17YSax44vVl6LMnidvKno1QwUjAJBgNVHRMEAjAAMB0GA1UdDgQWBBRI7w4/H9JCty7T4aVl+im74XEV0zAOBgNVHQ8BAf8EBAMCB4AwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwIwCgYIKoZIzj0EAwIDSQAwRgIhAPnLEppg5TGMCqp/Nn+2os6vupEclyKv1yk/JQFQME8TAiEAiYrVm/6J8zGmhtiG958kZB0afXhM/i3DuY8+0kpTUzA="

    @BeforeTest
    fun beforeEach() {
        app as NativeSoftwareProviderTestAppComponent
        softwareKmsProvider = app.softwareKmsProvider.create(kmsProviderConfig, session.sessionExecution)
        softwareStoreService = softwareKmsProvider.keyStore as SoftwareKeyStoreService

    }

    @AfterTest
    fun afterEach() {
        println("Cleaning up after test...")
        runBlocking {
            // Clean up all keys from keychain
            val keys = softwareStoreService.listKeys()
            keys.forEach { keyInfo ->
                softwareStoreService.deleteKey(keyInfo)
            }

            // Clean up all certificate chains
            val certChainAliases = softwareStoreService.listCertificateChainAliases()
            certChainAliases.forEach { alias ->
                softwareStoreService.deleteCertificateChain(alias)
            }

            // Clean up all certificates
            val certAliases = softwareStoreService.listCertificateAliases()
            certAliases.forEach { alias ->
                softwareStoreService.deleteCertificate(alias)
            }
        }
        println("Cleaning up after test... done")

    }

    @Test
    fun `should store a EC key`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "testalias8"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            x5c = arrayOf(certBase64)
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = alias
        )

        val keys = softwareStoreService.listKeys()
        assertEquals(1, keys.size)

        val key = softwareStoreService.getKey(KeyInfo<Jwk>(alias = alias))


        assertEquals(alias, key.alias)
    }

    @Test
    fun `should store a RSA key`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.RSA,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
            x5c = arrayOf(certBase64)
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = alias
        )

        val keys = softwareStoreService.listKeys()
        val key = softwareStoreService.getKey(KeyInfo<Jwk>(alias = alias))

        assertEquals(1, keys.size)
        assertEquals(alias, key.alias)
    }

    @Test
    fun `should delete a key`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            x5c = arrayOf(certBase64)
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = alias
        )

        val keyCount = softwareStoreService.listKeys().size
        assertEquals(1, keyCount)

        softwareStoreService.deleteKey(resolvedKeyInfo)

        val keyCountDelete = softwareStoreService.listKeys().size

        assertEquals(0, keyCountDelete)
    }

    @Test
    fun `should list all keys`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            x5c = arrayOf(certBase64)
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = "test-key-alias1"
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = "test-key-alias2"
        )

        val keys = softwareStoreService.listKeys()
        println(keys.map { it.alias }.joinToString(","))

        val keyCount = keys.size

        assertEquals(2, keys.size)
        assertEquals(2, keyCount)
    }

    @Test
    fun `should get a specific key`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            x5c = arrayOf(certBase64)
        )
        softwareStoreService.storeKey(
            resolvedKeyInfo,
            providerId = softwareKmsProvider.id,
            alias = alias
        )

        val key = softwareStoreService.getKey(KeyInfo<Jwk>(alias = alias))

        assertEquals(alias, key.alias)
    }

    @Test
    fun `should store a certificate chain`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val cert = certificateFromDer(derBytes)

        softwareStoreService.storeCertificateChain(alias, arrayOf(cert), resolvedKeyInfo)

        val certChain = softwareStoreService.getCertificateChain(alias)

        assertEquals(1, certChain.size)
    }

    @Test
    fun `should delete a certificate chain`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val cert = certificateFromDer(derBytes)
        softwareStoreService.storeCertificateChain(alias, arrayOf(cert), resolvedKeyInfo)

        val certChain = softwareStoreService.getCertificateChain(alias)
        assertEquals(1, certChain.size)

        val deleted = softwareStoreService.deleteCertificateChain(alias)

        // After deletion, trying to get the chain should throw an exception
        try {
            softwareStoreService.getCertificateChain(alias)
            kotlin.test.fail("Expected PKIException when getting deleted certificate chain")
        } catch (e: com.sphereon.crypto.core.PKIException) {
            // Expected
        }
    }

    @Test
    fun `should list all certificate chains`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias1 = "test-alias1"
        val alias2 = "test-alias2"
        val resolvedKeyInfo1 = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias1,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )
        val resolvedKeyInfo2 = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias2,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val cert = certificateFromDer(derBytes)

        softwareStoreService.storeCertificateChain(alias1, arrayOf(cert), resolvedKeyInfo1)
        softwareStoreService.storeCertificateChain(alias2, arrayOf(cert), resolvedKeyInfo2)

        val certChains = softwareStoreService.listCertificateChainAliases()

        val certChainCount = certChains.size

        assertEquals(2, certChains.size)
        assertEquals(2, certChainCount)
    }

    @Test
    fun `should get a certificate chain by alias`() = runTest {
        val keyPair = softwareKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val alias = "test-alias"
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk ?: keyPair.jose.publicJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            kid = keyPair.kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val cert = certificateFromDer(derBytes)
        softwareStoreService.storeCertificateChain(alias, arrayOf(cert), resolvedKeyInfo)

        val certChain = softwareStoreService.getCertificateChain(alias)

        assertEquals(1, certChain.size)
    }

    @Test
    fun `should store a certificate`() = runTest {
        val alias = "test-alias"
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val certificate = certificateFromDer(derBytes)

        softwareStoreService.storeTrustedCertificate(alias, certificate)

        val cert = softwareStoreService.getCertificate(alias)

        assertNotNull(cert)
    }

    @Test
    fun `should delete certificate`() = runTest {
        val alias = "test-alias"
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val certificate = certificateFromDer(derBytes)

        softwareStoreService.storeTrustedCertificate(alias, certificate)

        val cert = softwareStoreService.getCertificate(alias)
        assertNotNull(cert)

        val deleted = softwareStoreService.deleteCertificate(alias)

        // After deletion, trying to get the certificate should throw an exception
        try {
            softwareStoreService.getCertificate(alias)
            kotlin.test.fail("Expected PKIException when getting deleted certificate")
        } catch (e: com.sphereon.crypto.core.PKIException) {
            // Expected
        }
    }

    @Test
    fun `should list all certificates`() = runTest {
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val certificate = certificateFromDer(derBytes)

        softwareStoreService.storeTrustedCertificate("test-alias1", certificate)
        softwareStoreService.storeTrustedCertificate("test-alias2", certificate)

        val certs = softwareStoreService.listCertificateAliases()

        val certCount = certs.size

        assertEquals(2, certs.size)
        assertEquals(2, certCount)
    }

    @Test
    fun `should get certificate by alias`() = runTest {
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val certificate = certificateFromDer(derBytes)
        val alias = "test-alias"
        softwareStoreService.storeTrustedCertificate(alias, certificate)

        val cert = softwareStoreService.getCertificate(alias)

        assertNotNull(cert)
    }


}
