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
 */

package com.sphereon.oauth2.server.authorization.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyAuthorizationCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyPreAuthorizedCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryPreAuthorizedCodeStorage
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Integration tests for OID4VCI flows through the Authorization Server.
 *
 * Tests pre-authorized code grant (OID4VCI Section 4.1.1), authorization code
 * grant with credential_configuration_ids, tx_code verification, single-use
 * code enforcement, and PKCE validation.
 */
class Oid4vciAsIntegrationTest {
    private val ctx = OAuth2ServerTestContext("oid4vci-as-test", this)
    private val execution = ctx.execution
    private val configProvider = TestOAuth2ServersConfigProvider()

    /** Client that supports the pre-authorized code grant type */
    private val oid4vciClient =
        ClientRegistration(
            clientId = "oid4vci-client",
            clientSecret = null,
            clientType = ClientType.PUBLIC,
            grantTypes =
                listOf(
                    GrantType.PRE_AUTHORIZED_CODE,
                    GrantType.AUTHORIZATION_CODE,
                ),
            redirectUris = listOf("https://wallet.example.com/callback"),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
            requirePkce = true,
            requirePushedAuthorizationRequests = false,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("openid"),
            clientName = "Test OID4VCI Wallet Client",
        )

    // =========================================================================
    // Test 1: Pre-authorized code with credential_identifiers
    // =========================================================================

    @Test
    fun testPreAuthorizedCodeWithCredentialConfigurationIds() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client
            val registerResult = clientRegistry.registerClient(oid4vciClient)
            assertTrue(registerResult.isOk)

            // Store pre-authorized code with credential configuration IDs
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-1",
                    credentialConfigurationIds = listOf("UniversityDegreeCredential", "EmployeeIDCredential"),
                    subject = "did:example:holder123",
                    txCodeRequired = false,
                    txCodeHash = null,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val storeResult = preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-001", preAuthData)
            assertTrue(storeResult.isOk)

            // Verify pre-authorized code grant
            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-001",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isOk, "Pre-auth code verification should succeed, got: ${if (result.isErr) result.error else ""}")

            val grant = result.value
            assertEquals("oid4vci-session-1", grant.sessionId)
            assertEquals("did:example:holder123", grant.subject)
            assertNotNull(grant.credentialConfigurationIds)
            assertEquals(2, grant.credentialConfigurationIds.size)
            assertTrue(grant.credentialConfigurationIds.contains("UniversityDegreeCredential"))
            assertTrue(grant.credentialConfigurationIds.contains("EmployeeIDCredential"))
        }

    // =========================================================================
    // Test 2: Authorization code with PKCE and credential data in additionalData
    // =========================================================================

    @Test
    fun testAuthorizationCodeWithPkceAndCredentialData() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client
            val registerResult = clientRegistry.registerClient(oid4vciClient)
            assertTrue(registerResult.isOk)

            // Store authorization code with PKCE and credential_configuration_ids in additionalData
            val now = Clock.System.now()
            val codeData =
                AuthorizationCodeData(
                    code = "auth-code-oid4vci-001",
                    clientId = oid4vciClient.clientId,
                    subject = "did:example:holder456",
                    redirectUri = oid4vciClient.redirectUris.first(),
                    scope = "openid",
                    codeChallenge = TestFixtures.Pkce.CODE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData =
                        mapOf(
                            "credential_configuration_ids" to listOf("UniversityDegreeCredential"),
                        ),
                )
            val storeResult = codeStorage.storeAuthorizationCode(codeData.code, codeData)
            assertTrue(storeResult.isOk)

            // Verify authorization code grant with correct PKCE verifier
            val verifyCommand =
                VerifyAuthorizationCodeGrantCommandImpl(
                    execution = execution,
                    authorizationCodeStorage = codeStorage,
                    tokenStorage = InMemoryTokenStorageImpl(storage),
                    clientRegistry = clientRegistry,
                    configProvider = configProvider,
                )

            val result =
                verifyCommand.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = "auth-code-oid4vci-001",
                        redirectUri = oid4vciClient.redirectUris.first(),
                        clientId = oid4vciClient.clientId,
                        codeVerifier = TestFixtures.Pkce.CODE_VERIFIER,
                    ),
                )
            assertTrue(result.isOk, "Auth code with PKCE verification should succeed, got: ${if (result.isErr) result.error else ""}")

            assertEquals("did:example:holder456", result.value.subject)
            assertEquals(oid4vciClient.clientId, result.value.clientId)
            assertEquals("openid", result.value.scope)

            // Verify additionalData propagated through
            assertTrue(result.value.additionalData.containsKey("credential_configuration_ids"))
        }

    // =========================================================================
    // Test 3: Pre-auth code rejected when already consumed (single-use)
    // =========================================================================

    @Test
    fun testPreAuthorizedCodeRejectedWhenAlreadyConsumed() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)

            // Store pre-authorized code
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-replay",
                    credentialConfigurationIds = listOf("VerifiableCredential"),
                    subject = "did:example:replaytest",
                    txCodeRequired = false,
                    txCodeHash = null,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val storeResult = preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-replay", preAuthData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            // First exchange - should succeed
            val firstResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-replay",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(firstResult.isOk, "First exchange should succeed")

            // Second exchange with same code - should fail (code already consumed)
            val secondResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-replay",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(secondResult.isErr, "Second exchange with same code must fail (single-use enforcement)")
        }

    // =========================================================================
    // Test 4: Auth-code PKCE verification fails with wrong verifier
    // =========================================================================

    @Test
    fun testAuthCodePkceFailsWithWrongVerifier() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client
            val registerResult = clientRegistry.registerClient(oid4vciClient)
            assertTrue(registerResult.isOk)

            // Store authorization code with PKCE
            val now = Clock.System.now()
            val codeData =
                AuthorizationCodeData(
                    code = "auth-code-pkce-wrong",
                    clientId = oid4vciClient.clientId,
                    subject = "did:example:holder789",
                    redirectUri = oid4vciClient.redirectUris.first(),
                    scope = "openid",
                    codeChallenge = TestFixtures.Pkce.CODE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData = emptyMap(),
                )
            val storeResult = codeStorage.storeAuthorizationCode(codeData.code, codeData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyAuthorizationCodeGrantCommandImpl(
                    execution = execution,
                    authorizationCodeStorage = codeStorage,
                    tokenStorage = InMemoryTokenStorageImpl(storage),
                    clientRegistry = clientRegistry,
                    configProvider = configProvider,
                )

            // Exchange with wrong code_verifier
            val result =
                verifyCommand.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = "auth-code-pkce-wrong",
                        redirectUri = oid4vciClient.redirectUris.first(),
                        clientId = oid4vciClient.clientId,
                        codeVerifier = "completely-wrong-code-verifier-value",
                    ),
                )
            assertTrue(result.isErr, "PKCE verification with wrong verifier must fail")
        }

    // =========================================================================
    // Test 5: Pre-auth code with tx_code verification
    // =========================================================================

    @Test
    fun testPreAuthorizedCodeWithTxCodeSuccess() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)

            // Compute SHA-256 hash of the expected tx_code "123456"
            val txCodeValue = "123456"
            val txCodeHashValue = hash(txCodeValue.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()

            // Store pre-authorized code with tx_code requirement
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-txcode",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-user",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val storeResult = preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-txcode", preAuthData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            // Exchange with correct tx_code
            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-txcode",
                        txCode = txCodeValue,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isOk, "Exchange with correct tx_code should succeed, got: ${if (result.isErr) result.error else ""}")

            val grant = result.value
            assertEquals("oid4vci-session-txcode", grant.sessionId)
            assertEquals("did:example:txcode-user", grant.subject)
            assertTrue(grant.credentialConfigurationIds.contains("IdentityCredential"))
        }

    @Test
    fun testPreAuthorizedCodeWithWrongTxCodeFails() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)

            // Compute SHA-256 hash of the expected tx_code "123456"
            val correctTxCode = "123456"
            val txCodeHashValue = hash(correctTxCode.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()

            // Store pre-authorized code with tx_code requirement
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-txcode-fail",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-user-fail",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val storeResult = preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-txcode-wrong", preAuthData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            // Exchange with wrong tx_code
            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-txcode-wrong",
                        txCode = "999999",
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isErr, "Exchange with wrong tx_code must fail")
        }

    @Test
    fun wrongTxCodeDoesNotConsumePreAuthorizedCode() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val correctTxCode = "123456"
            val txCodeHashValue = hash(correctTxCode.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-txcode-retry",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-retry",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-txcode-retry", preAuthData).isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            val wrongResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-txcode-retry",
                        txCode = "999999",
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(wrongResult.isErr, "Wrong tx_code must be rejected")
            assertEquals("invalid_grant", wrongResult.error.code)

            val retryResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-txcode-retry",
                        txCode = correctTxCode,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(retryResult.isOk, "The same code must remain usable after a wrong tx_code")
            assertEquals("oid4vci-session-txcode-retry", retryResult.value.sessionId)
        }

    @Test
    fun expiredPreAuthorizedCodeIsRejected() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-expired",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:expired",
                    clientId = oid4vciClient.clientId,
                    createdAt = now - 10.minutes,
                    expiresAt = now - 1.seconds,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-expired", preAuthData).isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-expired",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isErr, "Expired pre-authorized code must be rejected")
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun preAuthorizedCodeWithExpirationAtNowIsRejected() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val fixedClock =
                object : Clock {
                    override fun now(): Instant = now
                }
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-expiration-boundary",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:expiration-boundary",
                    clientId = oid4vciClient.clientId,
                    createdAt = now - 10.minutes,
                    expiresAt = now,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-expiration-boundary", preAuthData).isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = fixedClock,
                )

            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-expiration-boundary",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isErr, "A pre-authorized code expiring at now must be rejected")
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun replacementAfterValidationCannotMintTheOriginalGrant() =
        runTest {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val original =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-original",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:original",
                    clientId = oid4vciClient.clientId,
                    createdAt = now - 10.minutes,
                    expiresAt = now + 10.minutes,
                )
            val replacement = original.copy(sessionId = "oid4vci-session-replacement", subject = "did:example:replacement")
            val preAuthCodeStorage = ReplacingPreAuthorizedCodeStorage(original, replacement)
            val fixedClock =
                object : Clock {
                    override fun now(): Instant = now
                }
            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = fixedClock,
                )

            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-replaced",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "A record replaced after validation must not be consumed")
            assertEquals("invalid_grant", result.error.code)
            assertEquals("oid4vci-session-replacement", preAuthCodeStorage.current.sessionId)
        }

    @Test
    fun expirationDuringValidationCannotBeCommitted() =
        runTest {
            val initialNow = Instant.parse("2026-01-01T00:00:00Z")
            val expiration = initialNow + 1.seconds
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-expiry-advance",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:expiry-advance",
                    clientId = oid4vciClient.clientId,
                    createdAt = initialNow,
                    expiresAt = expiration,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-expiry-advance", preAuthData).isOk)
            val advancingClock =
                object : Clock {
                    private var calls = 0

                    override fun now(): Instant =
                        if (calls++ == 0) initialNow else expiration
                }
            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = advancingClock,
                )

            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-expiry-advance",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "A code expiring during validation must not be committed")
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun compareAndConsumeRejectsReplacementAndLeavesReplacementInStorage() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val original =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-original-record",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:original-record",
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val replacement = original.copy(sessionId = "oid4vci-session-replacement-record")
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-replacement-record", original).isOk)
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-replacement-record", replacement).isOk)

            val result =
                preAuthCodeStorage.consumePreAuthorizedCodeIfValid(
                    code = "pre-auth-code-replacement-record",
                    expectedData = original,
                    now = now,
                )

            assertTrue(result.isOk)
            assertNull(result.value, "A replaced record must not be consumed as the original")
            assertEquals(replacement, preAuthCodeStorage.findPreAuthorizedCode("pre-auth-code-replacement-record").value)
        }

    @Test
    fun wrongClientBindingDoesNotConsumePreAuthorizedCode() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-client-binding",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:client-binding",
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-client-binding", preAuthData).isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            val wrongClientResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-client-binding",
                        txCode = null,
                        clientId = "different-client",
                    ),
                )
            assertTrue(wrongClientResult.isErr, "A client-bound code must reject a different client")
            assertEquals("invalid_grant", wrongClientResult.error.code)

            val retryResult =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-client-binding",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(retryResult.isOk, "The bound client must still be able to use the code")
        }

    @Test
    fun concurrentValidPreAuthorizedCodeAttemptsOnlyAllowOneSuccess() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-concurrent",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:concurrent",
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            assertTrue(preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-concurrent", preAuthData).isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            val results =
                coroutineScope {
                    (1..16).map {
                        async(Dispatchers.Default) {
                            verifyCommand.execute(
                                VerifyPreAuthCodeArgs(
                                    preAuthorizedCode = "pre-auth-code-concurrent",
                                    txCode = null,
                                    clientId = oid4vciClient.clientId,
                                ),
                            )
                        }
                    }.awaitAll()
                }
            assertEquals(1, results.count { it.isOk }, "Concurrent valid exchanges must allow exactly one success")
            assertEquals(15, results.count { it.isErr && it.error.code == "invalid_grant" })
        }

    @Test
    fun testPreAuthorizedCodeWithMissingTxCodeFails() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val preAuthCodeStorage = InMemoryPreAuthorizedCodeStorage(storage)

            // Compute SHA-256 hash of the expected tx_code
            val txCodeHashValue = hash("123456".encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()

            // Store pre-authorized code with tx_code requirement
            val now = Clock.System.now()
            val preAuthData =
                PreAuthorizedCodeData(
                    sessionId = "oid4vci-session-txcode-missing",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-user-missing",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                )
            val storeResult = preAuthCodeStorage.storePreAuthorizedCode("pre-auth-code-txcode-missing", preAuthData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyPreAuthorizedCodeGrantCommandImpl(
                    execution = execution,
                    preAuthorizedCodeStorage = preAuthCodeStorage,
                    clock = Clock.System,
                )

            // Exchange without tx_code (null)
            val result =
                verifyCommand.execute(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = "pre-auth-code-txcode-missing",
                        txCode = null,
                        clientId = oid4vciClient.clientId,
                    ),
                )
            assertTrue(result.isErr, "Exchange with missing tx_code must fail when tx_code is required")
        }

    private class ReplacingPreAuthorizedCodeStorage(
        initial: PreAuthorizedCodeData,
        replacement: PreAuthorizedCodeData,
    ) : PreAuthorizedCodeStorage {
        private val replacementData = replacement
        var current: PreAuthorizedCodeData = initial

        override suspend fun storePreAuthorizedCode(
            code: String,
            data: PreAuthorizedCodeData,
        ): IdkResult<Unit, AuthorizationServerError.StorageError> {
            current = data
            return Ok(Unit)
        }

        override suspend fun findPreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> = Ok(current)

        override suspend fun consumePreAuthorizedCodeIfValid(
            code: String,
            expectedData: PreAuthorizedCodeData,
            now: Instant,
        ): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> {
            current = replacementData
            return Ok(null)
        }

        override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> {
            current = replacementData
            return Ok(current)
        }

        override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)
    }
}
