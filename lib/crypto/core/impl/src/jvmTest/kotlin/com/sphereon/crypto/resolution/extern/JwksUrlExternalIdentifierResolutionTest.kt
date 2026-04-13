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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.JvmCryptoTestAppComponent
import com.sphereon.crypto.core.createJvmCryptoTestAppComponent
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Extension function to access JwksUrlExternalIdentifierResolutionService from a session component.
 */
fun Any.asJwksUrlExternalIdentifierResolutionServiceComponent(): JwksUrlExternalIdentifierResolutionServiceImpl.Component =
    this as JwksUrlExternalIdentifierResolutionServiceImpl.Component

/**
 * Tests for JwksUrlExternalIdentifierResolutionService.
 *
 * Note: Tests that require actual HTTP calls are limited as they depend on network availability.
 * This test file focuses on method support, identifier validation, and error handling.
 */
class JwksUrlExternalIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwksUrlService: JwksUrlExternalIdentifierResolutionService

    val app = createJvmCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jwks-url-test")

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(
            id = "test-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as JvmCryptoTestAppComponent
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwksUrlService = session.component.asJwksUrlExternalIdentifierResolutionServiceComponent().jwksUrlExternalIdentifierResolutionService
    }

    // =========== Method Support Tests ===========

    @Test
    fun jwksUrlServiceShouldSupportJwksUrlMethod() = runTest {
        val supported = jwksUrlService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWKS_URL)
        assertTrue(supported, "Should support JWKS_URL method")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportJwkMethod() = runTest {
        val supported = jwksUrlService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
        assertFalse(supported, "Should not support JWK method")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportDidMethod() = runTest {
        val supported = jwksUrlService.isSupportedIdentifierMethod(IdentifierMethodDefaults.DID)
        assertFalse(supported, "Should not support DID method")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportX5cMethod() = runTest {
        val supported = jwksUrlService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
        assertFalse(supported, "Should not support X5C method")
    }

    // =========== Identifier Support Tests ===========

    @Test
    fun jwksUrlServiceShouldSupportHttpsUrl() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("https://example.com/.well-known/jwks.json")
        assertTrue(supported, "Should support HTTPS URL")
    }

    @Test
    fun jwksUrlServiceShouldSupportHttpUrl() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("http://localhost:8080/jwks")
        assertTrue(supported, "Should support HTTP URL")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportNonUrlString() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("some-string")
        assertFalse(supported, "Should not support non-URL string")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportDidUrl() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("did:key:z6Mktest")
        assertFalse(supported, "Should not support DID URL")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportNonStringIdentifier() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier(12345)
        assertFalse(supported, "Should not support non-string identifier")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportEmptyString() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("")
        assertFalse(supported, "Should not support empty string")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportMalformedUrl() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("not-a-valid-url")
        assertFalse(supported, "Should not support malformed URL")
    }

    // =========== Opts Support Tests ===========

    @Test
    fun jwksUrlServiceShouldSupportJwksUrlOpts() = runTest {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
        val supported = jwksUrlService.isSupportedOpts(opts)
        assertTrue(supported, "Should support ExternalIdentifierJwksUrlOpts")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportJwkOpts() = runTest {
        val jwkOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
        val supported = jwksUrlService.isSupportedOpts(jwkOpts)
        assertFalse(supported, "Should not support DID opts")
    }

    @Test
    fun jwksUrlServiceShouldNotSupportX5cOpts() = runTest {
        val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."))
        val supported = jwksUrlService.isSupportedOpts(x5cOpts)
        assertFalse(supported, "Should not support X5C opts")
    }

    // =========== asSupportedOpts Tests ===========

    @Test
    fun jwksUrlServiceAsSupportedOptsShouldSucceedForJwksUrlOpts() = runTest {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
        val result = jwksUrlService.asSupportedOpts(opts)
        assertTrue(result.isOk, "asSupportedOpts should succeed for JWKS URL opts")
        assertEquals(opts, result.value)
    }

    @Test
    fun jwksUrlServiceAsSupportedOptsShouldFailForNonJwksUrlOpts() = runTest {
        val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
        val result = jwksUrlService.asSupportedOpts(didOpts)
        assertTrue(result.isErr, "asSupportedOpts should fail for non-JWKS-URL opts")
    }

    // =========== Opts Method Tests ===========

    @Test
    fun externalIdentifierJwksUrlOptsShouldHaveCorrectMethod() {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
        assertEquals(IdentifierMethodDefaults.JWKS_URL, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun externalIdentifierJwksUrlOptsShouldSupportLookupWithKid() {
        val lookup = AdditionalIdentifierLookup(kid = "my-key-id")
        val opts = ExternalIdentifierJwksUrlOpts(
            identifier = "https://example.com/jwks",
            lookup = lookup
        )
        assertEquals("my-key-id", opts.lookup.kid)
    }

    // =========== Error Handling Tests ===========

    @Test
    fun jwksUrlServiceResolveShouldFailForBlankUrl() = runTest {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "   ")
        val result = jwksUrlService.resolve(opts)
        assertTrue(result.isErr, "Should fail for blank URL")
        // Note: Blank URLs fail at the supports() check because they don't contain "://"
        // so the error may be about unsupported identifier rather than "blank"
        assertNotNull(result.error, "Should have an error for blank URL")
    }

    @Test
    fun jwksUrlServiceResolveShouldFailForEmptyUrl() = runTest {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "")
        // Note: Empty string doesn't pass isSupportedIdentifier, so it will fail with a different error
        val result = jwksUrlService.resolve(opts)
        assertTrue(result.isErr, "Should fail for empty URL")
    }

    @Test
    fun jwksUrlServiceResolveShouldFailForUnsupportedOpts() = runTest {
        val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
        val result = jwksUrlService.resolve(didOpts)
        assertTrue(result.isErr, "Should fail for unsupported opts")
    }

    // =========== URL Validation Edge Cases ===========

    @Test
    fun jwksUrlServiceShouldSupportHttpsWithPort() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("https://example.com:8443/.well-known/jwks.json")
        assertTrue(supported, "Should support HTTPS URL with port")
    }

    @Test
    fun jwksUrlServiceShouldSupportHttpsWithPath() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("https://example.com/api/v1/.well-known/jwks.json")
        assertTrue(supported, "Should support HTTPS URL with path")
    }

    @Test
    fun jwksUrlServiceShouldSupportHttpsWithQuery() = runTest {
        val supported = jwksUrlService.isSupportedIdentifier("https://example.com/jwks?format=json")
        assertTrue(supported, "Should support HTTPS URL with query params")
    }

    @Test
    fun jwksUrlServiceShouldBeCaseInsensitiveForProtocol() = runTest {
        val httpsLower = jwksUrlService.isSupportedIdentifier("https://example.com/jwks")
        val httpsUpper = jwksUrlService.isSupportedIdentifier("HTTPS://example.com/jwks")
        val httpsMixed = jwksUrlService.isSupportedIdentifier("Https://example.com/jwks")

        assertTrue(httpsLower, "Should support lowercase https")
        assertTrue(httpsUpper, "Should support uppercase HTTPS")
        assertTrue(httpsMixed, "Should support mixed case Https")
    }
}
