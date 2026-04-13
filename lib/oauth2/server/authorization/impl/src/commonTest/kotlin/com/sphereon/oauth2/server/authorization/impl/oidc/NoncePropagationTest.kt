package com.sphereon.oauth2.server.authorization.impl.oidc

import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationCodeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Tests nonce propagation: session → authorization code.
 * Verifies that the OIDC nonce survives the authorization code creation flow.
 */
class NoncePropagationTest {

    private val ctx = OAuth2ServerTestContext("nonce-propagation-test", this)

    @Test
    fun nonceIsPreservedInCodeData() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)

        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.SUPPORTED
                    )
                )
            )
        )

        val now = Clock.System.now()
        val consent = ConsentDecision(
            userId = "user123",
            clientId = "test-client",
            granted = true,
            grantedScopes = listOf("openid", "profile", "email"),
            grantedAt = now,
            rememberConsent = false
        )

        val session = AuthorizationSession(
            sessionId = "session-with-nonce",
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            scope = "openid profile email",
            state = "state-123",
            responseType = "code",
            codeChallenge = null,
            codeChallengeMethod = null,
            status = SessionStatus.AUTHORIZED,
            authenticatedUserId = "user123",
            consentDecision = consent,
            createdAt = now,
            expiresAt = now + 15.minutes,
            additionalData = emptyMap(),
            nonce = "test-nonce-abc123",
            authTime = now.epochSeconds
        )

        val codeCommand = CreateAuthorizationCodeCommandImpl(
            execution = ctx.execution,
            authorizationCodeStorage = codeStorage,
            configProvider = configProvider
        )

        val codeResult = codeCommand.execute(
            CreateAuthorizationCodeArgs(
                session = session,
                userId = "user123",
                consent = consent
            )
        )

        assertTrue(codeResult.isOk, "Code creation should succeed")
        val code = codeResult.value.value

        // Consume the code to retrieve its data and verify nonce propagation
        val consumed = codeStorage.consumeAuthorizationCode(code)
        assertTrue(consumed.isOk)
        val codeData = consumed.value
        assertNotNull(codeData)
        assertEquals("test-nonce-abc123", codeData.nonce, "Nonce must propagate from session to code")
        assertNotNull(codeData.authTime, "authTime must propagate from session to code")
    }

    @Test
    fun nullNonceIsPreservedAsNull() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)

        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.SUPPORTED
                    )
                )
            )
        )

        val now = Clock.System.now()
        val consent = ConsentDecision(
            userId = "user456",
            clientId = "test-client",
            granted = true,
            grantedScopes = listOf("read", "write"),
            grantedAt = now,
            rememberConsent = false
        )

        // Session without nonce (non-OIDC request)
        val session = AuthorizationSession(
            sessionId = "session-no-nonce",
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            scope = "read write",
            state = "state-456",
            responseType = "code",
            codeChallenge = null,
            codeChallengeMethod = null,
            status = SessionStatus.AUTHORIZED,
            authenticatedUserId = "user456",
            consentDecision = consent,
            createdAt = now,
            expiresAt = now + 15.minutes,
            additionalData = emptyMap(),
            nonce = null
        )

        val codeCommand = CreateAuthorizationCodeCommandImpl(
            execution = ctx.execution,
            authorizationCodeStorage = codeStorage,
            configProvider = configProvider
        )

        val codeResult = codeCommand.execute(
            CreateAuthorizationCodeArgs(
                session = session,
                userId = "user456",
                consent = consent
            )
        )

        assertTrue(codeResult.isOk)
        val code = codeResult.value.value

        val consumed = codeStorage.consumeAuthorizationCode(code)
        assertTrue(consumed.isOk)
        assertNull(consumed.value!!.nonce, "Null nonce should remain null")
    }
}
