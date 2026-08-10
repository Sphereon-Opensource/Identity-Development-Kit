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

package com.sphereon.crypto.core.x509

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for X509VerifyServiceJvmAdapter and related X.509 verification functionality.
 */
class X509VerifyServiceTest {
    private lateinit var keyManagerService: KeyManagerService

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("x509-verify-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    private val testCertificate =
        Certificate(
            der = byteArrayOf(0x30.toByte(), 0x82.toByte(), 0x01.toByte(), 0x00.toByte()),
            fingerPrint = "ABC123",
            serialNumber = "123456",
            issuerDN = "CN=Test Issuer,O=Test Org",
            subjectDN = "CN=Test Subject,O=Test Org",
            notBefore = Instant.parse("2024-01-01T00:00:00Z"),
            notAfter = Instant.parse("2025-12-31T23:59:59Z"),
        )

    private val testJwk =
        Jwk(
            generateKid = false,
            kty = JwaKeyType.EC,
            crv = com.sphereon.crypto.core.jose.JwaCurve.P_256,
            x = "testX",
            y = "testY",
        )

    private val testVerificationTime = LocalDateTimeKMP.now()

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "x509-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as JvmCryptoTestAppGraph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(DefaultCallbacks)
    }

    // =========== X509VerifyServiceJvmAdapter Registration Tests ===========

    @Test
    fun x509CallbackShouldBeRegisterable() {
        val adapter = X509VerifyServiceJvmAdapter()

        // Test that register() can be called without error
        adapter.register()

        // After registration, DefaultCallbacks should have the callback
        assertTrue(DefaultCallbacks.hasX509Default())
    }

    @Test
    fun x509CallbackShouldHandleDisabledVerification() {
        val adapter = X509VerifyServiceJvmAdapter()

        val testRequest =
            X509VerificationRequest(
                enabled = false,
                chainPEM = arrayOf("test-pem"),
                trustedCerts = arrayOf("trusted-cert"),
                verificationProfile = X509VerificationProfile.RFC_5280,
                verificationTime = testVerificationTime,
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        val result = adapter.verifyCertificateChainUsingPlatformCallback(context)

        assertNotNull(result)
        assertFalse(result.error, "Disabled verification should return error=false")
    }

    @Test
    fun x509CallbackShouldFailForEmptyCertificateChainWhenEnabled() {
        val adapter = X509VerifyServiceJvmAdapter()

        val testRequest =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("test-pem"),
                trustedCerts = arrayOf("trusted-cert"),
                verificationProfile = X509VerificationProfile.RFC_5280,
                verificationTime = testVerificationTime,
            )

        // Create context with empty certificate chain
        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = emptyArray(),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        val result = adapter.verifyCertificateChainUsingPlatformCallback(context)

        assertNotNull(result)
        assertTrue(result.error, "Verification should fail for empty certificate chain")
    }

    // =========== Certificate Parsing Helper Tests ===========

    @Test
    fun pemAndDerToCertificateChainShouldReturnEmptyForNullInputs() {
        val result = pemAndDerToCertificateChain(pemChain = null, derChain = null)

        assertNotNull(result)
        assertTrue(result.isEmpty(), "Should return empty list for null inputs")
    }

    @Test
    fun pemAndDerToCertificateChainShouldReturnEmptyForEmptyArrays() {
        val result = pemAndDerToCertificateChain(pemChain = emptyArray(), derChain = emptyArray())

        assertNotNull(result)
        assertTrue(result.isEmpty(), "Should return empty list for empty arrays")
    }

    // =========== X509VerificationRequest Tests ===========

    @Test
    fun x509VerificationRequestShouldHaveCorrectDefaults() {
        val request =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("cert"),
                trustedCerts = arrayOf("trusted"),
            )

        assertTrue(request.enabled)
        assertNotNull(request.verificationTime)
    }

    @Test
    fun x509VerificationRequestFromDtoShouldPreserveFields() {
        val original =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("pem-cert"),
                trustedCerts = arrayOf("trusted"),
                verificationProfile = X509VerificationProfile.ISO_18013_5,
            )

        val copy = X509VerificationRequest.fromDto(original)

        assertEquals(original.enabled, copy.enabled)
        assertEquals(original.chainPEM?.get(0), copy.chainPEM?.get(0))
        assertEquals(original.verificationProfile, copy.verificationProfile)
    }

    @Test
    fun x509VerificationRequestFromDtoShouldAllowOverridingEnabled() {
        val original =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("pem-cert"),
                trustedCerts = arrayOf("trusted"),
            )

        val copy = X509VerificationRequest.fromDto(original, enable = false)

        assertFalse(copy.enabled)
    }

    // =========== X509ValidationContext Extension Tests ===========

    @Test
    fun x509ValidationContextSuccessResultShouldNotHaveError() {
        val testRequest =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("test-pem"),
                trustedCerts = arrayOf("trusted-cert"),
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        val result = context.successResult()

        assertFalse(result.error)
        assertFalse(result.critical)
        assertEquals("Certificate chain validated", result.message)
    }

    @Test
    fun x509ValidationContextErrorResultShouldHaveError() {
        val testRequest =
            X509VerificationRequest(
                enabled = true,
                chainPEM = arrayOf("test-pem"),
                trustedCerts = arrayOf("trusted-cert"),
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        val result = context.errorResult("Test error", critical = true)

        assertTrue(result.error)
        assertTrue(result.critical)
        assertEquals("Test error", result.message)
    }

    @Test
    fun x509ValidationContextDisabledResultShouldNotHaveError() {
        val testRequest =
            X509VerificationRequest(
                enabled = false,
                chainPEM = arrayOf("test-pem"),
                trustedCerts = arrayOf("trusted-cert"),
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        val result = context.disabledResult()

        assertFalse(result.error)
        assertFalse(result.critical)
        assertTrue(result.message?.contains("disabled") == true)
    }

    // =========== X509VerifyServiceImpl Tests ===========

    @Test
    fun x509VerifyServiceImplShouldSetPlatformCallback() {
        val service = X509VerifyServiceImpl()
        val mockCallback = mockk<X509CoroutinesCallback>()

        val result = service.setPlatform(mockCallback)

        assertEquals(service, result, "setPlatform should return the service for chaining")
        assertTrue(service.hasPlatform(), "hasPlatform should return true after setting platform")
    }

    @Test
    fun x509VerifyServiceImplShouldDisableAndEnable() {
        val service = X509VerifyServiceImpl()

        assertTrue(service.isEnabled(), "Service should be enabled by default")

        service.disable()
        assertFalse(service.isEnabled(), "Service should be disabled after disable()")

        service.enable()
        assertTrue(service.isEnabled(), "Service should be enabled after enable()")
    }

    @Test
    fun x509VerifyServiceImplDisableShouldReturnServiceForChaining() {
        val service = X509VerifyServiceImpl()

        val result = service.disable()

        assertEquals(service, result, "disable() should return the service for chaining")
    }

    @Test
    fun x509VerifyServiceImplEnableShouldReturnServiceForChaining() {
        val service = X509VerifyServiceImpl()

        val result = service.enable()

        assertEquals(service, result, "enable() should return the service for chaining")
    }

    @Test
    fun x509VerifyServiceImplShouldSetTrustedCerts() {
        val service = X509VerifyServiceImpl()
        val certs = arrayOf("cert1", "cert2")

        val result = service.setTrustedCerts(certs)

        assertEquals(service, result, "setTrustedCerts should return the service for chaining")
        val trustedCerts = service.getTrustedCerts()
        assertNotNull(trustedCerts)
        assertEquals(2, trustedCerts.size)
        assertEquals("cert1", trustedCerts[0])
        assertEquals("cert2", trustedCerts[1])
    }

    @Test
    fun x509VerifyServiceImplShouldSetTrustedCertsToEmptyWhenNull() {
        val service = X509VerifyServiceImpl()
        // First set some certs
        service.setTrustedCerts(arrayOf("cert1"))

        // Then set to null
        service.setTrustedCerts(null)

        val trustedCerts = service.getTrustedCerts()
        assertNotNull(trustedCerts)
        assertTrue(trustedCerts.isEmpty(), "Trusted certs should be empty after setting null")
    }

    @Test
    fun x509VerifyServiceImplHasPlatformShouldReturnFalseWithNoCallbackAndNoDefault() {
        mockkObject(DefaultCallbacks)
        every { DefaultCallbacks.hasX509Default() } returns false

        val service = X509VerifyServiceImpl()

        assertFalse(service.hasPlatform(), "hasPlatform should return false when no callback and no default")
    }

    @Test
    fun x509VerifyServiceImplHasPlatformShouldReturnTrueWithDefaultCallback() {
        mockkObject(DefaultCallbacks)
        every { DefaultCallbacks.hasX509Default() } returns true

        val service = X509VerifyServiceImpl()

        assertTrue(service.hasPlatform(), "hasPlatform should return true when default callback exists")
    }

    @Test
    fun x509VerifyServiceImplPlatformShouldReturnSetCallback() {
        val service = X509VerifyServiceImpl()
        val mockCallback = mockk<X509CoroutinesCallback>()

        service.setPlatform(mockCallback)
        val result = service.platform()

        assertEquals(mockCallback, result, "platform() should return the set callback")
    }

    @Test
    fun x509VerifyServiceImplPlatformShouldUseSetCallbackOverDefault() {
        val service = X509VerifyServiceImpl()
        val mockCallback = mockk<X509CoroutinesCallback>()

        // Set the callback
        service.setPlatform(mockCallback)

        // Call platform() - should use the set callback, not fetch from DefaultCallbacks
        val result = service.platform()

        assertEquals(mockCallback, result, "platform() should use set callback instead of default")
    }

    @Test
    fun x509VerifyServiceImplVerifyCertificateChainShouldReturnResultWhenDisabledNoChainData() =
        runTest {
            val service = X509VerifyServiceImpl()
            service.disable()

            // When disabled and no chain data provided, the service should return early with disabled message
            val request =
                X509VerificationRequest(
                    enabled = true, // This will be overridden by the service's disabled state
                    chainPEM = null, // No chain data - tests "disabled with no data" path
                    chainDER = null,
                    trustedCerts = arrayOf("trusted-cert"),
                )

            val result = service.verifyCertificateChain(request)

            assertNotNull(result)
            assertFalse(result.error, "Disabled verification should not return error")
            assertTrue(result.message?.contains("disabled") == true, "Message should indicate verification is disabled")
            assertTrue(result.certificateChain.isEmpty(), "Certificate chain should be empty when no data provided")
        }

    @Test
    fun x509VerifyServiceImplVerifyCertificateChainShouldReturnParsedDataWhenDisabledWithChainData() =
        runTest {
            val service = X509VerifyServiceImpl()
            service.disable()

            // Valid test certificate chain
            val validPemChain =
                arrayOf(
                    """
-----BEGIN CERTIFICATE-----
MIID+zCCAeMCFGpZprAAGLBe7EwzJtczI10bzmO4MA0GCSqGSIb3DQEBCwUAMDkx
FTATBgNVBAMMDFRlc3QgUm9vdCBDQTETMBEGA1UECgwKTXkgQ29tcGFueTELMAkG
A1UEBhMCVVMwHhcNMjUwNDIyMTIwNzA1WhcNMjYwNDIyMTIwNzA1WjA7MRQwEgYD
VQQDDAt0ZXN0LWNsaWVudDEWMBQGA1UECgwNTXkgQ2xpZW50IE9yZzELMAkGA1UE
BhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDKH88PNb9/+pL1
FtuixCTHeXNIxU4iwU4F3GQsWGvM83ZPTyQsmne/0MBhKg55yhl93SgRLnlx6Gxt
ce8qBWEVkeJwzSgTG+0Nfyx57WBTz8WO9as9dsR0ee+Ay60dHv0xASEoxJwoyXdP
gHhAuIXHba7t1yhCnBrQBGxbCmvyTdRff9JmDa1wDrLNQ02JZBri1mOAV3oHxnjw
MlGDNyHGtqu1pkZ+NkJ8r1YxrtJMbkPNbn3W6WLxy4EcTuyz3geaWFA8+00U6OUl
+Nxdbr2jVRRJepiBGbBr40BKnTNnzDRX8euLppyqDru8TDRkUTKMhASTLrWAkNFv
lRxOuirpAgMBAAEwDQYJKoZIhvcNAQELBQADggIBAI2xqwCLx0i5dCYANJ9ScK0V
acWAk104jr3f+ymGYjbRnoH1n3+E9uPjLsSUZFcj0ohc6dkNsUzbL95/b6Zmrgt2
vFyxa13Kw0EzR0iPvRzOs2EZUPOiNT7dhoX8M6ulHCIH+qW7tviyq33QwxpKi2A2
7AQnaozhb9o06f5jhYNYF9CSlzSL5TibsM19GMtFMp3lwCHBy0TcYx5MFd9B9lDy
kB4Q/q9HoK6xdLb5j78UU7weDJjzATELE2nKuK7qSmM2b6pEl31t8VGhOCtBhd53
uYK4U2RVvBV/nYuQTeaDKi7Kz+Pr2z4oQA3RNG+3Yh4zdrWNMjhkXFsRoBTAlkCa
1jkNQp6vsXg5GafHPt9tZ6MbqO6PR52aXIPKEuWhY1+ry7jm5LLAhALRCpyvZrUl
/aUzbfe3RWFKE0+girYLIAavvmHSHstIKL1ZcF5E3zS6HO8otapbIjaok837MMmZ
3hZEoHqhmXRYpi6h+c9A3tr83rY8NSJCuPIp+Gt4j2sj+3Nc1voV8WEJ1Hkr30IQ
/akdPZw1DVcNCvbZHEvynw81c0Gl40ORaZJ1JD+NHv/oHYg4KFyYVcO/5Dg0+hgs
TOZ7uJOSWHiO7W3EOEVm4gaclmR0YYkTSFmGyifr0+WLzLr/k3pOX2uGAVlx88VE
4Vh9YF/MO1y4Gw4oIf0U
-----END CERTIFICATE-----
                    """.trimIndent(),
                )

            // When disabled but chain data IS provided, should parse chain and return parsed data with disabled status
            val request =
                X509VerificationRequest(
                    enabled = true, // This will be overridden by the service's disabled state
                    chainPEM = validPemChain,
                    chainDER = null,
                    trustedCerts = arrayOf("trusted-cert"),
                )

            val result = service.verifyCertificateChain(request)

            assertNotNull(result)
            assertFalse(result.error, "Disabled verification should not return error")
            assertTrue(result.message?.contains("disabled") == true, "Message should indicate verification is disabled")
            // When chain data is provided but disabled, the chain should be parsed and returned
            assertTrue(result.certificateChain.isNotEmpty(), "Certificate chain should be parsed and returned even when disabled")
            assertNotNull(result.publicKey, "Public key should be extracted from leaf certificate")
        }

    // =========== X509VerifyServiceJvmAdapter Default Trust Store Tests ===========

    @Test
    fun x509VerifyServiceJvmAdapterShouldUseDefaultTrustStoreWhenNoPemAnchors() {
        val adapter = X509VerifyServiceJvmAdapter()

        // Create a request with NO chainPEM and NO chainDER - this triggers the default trust store branch
        val testRequest =
            X509VerificationRequest(
                enabled = true,
                chainPEM = null,
                chainDER = null,
                trustedCerts = null,
                verificationProfile = X509VerificationProfile.RFC_5280,
                verificationTime = testVerificationTime,
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        // This should trigger the default trust store branch and then fail validation
        // (because our test certificate is not signed by any CA in the default trust store)
        val result = adapter.verifyCertificateChainUsingPlatformCallback(context)

        assertNotNull(result)
        // The verification will fail because our test cert is not in the default trust store
        assertTrue(result.error, "Verification should fail for certificate not in default trust store")
    }

    @Test
    fun x509VerifyServiceJvmAdapterShouldUseDefaultTrustStoreWhenEmptyPemAnchors() {
        val adapter = X509VerifyServiceJvmAdapter()

        // Create a request with empty chainPEM and empty chainDER
        val testRequest =
            X509VerificationRequest(
                enabled = true,
                chainPEM = emptyArray(),
                chainDER = emptyArray(),
                trustedCerts = emptyArray(),
                verificationProfile = X509VerificationProfile.RFC_5280,
                verificationTime = testVerificationTime,
            )

        val context =
            X509ValidationContext(
                request = testRequest,
                verificationAt = testVerificationTime,
                certificateChain = arrayOf(testCertificate),
                leafCertificate = testCertificate,
                publicKey = testJwk,
            )

        // This should trigger the default trust store branch
        val result = adapter.verifyCertificateChainUsingPlatformCallback(context)

        assertNotNull(result)
        // Will fail because our test cert isn't valid
        assertTrue(result.error, "Verification should fail for invalid test certificate")
    }

    // =========================================================================
    // Regression: trust anchor source selection
    // =========================================================================
    //
    // Earlier the JVM adapter built `TrustAnchor`s from `request.chainPEM` (the chain to
    // validate) instead of `request.trustedCerts`. That meant any chain whose root happened
    // to also be in the chain itself was accepted as trusted, while every legitimate use
    // (separate trust anchor configured out-of-band) failed with "Path does not chain with any
    // of the trust anchors". The HAIP wallet-attestation x5c flow tripped on this in
    // production. These tests pin down the correct selection so a future regression breaks the
    // build, not a live deployment.

    /** Demo CA used in the regression cases below — issuer CN=`IDK E2E CA`, valid 2026-04-29 → 2036-04-26. */
    private val regressionCaPem =
        """
        -----BEGIN CERTIFICATE-----
        MIIBtTCCAVygAwIBAgIUYHPHPaIhLbZjEG+87CQGaZRT1r0wCgYIKoZIzj0EAwIw
        JzElMCMGA1UEAwwcSURLIEUyRSBDQSwgTz1TcGhlcmVvbiwgQz1OTDAeFw0yNjA0
        MjkxNjI0NDRaFw0zNjA0MjYxNjI0NDRaMCcxJTAjBgNVBAMMHElESyBFMkUgQ0Es
        IE89U3BoZXJlb24sIEM9TkwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ/QuTk
        dimVLElzTHWWRizMyXFP5cxzM+yh4cev69MUXxgdbKxKOrj+MiyibEY2KofqAN3K
        hD9MtijUxE80AZeho2YwZDAdBgNVHQ4EFgQU4yGaoD/fqNjRbtD+oLYLU25xeq4w
        HwYDVR0jBBgwFoAU4yGaoD/fqNjRbtD+oLYLU25xeq4wEgYDVR0TAQH/BAgwBgEB
        /wIBADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgRnEcATheGu7k
        S9202u8Pw72876+HollpN2soD/kvd9ACICUz0HMgd7/K1/reEK0D4wxQdLvG2pBM
        QbJEgC/RKNkP
        -----END CERTIFICATE-----
        """.trimIndent()

    /** Leaf issued by `regressionCaPem` — subject CN=`IDK E2E Wallet Attester`, valid through 2036-04-26. */
    private val regressionLeafPem =
        """
        -----BEGIN CERTIFICATE-----
        MIIBvDCCAWOgAwIBAgIUBLQhxPwu6uo4EloFsWwkv19KwewwCgYIKoZIzj0EAwIw
        JzElMCMGA1UEAwwcSURLIEUyRSBDQSwgTz1TcGhlcmVvbiwgQz1OTDAeFw0yNjA0
        MjkyMTM5NTNaFw0zNjA0MjYyMTM5NTNaMDQxMjAwBgNVBAMMKUlESyBFMkUgV2Fs
        bGV0IEF0dGVzdGVyLCBPPVNwaGVyZW9uLCBDPU5MMFkwEwYHKoZIzj0CAQYIKoZI
        zj0DAQcDQgAEr8lJWAbfbePddc4RkXpCG0+bcCwegwW7e+TnqobDnk1FeznMAu4f
        5TGhTUKZ7SPkvECSP+wOEEIHcbCsE71d/KNgMF4wDAYDVR0TAQH/BAIwADAOBgNV
        HQ8BAf8EBAMCB4AwHQYDVR0OBBYEFPpcMwPNsui3rXwQO6p3mFTty0q5MB8GA1Ud
        IwQYMBaAFOMhmqA/36jY0W7Q/qC2C1NucXquMAoGCCqGSM49BAMCA0cAMEQCIDGE
        b0g2Qzl9pK7f6B8p+sLQYBc7EIH45zcdoBLG6pTwAiAsD5Y+IzGw+EniC+YrnjQG
        vRwjDQ11qU+rgJ9kCz2Fng==
        -----END CERTIFICATE-----
        """.trimIndent()

    /** Unrelated CA that the leaf above does NOT chain to — used for the negative test. */
    private val unrelatedCaPem =
        """
        -----BEGIN CERTIFICATE-----
        MIIBtTCCAVygAwIBAgIUYHPHPaIhLbZjEG+87CQGaZRT1r1wCgYIKoZIzj0EAwIw
        JzElMCMGA1UEAwwcWFlaIE90aGVyIENBLCBPPU90aGVyLCBDPVVTMB4XDTI2MDQy
        OTE2MjQ0NFoXDTM2MDQyNjE2MjQ0NFowJzElMCMGA1UEAwwcWFlaIE90aGVyIENB
        LCBPPU90aGVyLCBDPVVTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/zP/D////
        nl0ssrjeg+E1FxA1J6m5nSU2+xLZ+G4ZeGI8oxSSPhVB1jR3lcq0/oIGrdBDt2Z3
        a3aFPS6QxXXa56NmMGQwHQYDVR0OBBYEFP////////////////////////////8w
        HwYDVR0jBBgwFoAU/////////////////////////////zASBgNVHRMBAf8ECDAG
        AQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAKBggqhkjOPQQDAgNHADBEAiAA////////
        ////////////////////////////////////AiAA////////////////////
        -----END CERTIFICATE-----
        """.trimIndent()

    @Test
    fun verifyCertificateChainShouldAcceptChainAnchoredAtConfiguredTrustedCert() =
        runTest {
            val service = X509VerifyServiceImpl()
            val request =
                X509VerificationRequest(
                    enabled = true,
                    chainPEM = arrayOf(regressionLeafPem),
                    trustedCerts = arrayOf(regressionCaPem),
                    verificationProfile = X509VerificationProfile.RFC_5280,
                    verificationTime = LocalDateTimeKMP(year = 2026, month = 12, day = 1, hour = 0, minute = 0),
                )

            val result = service.verifyCertificateChain(request)

            assertNotNull(result)
            assertFalse(
                result.error,
                "Leaf signed by trustedCerts[0] must validate; got error=${result.error} message=${result.message}",
            )
        }

    @Test
    fun verifyCertificateChainShouldRejectWhenTrustedCertsContainsUnrelatedAnchor() =
        runTest {
            // Regression: the previous JVM impl read trust anchors from `chainPEM` instead of
            // `trustedCerts`. With chainPEM=[leaf] and trustedCerts=[unrelatedCA], the buggy
            // code would have used [leaf] as the anchor and "validated" the chain against
            // itself. A correct impl reads trustedCerts and rejects the chain because the
            // leaf's issuer is not the unrelated CA.
            val service = X509VerifyServiceImpl()
            val request =
                X509VerificationRequest(
                    enabled = true,
                    chainPEM = arrayOf(regressionLeafPem),
                    trustedCerts = arrayOf(unrelatedCaPem),
                    verificationProfile = X509VerificationProfile.RFC_5280,
                    verificationTime = LocalDateTimeKMP(year = 2026, month = 12, day = 1, hour = 0, minute = 0),
                )

            val result = service.verifyCertificateChain(request)

            assertNotNull(result)
            assertTrue(
                result.error,
                "Leaf NOT signed by any trustedCerts entry must be rejected; instead got error=${result.error}",
            )
        }
}
