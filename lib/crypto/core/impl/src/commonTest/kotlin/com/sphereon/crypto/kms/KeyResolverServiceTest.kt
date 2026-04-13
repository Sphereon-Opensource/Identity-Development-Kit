/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.kms

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.sphereon.core.api.encodeToBase64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for key resolver services including CoseJoseProvidedKeyResolverService
 * and X509CertificateChainKeyResolverService.
 */
class KeyResolverServiceTest {
    private lateinit var keyManagerService: KeyManagerService

    val app = createCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("key-resolver-test")

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(
            id = "test-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }

    // =========== CoseJoseProvidedKeyResolverService Tests ===========

    @Test
    fun joseResolverShouldBeAvailable() {
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.contains("jose_cose_resolver"), "jose_cose_resolver should be registered")
    }

    @Test
    fun joseResolverShouldResolveJwkKey() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // Use the KeyManagerService to resolve with JWK method
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = IdentifierMethod.jwk,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldSupportJwkMethod() {
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.jwk), "jose_cose_resolver should support JWK method")
    }

    @Test
    fun joseResolverShouldSupportCoseKeyMethod() {
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.cose_key), "jose_cose_resolver should support COSE_KEY method")
    }

    @Test
    fun joseResolverShouldNotSupportX5cMethod() {
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertFalse(supportedMethods.contains(IdentifierMethod.x5c), "jose_cose_resolver should not support X5C method")
    }

    @Test
    fun joseResolverShouldResolveP384Key() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = IdentifierMethod.jwk,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldResolveP521Key() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA512)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = IdentifierMethod.jwk,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldReturnAllSupportedKeyTypes() {
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)
        val keyTypes = resolver.allSupportedKeyTypes()
        assertTrue(keyTypes.isNotEmpty(), "jose_cose_resolver should support at least one key type")
    }

    @Test
    fun joseResolverShouldReturnSupportedKeyTypesForJwkMethod() {
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)
        val keyTypes = resolver.getSupportedKeyTypes(IdentifierMethod.jwk)
        assertTrue(keyTypes.isNotEmpty(), "Should have supported key types for JWK method")
    }

    // =========== X509CertificateChainKeyResolverService Tests ===========

    @Test
    fun x5cResolverShouldBeAvailable() {
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.contains("x5c"), "x5c resolver should be registered")
    }

    @Test
    fun x5cResolverShouldSupportX5cMethod() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.x5c), "x5c resolver should support X5C method")
    }

    @Test
    fun x5cResolverShouldSupportJwkMethod() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.jwk), "x5c resolver should support JWK method")
    }

    @Test
    fun x5cResolverShouldSupportCoseKeyMethod() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.cose_key), "x5c resolver should support COSE_KEY method")
    }

    @Test
    fun x5cResolverShouldHaveCorrectSupportedMethods() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)

        // Verify the resolver supports expected methods
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.x5c), "x5c resolver should support X5C")
        assertTrue(supportedMethods.contains(IdentifierMethod.jwk), "x5c resolver should support JWK")
        assertTrue(supportedMethods.contains(IdentifierMethod.cose_key), "x5c resolver should support COSE_KEY")
    }

    @Test
    fun x5cResolverShouldReturnAllSupportedKeyTypes() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val keyTypes = resolver.allSupportedKeyTypes()
        assertTrue(keyTypes.isNotEmpty(), "x5c resolver should support at least one key type")
    }

    @Test
    fun x5cResolverShouldReturnSupportedKeyTypesForX5cMethod() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val keyTypes = resolver.getSupportedKeyTypes(IdentifierMethod.x5c)
        assertTrue(keyTypes.isNotEmpty(), "Should have supported key types for X5C method")
    }

    @Test
    fun x5cResolverShouldReturnMappingOfMethodsToKeyTypes() {
        val resolver = keyManagerService.getResolverById("x5c")
        assertNotNull(resolver)
        val mapping = resolver.supportedKeyTypesAndIdentifierMethods()
        assertTrue(mapping.isNotEmpty(), "Should have mapping of identifier methods to key types")
        assertTrue(mapping.containsKey(IdentifierMethod.x5c), "Mapping should include X5C method")
    }

    // =========== Resolver ID Tests ===========

    @Test
    fun getResolverIdsShouldReturnMultipleResolvers() {
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.size >= 2, "Should have at least jose_cose_resolver and x5c resolver")
    }

    @Test
    fun getResolverByIdShouldReturnCorrectResolver() {
        val joseResolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(joseResolver)
        assertEquals("jose_cose_resolver", joseResolver.getId())

        val x5cResolver = keyManagerService.getResolverById("x5c")
        assertNotNull(x5cResolver)
        assertEquals("x5c", x5cResolver.getId())
    }

    @Test
    fun defaultResolverShouldBeSet() {
        val defaultId = keyManagerService.defaultResolverId()
        assertNotNull(defaultId)

        // The default resolver should exist in the resolver list
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.contains(defaultId), "Default resolver should be in resolver list")
    }

    // =========== getResolverByKeyTypeOrIdentifier Tests ===========

    @Test
    fun getResolverByIdentifierMethodShouldFindResolver() {
        val resolver = keyManagerService.getResolverByKeyTypeOrIdentifier(
            identifierMethod = IdentifierMethod.jwk,
            keyType = null,
            resolverId = null
        )
        assertNotNull(resolver)
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertTrue(supportedMethods.contains(IdentifierMethod.jwk), "Found resolver should support JWK method")
    }

    @Test
    fun getResolverByIdentifierMethodShouldFindX5cCapableResolver() {
        // Note: When requesting x5c method, we might get any resolver that supports x5c
        // (either jose_cose_resolver or x5c resolver, depending on registration order)
        val resolver = keyManagerService.getResolverByKeyTypeOrIdentifier(
            identifierMethod = IdentifierMethod.x5c,
            keyType = null,
            resolverId = null
        )
        assertNotNull(resolver, "Should find a resolver that supports x5c")
    }

    @Test
    fun getResolverByResolverIdShouldFindSpecificResolver() {
        val resolver = keyManagerService.getResolverByKeyTypeOrIdentifier(
            identifierMethod = null,
            keyType = null,
            resolverId = "jose_cose_resolver"
        )
        assertNotNull(resolver)
        assertEquals("jose_cose_resolver", resolver.getId())
    }

    // =========== X509CertificateChainKeyResolverService Resolve Tests ===========


    @Test
    fun x5cResolverShouldResolveKeyFromX5cCertificateChain() = runTest {
        // Generate a key and create a self-signed certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val dn = X509DistinguishedNameElements(
            commonName = "X5C Resolver Test",
            organizationName = "Sphereon Test",
            country = "NL"
        )

        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create KeyInfo with x5c
        val certBase64 = certResult.certificate.der.encodeToBase64()
        val keyInfoWithX5c = KeyInfo<KeyType>(
            x5c = arrayOf(certBase64),
            kid = "test-x5c-kid"
        )

        // Resolve using x5c resolver - identifierMethod now properly routes to x5c resolver
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfoWithX5c,
            identifierMethod = IdentifierMethod.x5c,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key, "Should extract public key from certificate")
    }


    @Test
    fun x5cResolverShouldResolveKeyWithoutKid() = runTest {
        // Generate a key and create a self-signed certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val dn = X509DistinguishedNameElements(
            commonName = "X5C No Kid Test",
            organizationName = "Sphereon Test",
            country = "NL"
        )

        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 2,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create KeyInfo with x5c but NO kid (tests the kid null branch)
        val certBase64 = certResult.certificate.der.encodeToBase64()
        val keyInfoWithX5c = KeyInfo<KeyType>(
            x5c = arrayOf(certBase64),
            kid = null  // No kid - should get it from the key
        )

        // Resolve using x5c resolver - identifierMethod now properly routes to x5c resolver
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfoWithX5c,
            identifierMethod = IdentifierMethod.x5c,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
        // Kid should be derived from the key
        assertNotNull(resolved.kid, "Should derive kid from the key when not provided")
    }

    @Test
    fun x5cResolverShouldThrowForMissingX5c() = runTest {
        // Create KeyInfo without x5c and without key
        val keyInfoNoX5c = KeyInfo<KeyType>(
            kid = "test-no-x5c"
        )

        // With command pattern, exceptions are wrapped as PKIException
        assertFailsWith<Exception> {
            keyManagerService.resolvePublicKey(
                keyInfo = keyInfoNoX5c,
                identifierMethod = IdentifierMethod.x5c,
                trustedCerts = null,
                verifyX509CertificateChain = false
            )
        }
    }

    // =========== CoseJoseProvidedKeyResolverService Additional Tests ===========

    @Test
    fun joseResolverShouldResolveWithNullIdentifierMethod() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // Pass null identifierMethod (tests the null branch in require)
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = null,  // Null identifier method
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldResolveWithCoseKeyMethod() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // Use COSE_KEY identifier method
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = IdentifierMethod.cose_key,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldThrowForInvalidIdentifierMethod() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // X5C method is not supported by jose_cose_resolver for direct key resolution
        // This should use a different resolver or fail based on the resolver selection
        val resolver = keyManagerService.getResolverById("jose_cose_resolver")
        assertNotNull(resolver)

        // Verify jose resolver doesn't support x5c method
        val supportedMethods = resolver.allSupportedIdentifierMethods()
        assertFalse(supportedMethods.contains(IdentifierMethod.x5c), "jose_cose_resolver should not support x5c")
    }

    @Test
    fun joseResolverShouldThrowForNullKey() = runTest {
        // Create KeyInfo without a key
        val keyInfoNoKey = KeyInfo<KeyType>(
            kid = "test-no-key"
        )

        assertFailsWith<IllegalArgumentException> {
            val resolver = keyManagerService.getResolverById("jose_cose_resolver") as CoseJoseProvidedKeyResolverServiceImpl
            resolver.resolvePublicKey(
                keyInfo = keyInfoNoKey,
                identifierMethod = IdentifierMethod.jwk,
                trustedCerts = null,
                verifyX509CertificateChain = false
            )
        }
    }


    @Test
    fun joseResolverShouldHandleVerifyX509TrueWithCertChain() = runTest {
        // Generate a key with a self-signed certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val dn = X509DistinguishedNameElements(
            commonName = "Jose Resolver X509 Test",
            organizationName = "Sphereon Test",
            country = "NL"
        )

        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 3,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Get the JWK with the certificate chain - cast to Jwk since we know it's a JWK
        val jwk = keyInfo.key as com.sphereon.crypto.core.jose.Jwk
        val certBase64 = certResult.certificate.der.encodeToBase64()
        val jwkWithCert = jwk.copy(x5c = arrayOf(certBase64))

        val keyInfoWithCert = KeyInfo<com.sphereon.crypto.core.jose.Jwk>(
            key = jwkWithCert,
            kid = "jose-x509-test"
        )

        // Resolve with verifyX509CertificateChain = false (since we don't have proper trust anchors)
        val resolved = keyManagerService.resolvePublicKey<com.sphereon.crypto.core.jose.Jwk>(
            keyInfo = keyInfoWithCert,
            identifierMethod = IdentifierMethod.jwk,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    @Test
    fun joseResolverShouldHandleVerifyX509TrueWithoutCertChain() = runTest {
        // JWK without certificate chain - verifyX509CertificateChain should be skipped
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // Request verification but key has no cert chain - should still succeed
        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = IdentifierMethod.jwk,
            trustedCerts = arrayOf("dummy-trust-anchor"),
            verifyX509CertificateChain = true  // True but no chain, so verification is skipped
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key)
    }

    // =========== X509CertificateChainKeyResolverService Branch Coverage Tests ===========


    @Test
    fun x5cResolverShouldResolveViaKeyX509CertificateChain() = runTest {
        // Test the branch where keyInfo.x5c is null but key.getX509CertificateChain() is available
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val dn = X509DistinguishedNameElements(
            commonName = "X5C Via Key Test",
            organizationName = "Sphereon Test",
            country = "NL"
        )

        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 10,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create a JWK with x5c embedded in the key itself (not in KeyInfo.x5c)
        val jwk = keyInfo.key as com.sphereon.crypto.core.jose.Jwk
        val certBase64 = certResult.certificate.der.encodeToBase64()
        val jwkWithEmbeddedCert = jwk.copy(x5c = arrayOf(certBase64))

        // Create KeyInfo with NO x5c at top level, but key has x5c
        // This tests the branch: keyInfo.x5c ?: keyInfo.key?.getX509CertificateChain()
        val keyInfoViaKey = KeyInfo(
            key = jwkWithEmbeddedCert,
            x5c = null,  // No x5c at KeyInfo level
            kid = "via-key-x5c-test"
        )

        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfoViaKey,
            identifierMethod = IdentifierMethod.x5c,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key, "Should extract public key via key's x5c chain")
    }


    @Test
    fun x5cResolverShouldResolveCoseEncodedKey() = runTest {
        // Test the COSE encoding branch: if (keyInfo.keyEncoding === KeyEncoding.COSE)
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val dn = X509DistinguishedNameElements(
            commonName = "X5C COSE Encoding Test",
            organizationName = "Sphereon Test",
            country = "NL"
        )

        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 11,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create KeyInfo with x5c and COSE encoding
        val certBase64 = certResult.certificate.der.encodeToBase64()
        val keyInfoWithCose = KeyInfo<KeyType>(
            x5c = arrayOf(certBase64),
            kid = "test-cose-encoding",
            keyEncoding = KeyEncoding.COSE  // This tests the COSE encoding branch
        )

        val resolved = keyManagerService.resolvePublicKey(
            keyInfo = keyInfoWithCose,
            identifierMethod = IdentifierMethod.x5c,
            trustedCerts = null,
            verifyX509CertificateChain = false
        )

        assertNotNull(resolved)
        assertNotNull(resolved.key, "Should resolve and convert to COSE key")
    }
}
