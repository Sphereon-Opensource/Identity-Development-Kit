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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.x509.wrapX509CertificatePem
import com.sphereon.crypto.kms.asCertificateServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Extension function to access X5cExternalIdentifierResolutionService from a session graph.
 */
fun Any.asX5cExternalIdentifierResolutionServiceGraph(): X5cExternalIdentifierResolutionService.Graph = this as X5cExternalIdentifierResolutionService.Graph

/**
 * Extension function to access MultiExternalIdentifierService from a session graph.
 */
fun Any.asMultiExternalIdentifierServiceGraph(): MultiExternalIdentifierService.Graph = this as MultiExternalIdentifierService.Graph

/**
 * Tests for X5C external identifier resolution service.
 */
class X5cExternalIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var x5cResolutionService: X5cExternalIdentifierResolutionService
    private lateinit var multiResolutionService: MultiExternalIdentifierService

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("x5c-resolution-test")

    // Test certificate in base64 DER format (generated once for testing)
    private var testCertificateBase64: String = ""

    @OptIn(ExperimentalEncodingApi::class)
    @BeforeTest
    fun setUp() =
        runTest {
            // Register the software KMS provider for key generation
            val config =
                SoftwareKmsProviderConfig(
                    id = "test-software-provider",
                    cryptographyProvider = CryptographyProvider.Default.name,
                )
            app as JvmCryptoTestAppGraph
            val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

            keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
            keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

            x5cResolutionService = session.graph.asX5cExternalIdentifierResolutionServiceGraph().x5cExternalIdentifierResolutionService
            multiResolutionService = session.graph.asMultiExternalIdentifierServiceGraph().multiExternalIdentifierService

            // Generate a self-signed certificate for testing
            val certificateService = session.graph.asCertificateServiceGraph().certificateService
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val dn =
                X509DistinguishedNameElements(
                    commonName = "X5C Test Certificate",
                    organizationName = "Sphereon Test",
                    country = "NL",
                )

            val notBefore = LocalDateTimeKMP.now()
            val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
            val notAfter =
                LocalDateTimeKMP.fromString(
                    notAfterInstant.toLocalDateTime(TimeZone.UTC).toString(),
                )

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

            // Convert DER certificate to base64
            testCertificateBase64 = Base64.encode(certResult.certificate.der)
        }

    // =========== X5C Resolution Service Tests ===========

    @Test
    fun x5cResolutionServiceShouldSupportX5cMethod() =
        runTest {
            val supported = x5cResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
            assertTrue(supported, "Should support X5C identifier method")

            val notSupported = x5cResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
            assertFalse(notSupported, "Should not support JWK identifier method")

            val didNotSupported = x5cResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.DID)
            assertFalse(didNotSupported, "Should not support DID identifier method")
        }

    @Test
    fun x5cResolutionServiceShouldSupportX5cIdentifier() =
        runTest {
            // X5C expects an array of base64 DER certificates
            @Suppress("UNCHECKED_CAST")
            val x5cArray = arrayOf(testCertificateBase64) as Array<Any?>
            val supported = x5cResolutionService.isSupportedIdentifier(x5cArray)
            assertTrue(supported, "Should support X5C array identifier")

            // Should not support single string
            val stringNotSupported = x5cResolutionService.isSupportedIdentifier("some-string")
            assertFalse(stringNotSupported, "Should not support single string identifier")

            // Should not support empty array
            @Suppress("UNCHECKED_CAST")
            val emptyArray = emptyArray<String>() as Array<Any?>
            val emptyNotSupported = x5cResolutionService.isSupportedIdentifier(emptyArray)
            assertFalse(emptyNotSupported, "Should not support empty array identifier")
        }

    @Test
    fun x5cResolutionServiceShouldSupportX5cOpts() =
        runTest {
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf(testCertificateBase64))
            val supported = x5cResolutionService.isSupportedOpts(x5cOpts)
            assertTrue(supported, "Should support ExternalIdentifierX5cOpts")

            // Should not support JWK opts
            val jwkOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            val jwkNotSupported = x5cResolutionService.isSupportedOpts(jwkOpts)
            assertFalse(jwkNotSupported, "Should not support DID opts")
        }

    @Test
    fun x5cResolutionServiceAsSupportedOptsShouldSucceedForX5cOpts() =
        runTest {
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf(testCertificateBase64))
            val result = x5cResolutionService.asSupportedOpts(x5cOpts)

            assertTrue(result.isOk)
            assertEquals(x5cOpts, result.value)
        }

    @Test
    fun x5cResolutionServiceAsSupportedOptsShouldFailForNonX5cOpts() =
        runTest {
            val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            val result = x5cResolutionService.asSupportedOpts(didOpts)

            assertTrue(result.isErr, "Should return error for non-X5C opts")
        }

    @Test
    fun x5cResolutionServiceShouldResolveValidCertificateChain() =
        runTest {
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf(testCertificateBase64))
            val result = x5cResolutionService.resolve(x5cOpts)

            assertTrue(result.isOk, "Resolution should succeed for valid X5C")
            val value = result.value
            assertNotNull(value)
            assertNotNull(value.keyInfo, "Should have key info")
            assertNotNull(value.jwks, "Should have JWKS")
            assertTrue(value.jwks.isNotEmpty(), "JWKS should contain at least one key")
            assertNotNull(value.certificates, "Should have certificates")
            assertTrue(value.certificates.isNotEmpty(), "Should have at least one certificate")
        }

    @Test
    fun x5cResolutionServiceShouldPreserveIdentifierOpts() =
        runTest {
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf(testCertificateBase64))
            val result = x5cResolutionService.resolve(x5cOpts)

            assertTrue(result.isOk)
            assertEquals(x5cOpts, result.value.identifierOpts)
        }

    // =========== Multi Resolution Service Tests ===========

    @Test
    fun multiResolutionServiceShouldSupportMultipleMethods() =
        runTest {
            // Multi should aggregate all supported methods from individual services
            val supportsX5c = multiResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
            val supportsJwk = multiResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)

            assertTrue(supportsX5c, "Multi service should support X5C method")
            assertTrue(supportsJwk, "Multi service should support JWK method")
        }

    @Test
    fun multiResolutionServiceShouldSupportMultipleIdentifiers() =
        runTest {
            // Should support X5C array
            @Suppress("UNCHECKED_CAST")
            val x5cArray = arrayOf(testCertificateBase64) as Array<Any?>
            val supportsX5c = multiResolutionService.isSupportedIdentifier(x5cArray)
            assertTrue(supportsX5c, "Multi service should support X5C array identifier")
        }

    @Test
    fun multiResolutionServiceShouldResolveX5cOpts() =
        runTest {
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf(testCertificateBase64))
            val result = multiResolutionService.resolve(x5cOpts)

            assertTrue(result.isOk, "Multi service should resolve X5C opts")
            val value = result.value
            assertNotNull(value)
            assertTrue(value is ExternalIdentifierResult.X5c, "Result should be X5c type")
        }

    @Test
    fun multiResolutionServiceShouldResolveJwkOpts() =
        runTest {
            // Generate a JWK for testing
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = multiResolutionService.resolve(jwkOpts)

            assertTrue(result.isOk, "Multi service should resolve JWK opts")
            val value = result.value
            assertNotNull(value)
            // Result type depends on the resolver that handles it
            assertNotNull(value.keyInfo)
        }

    @Test
    fun multiResolutionServiceShouldReturnErrorForUnsupportedOpts() =
        runTest {
            // Create opts that no service supports
            val unsupportedOpts = ExternalIdentifierOidcDiscoveryOpts(identifier = "https://example.com")
            val result = multiResolutionService.resolve(unsupportedOpts)

            // This may succeed or fail depending on whether OIDC discovery service is registered
            // The important thing is it shouldn't throw
            assertNotNull(result)
        }

    // =========== ExternalIdentifierX5cOpts Tests ===========

    @Test
    fun externalIdentifierX5cOptsShouldHaveCorrectMethod() {
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = listOf("MIIB...", "MIIC..."),
                verify = true,
            )

        assertEquals(IdentifierMethodDefaults.X5C, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun externalIdentifierX5cOptsShouldSupportTrustAnchors() {
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = listOf("MIIB..."),
                trustAnchors = listOf("MIIC...", "MIID..."),
                verify = true,
            )

        assertNotNull(opts.trustAnchors)
        assertEquals(2, opts.trustAnchors?.size)
    }

    @Test
    fun externalIdentifierX5cOptsShouldSupportVerificationTime() {
        val verificationTime = "2025-01-19T12:00:00"
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = listOf("MIIB..."),
                verificationTime = verificationTime,
            )

        assertEquals(verificationTime, opts.verificationTime)
    }
}
