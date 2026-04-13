/*
 * © 2026 Sphereon International B.V.
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

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.core.x509.wrapX509CertificatePem
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Extension function to access CertificateService from a session graph.
 */
fun Any.asCertificateServiceGraph(): CertificateService.Graph = this as CertificateService.Graph

/**
 * Tests for CertificateService implementation.
 */
class CertificateServiceImplTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var certificateService: CertificateService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("certificate-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        // Get services from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        certificateService = session.graph.asCertificateServiceGraph().certificateService
    }

    // =========== CSR Generation Tests ===========

    @Test
    fun generateCSRShouldSucceedWithValidP256Key() =
        runTest {
            // Generate a P-256 key for CSR
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            // Convert ManagedKeyPair to ResolvedKeyInfoType via joseToManagedKeyInfo
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn =
                X509DistinguishedNameElements(
                    commonName = "Test Subject",
                    organizationName = "Test Org",
                    organizationUnit = "Test Unit",
                    locality = "Test City",
                    state = "Test State",
                    country = "US",
                )

            val csr =
                certificateService.generateCSR(
                    subjectKeyInfo = keyInfo,
                    distinguishedNameElements = dn,
                    serialNumber = 1,
                )

            assertNotNull(csr)
            assertNotNull(csr.der)
            assertTrue(csr.der.isNotEmpty(), "CSR DER should not be empty")
            val pem = csr.toPem()
            assertNotNull(pem)
            assertTrue(pem.startsWith("-----BEGIN CERTIFICATE REQUEST-----"))
        }

    @Test
    fun generateCSRShouldSucceedWithValidP384Key() =
        runTest {
            // Generate a P-384 key for CSR
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA384,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn =
                X509DistinguishedNameElements(
                    commonName = "Test Subject P384",
                )

            val csr =
                certificateService.generateCSR(
                    subjectKeyInfo = keyInfo,
                    distinguishedNameElements = dn,
                    serialNumber = 1,
                )

            assertNotNull(csr)
            assertTrue(csr.der.isNotEmpty())
        }

    @Test
    fun generateCSRShouldIncludeAllDNElements() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn =
                X509DistinguishedNameElements(
                    commonName = "My Common Name",
                    organizationName = "My Organization",
                    organizationUnit = "My Unit",
                    locality = "Amsterdam",
                    state = "North Holland",
                    country = "NL",
                    email = "test@example.com",
                )

            val csr =
                certificateService.generateCSR(
                    subjectKeyInfo = keyInfo,
                    distinguishedNameElements = dn,
                    serialNumber = 42,
                )

            assertNotNull(csr)
            // Verify DN elements are preserved in the result
            assertEquals("My Common Name", csr.commonName)
            assertEquals("My Organization", csr.organization)
            assertEquals("My Unit", csr.organizationalUnit)
            assertEquals("Amsterdam", csr.locality)
            assertEquals("North Holland", csr.state)
            assertEquals("NL", csr.country)
            assertEquals("test@example.com", csr.email)
            assertEquals(42, csr.serialNumber)
        }

    @Test
    fun generateCSRShouldFailWithZeroSerialNumber() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn = X509DistinguishedNameElements(commonName = "Test")

            assertFailsWith<IllegalArgumentException> {
                certificateService.generateCSR(
                    subjectKeyInfo = keyInfo,
                    distinguishedNameElements = dn,
                    serialNumber = 0,
                )
            }
        }

    @Test
    fun generateCSRShouldFailWithNegativeSerialNumber() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn = X509DistinguishedNameElements(commonName = "Test")

            assertFailsWith<IllegalArgumentException> {
                certificateService.generateCSR(
                    subjectKeyInfo = keyInfo,
                    distinguishedNameElements = dn,
                    serialNumber = -1,
                )
            }
        }

    // =========== Certificate Creation Tests ===========

    @Test
    fun createCertificateShouldSucceedWithValidKeys() =
        runTest {
            // Generate issuer key
            val issuerKeyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(issuerKeyPair)

            // Generate subject key
            val subjectKeyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(subjectKeyPair)

            // Convert to key info types
            val issuerKeyInfo = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val subjectKeyInfo = subjectKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuerDN =
                X509DistinguishedNameElements(
                    commonName = "Test Issuer CA",
                    organizationName = "Test CA Org",
                    country = "US",
                )

            val subjectDN =
                X509DistinguishedNameElements(
                    commonName = "Test Subject",
                    organizationName = "Test Subject Org",
                    country = "US",
                )

            val notBefore = LocalDateTimeKMP.now()
            // Add 365 days using kotlinx.datetime (DAY requires TimeZone)
            val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
            val notAfter =
                LocalDateTimeKMP.fromString(
                    notAfterInstant.toLocalDateTime(TimeZone.UTC).toString(),
                )

            val certResult =
                certificateService.createCertificate(
                    issuerKeyInfo = issuerKeyInfo,
                    issuer = issuerDN,
                    subjectKeyInfo = subjectKeyInfo,
                    subject = subjectDN,
                    serialNumber = 1,
                    notBefore = notBefore,
                    notAfter = notAfter,
                )

            assertNotNull(certResult)
            assertNotNull(certResult.certificate)
            val certPem = wrapX509CertificatePem(certResult.certificate.derToBase64())
            assertTrue(certPem.startsWith("-----BEGIN CERTIFICATE-----"))
        }

    @Test
    fun createSelfSignedCertificateShouldSucceed() =
        runTest {
            // Generate a key for self-signed certificate
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn =
                X509DistinguishedNameElements(
                    commonName = "Self-Signed Test",
                    organizationName = "Self-Signed Org",
                    country = "US",
                )

            val notBefore = LocalDateTimeKMP.now()
            val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
            val notAfter =
                LocalDateTimeKMP.fromString(
                    notAfterInstant.toLocalDateTime(TimeZone.UTC).toString(),
                )

            // Use same key as both issuer and subject for self-signed
            val certResult =
                certificateService.createCertificate(
                    issuerKeyInfo = keyInfo,
                    issuer = dn,
                    subjectKeyInfo = keyInfo,
                    subject = dn,
                    serialNumber = 1,
                    notBefore = notBefore,
                    notAfter = notAfter,
                )

            assertNotNull(certResult)
            assertNotNull(certResult.certificate)
        }

    // =========== CSR to Certificate Tests ===========

    @Test
    fun createCertificateFromCSRShouldSucceed() =
        runTest {
            // Generate issuer key
            val issuerKeyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(issuerKeyPair)

            // Generate subject key
            val subjectKeyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(subjectKeyPair)

            // Convert to key info types
            val issuerKeyInfo = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val subjectKeyInfo = subjectKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val subjectDN =
                X509DistinguishedNameElements(
                    commonName = "CSR Subject",
                    organizationName = "CSR Org",
                    country = "NL",
                )

            // Generate CSR
            val csr =
                certificateService.generateCSR(
                    subjectKeyInfo = subjectKeyInfo,
                    distinguishedNameElements = subjectDN,
                    serialNumber = 1,
                )
            assertNotNull(csr)

            val issuerDN =
                X509DistinguishedNameElements(
                    commonName = "CSR Issuer CA",
                    organizationName = "CA Org",
                    country = "NL",
                )

            val notBefore = LocalDateTimeKMP.now()
            val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
            val notAfter =
                LocalDateTimeKMP.fromString(
                    notAfterInstant.toLocalDateTime(TimeZone.UTC).toString(),
                )

            val certResult =
                certificateService.createCertificateFromCSR(
                    issuerKeyInfo = issuerKeyInfo,
                    issuer = issuerDN,
                    subjectKeyInfo = subjectKeyInfo,
                    csr = csr,
                    serialNumber = 100,
                    notBefore = notBefore,
                    notAfter = notAfter,
                )

            assertNotNull(certResult)
            assertNotNull(certResult.certificate)
            assertTrue(certResult.certificate.der.isNotEmpty())
        }
}
