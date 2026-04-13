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

package com.sphereon.crypto.resolution.managed

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionScope
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Extension function to access KeyInfoIdentifierResolutionService from a session component.
 */
fun Any.asKeyInfoIdentifierResolutionServiceComponent(): KeyInfoIdentifierResolutionServiceImpl.Component =
    this as KeyInfoIdentifierResolutionServiceImpl.Component

/**
 * Extension function to access MultiManagedIdentifierService from a session component.
 */
fun Any.asMultiManagedIdentifierServiceComponent(): MultiManagedIdentifierResolutionServiceImpl.Component =
    this as MultiManagedIdentifierResolutionServiceImpl.Component

/**
 * Tests for managed identifier resolution services.
 */
class ManagedIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var keyInfoResolutionService: KeyInfoIdentifierResolutionService
    private lateinit var multiManagedService: MultiManagedIdentifierService

    val app = createCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("managed-resolution-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider for key generation
        val config = SoftwareKmsProviderConfig(
            id = "test-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        keyInfoResolutionService = session.component.asKeyInfoIdentifierResolutionServiceComponent().keyInfoIdentifierResolutionService
        multiManagedService = session.component.asMultiManagedIdentifierServiceComponent().multiManagedIdentifierService
    }

    // =========== KeyInfoIdentifierResolutionService Tests ===========

    @Test
    fun keyInfoResolutionServiceShouldSupportKeyMethod() = runTest {
        val supportsKey = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY)
        assertTrue(supportsKey, "Should support KEY identifier method")

        val supportsAlias = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY_ALIAS)
        assertTrue(supportsAlias, "Should support KEY_ALIAS identifier method")

        val supportsKid = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KID)
        assertTrue(supportsKid, "Should support KID identifier method")

        val supportsJwk = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
        assertTrue(supportsJwk, "Should support JWK identifier method")

        val supportsCoseKey = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.COSE_KEY)
        assertTrue(supportsCoseKey, "Should support COSE_KEY identifier method")
    }

    @Test
    fun keyInfoResolutionServiceShouldNotSupportDidMethod() = runTest {
        val supportsDid = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.DID)
        assertFalse(supportsDid, "Should not support DID identifier method")

        val supportsX5c = keyInfoResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
        assertFalse(supportsX5c, "Should not support X5C identifier method")
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportKeyInfoTypeIdentifier() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val supported = keyInfoResolutionService.isSupportedIdentifier(keyInfo)
        assertTrue(supported, "Should support KeyInfoType identifier")
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportKeyTypeIdentifier() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val key = keyPair.jose.publicJwk!!

        val supported = keyInfoResolutionService.isSupportedIdentifier(key)
        assertTrue(supported, "Should support KeyType identifier")
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportKidString() = runTest {
        // Any string that doesn't start with "did:" or "http" is considered a valid KID
        val supported = keyInfoResolutionService.isSupportedIdentifier("random-string")
        assertTrue(supported, "Should support string as KID identifier")
    }

    @Test
    fun keyInfoResolutionServiceShouldNotSupportDidString() = runTest {
        // KeyInfoIdentifierResolutionService delegates DID resolution to a DID-specific service
        val supported = keyInfoResolutionService.isSupportedIdentifier("did:key:z6Mktest")
        assertFalse(supported, "Should not support DID string identifier - handled by DID resolver")
    }

    @Test
    fun keyInfoResolutionServiceShouldNotSupportHttpUrl() = runTest {
        // HTTP URLs (like JWKS endpoints) are external identifiers, not managed keys
        val supported = keyInfoResolutionService.isSupportedIdentifier("https://example.com/.well-known/jwks.json")
        assertFalse(supported, "Should not support HTTP URL identifier")
    }

    @Test
    fun keyInfoResolutionServiceShouldResolveStoredKey() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "test-resolve-key",
            certChain = null
        )

        val opts = ManagedOptsKeyInfo(
            identifier = storedKey,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.resolve(opts)
        assertTrue(result.isOk, "Resolution should succeed for stored key")
        assertNotNull(result.value.keyInfo)
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportManagedOptsKeyInfo() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val opts = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext()
        )

        val supported = keyInfoResolutionService.isSupportedOpts(opts)
        assertTrue(supported, "Should support ManagedOptsKeyInfo")
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsAlias() = runTest {
        val opts = ManagedOptsAlias(
            identifier = "test-alias",
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsAlias")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsKid() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val opts = ManagedOptsKid(
            identifier = "test-kid",
            context = IdentifierContext(),
            lookup = keyInfo
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    // Note: ManagedOptsJwk test removed - requires JwkDTOType which is not easily constructable
    // The ManagedOptsKey test covers similar functionality

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsKey() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val key = keyPair.jose.publicJwk!!

        val opts = ManagedOptsKey(
            identifier = key,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKey")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    // =========== MultiManagedIdentifierService Tests ===========

    @Test
    fun multiManagedServiceShouldSupportMultipleMethods() = runTest {
        val supportsKey = multiManagedService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY)
        assertTrue(supportsKey, "Multi service should support KEY method")

        val supportsAlias = multiManagedService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY_ALIAS)
        assertTrue(supportsAlias, "Multi service should support KEY_ALIAS method")

        val supportsKid = multiManagedService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KID)
        assertTrue(supportsKid, "Multi service should support KID method")
    }

    @Test
    fun multiManagedServiceShouldSupportKeyTypeIdentifier() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val key = keyPair.jose.publicJwk!!

        val supported = multiManagedService.isSupportedIdentifier(key)
        assertTrue(supported, "Multi service should support KeyType identifier")
    }

    @Test
    fun multiManagedServiceShouldResolveManagedOptsKeyInfo() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "multi-resolve-key",
            certChain = null
        )

        val opts = ManagedOptsKeyInfo(
            identifier = storedKey,
            context = IdentifierContext()
        )

        val result = multiManagedService.resolve(opts)
        assertTrue(result.isOk, "Multi service should resolve ManagedOptsKeyInfo")
        assertNotNull(result.value.keyInfo)
    }

    @Test
    fun multiManagedServiceShouldSupportManagedOptsKeyInfo() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val opts = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext()
        )

        val supported = multiManagedService.isSupportedOpts(opts)
        assertTrue(supported, "Multi service should support ManagedOptsKeyInfo")
    }

    @Test
    fun multiManagedServiceAsSupportedOptsShouldSucceedForSupportedOpts() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val opts = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext()
        )

        val result = multiManagedService.asSupportedOpts(opts)
        assertTrue(result.isOk, "asSupportedOpts should succeed for supported opts")
    }

    // =========== ManagedOpts Tests ===========

    @Test
    fun managedOptsKeyInfoShouldHaveCorrectMethod() {
        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")
        val opts = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext()
        )

        assertEquals(IdentifierMethodDefaults.KEY, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun managedOptsAliasShouldHaveCorrectMethod() {
        val opts = ManagedOptsAlias(
            identifier = "test-alias",
            context = IdentifierContext()
        )

        assertEquals(IdentifierMethodDefaults.KEY_ALIAS, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun managedOptsKidShouldHaveCorrectMethod() {
        val keyInfo = KeyInfo<KeyType>(kid = "test-kid")
        val opts = ManagedOptsKid(
            identifier = "test-kid",
            context = IdentifierContext(),
            lookup = keyInfo
        )

        assertEquals(IdentifierMethodDefaults.KID, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun managedIdentifierKeyResultShouldBeResolved() = runTest {
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val result = ManagedIdentifierKeyResult(
            keyInfo = keyInfo,
            context = IdentifierContext(),
            identifier = keyInfo.key
        )

        assertTrue(result.isResolved)
        assertEquals(IdentifierMethodDefaults.KEY, result.method)
        assertNotNull(result.identifier)
    }

    // =========== Algorithm Hint Override Tests ===========

    @Test
    fun keyInfoResolutionServiceShouldOverrideAlgorithmFromHint() = runTest {
        // Generate and store a key without specifying an algorithm
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null,
            signatureAlgorithm = null // No algorithm set on the stored key
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "alg-hint-test-key",
            certChain = null
        )

        // Create opts with an algorithm hint (as would come from JWT header)
        val identifierWithHint = KeyInfo<KeyType>(
            alias = "alg-hint-test-key",
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384 // Different algorithm as hint
        )

        val opts = ManagedOptsKeyInfo(
            identifier = identifierWithHint,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.resolve(opts)
        assertTrue(result.isOk, "Resolution should succeed")
        assertNotNull(result.value.keyInfo)

        // The result should have the hinted algorithm, not the stored one
        assertEquals(
            SignatureAlgorithm.ECDSA_SHA384,
            result.value.keyInfo.signatureAlgorithm,
            "Should use algorithm hint from identifier"
        )
    }

    @Test
    fun keyInfoResolutionServiceShouldNotOverrideWhenAlgorithmsMatch() = runTest {
        // Generate and store a key with a specific algorithm
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 // Algorithm set on stored key
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "alg-match-test-key",
            certChain = null
        )

        // Create opts with the same algorithm hint
        val identifierWithHint = KeyInfo<KeyType>(
            alias = "alg-match-test-key",
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 // Same algorithm
        )

        val opts = ManagedOptsKeyInfo(
            identifier = identifierWithHint,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.resolve(opts)
        assertTrue(result.isOk, "Resolution should succeed")
        assertNotNull(result.value.keyInfo)

        // The result should keep the original algorithm
        assertEquals(
            SignatureAlgorithm.ECDSA_SHA256,
            result.value.keyInfo.signatureAlgorithm,
            "Should keep original algorithm when they match"
        )
    }

    @Test
    fun keyInfoResolutionServiceShouldKeepStoredAlgorithmWhenNoHint() = runTest {
        // Generate and store a key with a specific algorithm
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 // Algorithm set on stored key
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "no-hint-test-key",
            certChain = null
        )

        // Create opts without an algorithm hint
        val identifierWithoutHint = KeyInfo<KeyType>(
            alias = "no-hint-test-key",
            signatureAlgorithm = null // No hint
        )

        val opts = ManagedOptsKeyInfo(
            identifier = identifierWithoutHint,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.resolve(opts)
        assertTrue(result.isOk, "Resolution should succeed")
        assertNotNull(result.value.keyInfo)

        // The result should keep the stored algorithm
        assertEquals(
            SignatureAlgorithm.ECDSA_SHA256,
            result.value.keyInfo.signatureAlgorithm,
            "Should keep stored algorithm when no hint provided"
        )
    }

    @Test
    fun keyInfoResolutionServiceShouldResolveKeyByKid() = runTest {
        // Generate and store a key with a specific KID
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = null,
            providerId = null,
            kid = "test-kid-for-resolution"
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "kid-resolution-test-key",
            certChain = null
        )

        // Resolve using KID
        val opts = ManagedOptsKid(
            identifier = "test-kid-for-resolution",
            context = IdentifierContext(),
            lookup = KeyInfo<KeyType>(kid = "test-kid-for-resolution")
        )

        val result = keyInfoResolutionService.resolve(opts)
        assertTrue(result.isOk, "Resolution by KID should succeed")
        assertNotNull(result.value.keyInfo)
    }

    // =========== Additional Branch Coverage Tests ===========

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsKidWhenLookupHasNoKid() = runTest {
        // Test the branch where lookupKeyInfo.kid is null
        val opts = ManagedOptsKid(
            identifier = "new-kid-from-opts",
            context = IdentifierContext(),
            lookup = KeyInfo<KeyType>(alias = "some-alias") // No kid in lookup
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid even when lookup has no kid")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsKidWithResolvedKeyInfoLookup() = runTest {
        // Test the branch where lookupKeyInfo is ResolvedKeyInfo with no kid
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfoWithoutKid = ResolvedKeyInfo(
            key = keyPair.jose.publicJwk!!,
            alias = "test-alias-no-kid",
            providerId = null,
            kid = null  // No kid in ResolvedKeyInfo
        )

        val opts = ManagedOptsKid(
            identifier = "provided-kid",
            context = IdentifierContext(),
            lookup = resolvedKeyInfoWithoutKid
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid with ResolvedKeyInfo lookup")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportKeyAliasString() = runTest {
        // Test the key alias identifier branch - strings that look like key aliases
        // In IdentifierTypeUtils, key aliases are identified by specific patterns
        val supported = keyInfoResolutionService.isSupportedIdentifier("simple-key-alias")
        assertTrue(supported, "Should support simple string as key alias identifier")
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedIdentifierResult() = runTest {
        // Test converting a ManagedIdentifierResult back to opts
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val originalResult = ManagedIdentifierKeyResult(
            keyInfo = keyInfo,
            context = IdentifierContext(),
            identifier = keyInfo.key
        )

        val result = keyInfoResolutionService.asSupportedOpts(originalResult)
        assertTrue(result.isOk, "Should convert ManagedIdentifierResult to ManagedOptsKeyInfo")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsKidWithManagedKeyInfoLookup() = runTest {
        // Test the 'else' branch in asSupportedOpts when lookup is ManagedKeyInfo (neither ResolvedKeyInfo nor KeyInfo)
        // ManagedKeyInfo delegates to ResolvedKeyInfoType but is not an instance of ResolvedKeyInfo
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyPair.jose.privateJwk!!,
            alias = "managed-lookup-test-alias",
            providerId = "test-software-provider",
            kid = null  // No kid in managed key info to trigger the branch
        )

        val storedKey = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "test-software-provider",
            alias = "managed-lookup-test-alias",
            certChain = null
        )

        // Use the stored ManagedKeyInfo as the lookup
        val opts = ManagedOptsKid(
            identifier = "new-kid-to-set",
            context = IdentifierContext(),
            lookup = storedKey  // ManagedKeyInfo with no kid
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid with ManagedKeyInfo lookup")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceShouldNotSupportUnsupportedIdentifierTypes() = runTest {
        // Test isSupportedIdentifier returns false for unsupported types
        val unsupportedInt = 12345
        val supported = keyInfoResolutionService.isSupportedIdentifier(unsupportedInt)
        assertFalse(supported, "Should not support Int identifiers")

        val unsupportedList = listOf("a", "b", "c")
        val supportedList = keyInfoResolutionService.isSupportedIdentifier(unsupportedList)
        assertFalse(supportedList, "Should not support List identifiers")
    }

    @Test
    fun keyInfoResolutionServiceShouldSupportStringIdentifiers() = runTest {
        // Test isSupportedIdentifier with various string types
        // A simple string without special prefixes should be supported as a kid/alias
        val simpleString = "some-key-id"
        val supported = keyInfoResolutionService.isSupportedIdentifier(simpleString)
        assertTrue(supported, "Simple string should be supported as kid/alias identifier")
    }

    @Test
    fun keyInfoResolutionServiceIsSupportedOptsShouldReturnFalseForUnsupportedOpts() = runTest {
        // Test isSupportedOpts with DID opts (should not be supported by KeyInfoIdentifierResolutionService)
        val didOpts = ManagedOptsDid(
            identifier = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            context = IdentifierContext()
        )

        val supported = keyInfoResolutionService.isSupportedOpts(didOpts)
        assertFalse(supported, "Should not support ManagedOptsDid")
    }

    @Test
    fun multiManagedServiceShouldNotSupportUnsupportedIdentifiers() = runTest {
        // Test that multi service returns false for unsupported identifier types
        val unsupportedInt = 42
        val supported = multiManagedService.isSupportedIdentifier(unsupportedInt)
        assertFalse(supported, "Multi service should not support Int identifiers")
    }

    @Test
    fun keyInfoResolutionServiceShouldHandleManagedOptsKidWithLookupHavingKid() = runTest {
        // Test the branch where lookup already has a kid (should use lookup as-is)
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfoWithKid = KeyInfo<KeyType>(
            kid = "existing-kid-in-lookup",
            alias = "some-alias"
        )

        val opts = ManagedOptsKid(
            identifier = "different-kid-from-opts",  // This should be ignored since lookup has kid
            context = IdentifierContext(),
            lookup = keyInfoWithKid
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid when lookup has kid")
        // The lookup should be used as-is since it already has a kid
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsJwk() = runTest {
        // Test the ManagedOptsJwk branch in asSupportedOpts
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val jwk = keyPair.jose.publicJwk!!

        val opts = ManagedOptsJwk(
            identifier = jwk,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsJwk")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldConvertManagedOptsCoseKey() = runTest {
        // Test the ManagedOptsCoseKey branch in asSupportedOpts
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val jwk = keyPair.jose.publicJwk!!
        val coseKey = jwk.jwkToCoseKey()

        val opts = ManagedOptsCoseKey(
            identifier = coseKey,
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsCoseKey")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }

    // =========== MultiManagedIdentifierResolutionService Error Path Tests ===========

    @Test
    fun multiManagedServiceShouldReturnErrorForUnsupportedIdentifier() = runTest {
        // Use a DID opts with a DID that won't be found by any service
        // The KeyInfoIdentifierResolutionService doesn't support DID resolution
        // and there's no DID service registered in this test setup
        val didOpts = ManagedOptsDid(
            identifier = "did:example:unsupported-did-that-wont-be-found",
            context = IdentifierContext()
        )

        val result = multiManagedService.resolve(didOpts)
        assertTrue(result.isErr, "Should return error for unsupported identifier")
    }

    @Test
    fun multiManagedServiceShouldReturnFalseForUnsupportedIdentifier() = runTest {
        // Test isSupportedIdentifier returns false for unsupported types
        // Use an arbitrary object that definitely isn't a supported identifier type
        class UnsupportedIdentifierType
        val unsupported = multiManagedService.isSupportedIdentifier(UnsupportedIdentifierType())
        assertFalse(unsupported, "Should return false for unsupported identifier type")
    }

    // =========== Additional Branch Coverage Tests for KeyInfoIdentifierResolutionService ===========

    @Test
    fun keyInfoResolutionServiceShouldReturnErrorWhenSupportsReturnsFalse() = runTest {
        // Test the branch: if (!supports(args)) return error
        // Use DID opts which are not supported by KeyInfoIdentifierResolutionService
        val didOpts = ManagedOptsDid(
            identifier = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.resolve(didOpts)
        assertTrue(result.isErr, "Should return error when supports() returns false")
        // Error message says "does not support" or contains "support" (from COMMAND_ARG_NOT_SUPPORTED_ERROR)
        assertTrue(
            result.error.message.defaultMessage.contains("support", ignoreCase = true),
            "Error should mention unsupported type"
        )
    }

    @Test
    fun keyInfoResolutionServiceIsSupportedIdentifierWithKidString() = runTest {
        // Test the String branch with a kid-formatted string
        // This exercises: IdentifierTypeUtils.isKidIdentifier(identifier)
        val kidIdentifier = "some-kid-value"
        val supported = keyInfoResolutionService.isSupportedIdentifier(kidIdentifier)
        assertTrue(supported, "Should support KID string identifier")
    }

    @Test
    fun keyInfoResolutionServiceIsSupportedIdentifierWithAliasString() = runTest {
        // Test the String branch with an alias-formatted string
        // This exercises: IdentifierTypeUtils.isKeyAliasIdentifier(identifier)
        val aliasIdentifier = "alias:my-key"
        val supported = keyInfoResolutionService.isSupportedIdentifier(aliasIdentifier)
        assertTrue(supported, "Should support key alias string identifier")
    }

    @Test
    fun keyInfoResolutionServiceAsSupportedOptsShouldReturnErrorForUnsupportedOpts() = runTest {
        // Test the branch: if (!isSupportedOpts(opts)) return error in asSupportedOpts
        // ManagedOptsDid is not supported by KeyInfoIdentifierResolutionService
        val didOpts = ManagedOptsDid(
            identifier = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            context = IdentifierContext()
        )

        val result = keyInfoResolutionService.asSupportedOpts(didOpts)
        assertTrue(result.isErr, "asSupportedOpts should return error for unsupported opts")
        // Error message contains "supported" (from the error factory)
        assertTrue(
            result.error.message.defaultMessage.contains("support", ignoreCase = true),
            "Error should mention unsupported"
        )
    }

    @Test
    fun keyInfoResolutionServiceShouldHandleManagedOptsKidWithSignatureAlgorithmHint() = runTest {
        // Test the algorithm hint branch in asSupportedOpts for ManagedOptsKid
        val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfoWithAlgHint = KeyInfo<KeyType>(
            kid = "test-kid-with-alg",
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384  // Different algorithm as hint
        )

        val opts = ManagedOptsKid(
            identifier = "test-kid-with-alg",
            context = IdentifierContext(),
            lookup = keyInfoWithAlgHint
        )

        val result = keyInfoResolutionService.asSupportedOpts(opts)
        assertTrue(result.isOk, "Should convert ManagedOptsKid with algorithm hint")
        assertTrue(result.value is ManagedOptsKeyInfo)
    }
}
