package com.sphereon.oauth2.server.authorization.impl.command.revocation

import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevokeTokenCommandImplTest {

    private val ctx = OAuth2ServerTestContext("revoke-token-test", this)

    private fun createConfigProvider(
        revocation: FeaturePolicy = FeaturePolicy.SUPPORTED
    ) = TestOAuth2ServersConfigProvider(
        OAuth2ServersConfig(
            servers = mapOf(
                "default" to OAuth2ServerInstanceConfig(revocation = revocation)
            )
        )
    )

    @Test
    fun testRevokeAccessToken() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Store an access token
        val accessToken = TestFixtures.createAccessToken(
            accessToken = "at-revoke-1",
            clientId = "confidential-client"
        )
        tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)

        // Verify it exists
        val before = tokenStorage.getAccessToken("at-revoke-1")
        assertTrue(before.isOk)
        assertFalse(before.value!!.revoked)

        // Revoke it
        val result = command.execute(
            RevokeTokenArgs(
                token = "at-revoke-1",
                clientId = "confidential-client"
            )
        )
        assertTrue(result.isOk)

        // Verify it's revoked
        val after = tokenStorage.getAccessToken("at-revoke-1")
        assertTrue(after.isOk)
        assertTrue(after.value!!.revoked)
    }

    @Test
    fun testRevokeRefreshToken() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Store a refresh token
        val refreshToken = TestFixtures.createRefreshToken(
            refreshToken = "rt-revoke-1",
            clientId = "confidential-client"
        )
        tokenStorage.storeRefreshToken(refreshToken.refreshToken, refreshToken)

        // Revoke it
        val result = command.execute(
            RevokeTokenArgs(
                token = "rt-revoke-1",
                tokenTypeHint = "refresh_token",
                clientId = "confidential-client"
            )
        )
        assertTrue(result.isOk)

        // Verify it's revoked
        val after = tokenStorage.getRefreshToken("rt-revoke-1")
        assertTrue(after.isOk)
        assertTrue(after.value!!.revoked)
    }

    @Test
    fun testRevokeRefreshTokenCascadesToAccessTokens() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Store a refresh token
        val refreshToken = TestFixtures.createRefreshToken(
            refreshToken = "rt-cascade-1",
            clientId = "confidential-client"
        )
        tokenStorage.storeRefreshToken(refreshToken.refreshToken, refreshToken)

        // Store access token linked to the refresh token
        val accessToken = TestFixtures.createAccessToken(
            accessToken = "at-cascade-1",
            clientId = "confidential-client"
        ).copy(refreshTokenId = "rt-cascade-1")
        tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)

        // Revoke the refresh token
        val result = command.execute(
            RevokeTokenArgs(
                token = "rt-cascade-1",
                tokenTypeHint = "refresh_token",
                clientId = "confidential-client"
            )
        )
        assertTrue(result.isOk)

        // Verify refresh token is revoked
        val rtAfter = tokenStorage.getRefreshToken("rt-cascade-1")
        assertTrue(rtAfter.isOk)
        assertTrue(rtAfter.value!!.revoked)

        // Verify associated access token is also revoked
        val atAfter = tokenStorage.getAccessToken("at-cascade-1")
        assertTrue(atAfter.isOk)
        assertTrue(atAfter.value!!.revoked)
    }

    @Test
    fun testUnknownTokenReturnsSuccess() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Try to revoke a non-existent token
        val result = command.execute(
            RevokeTokenArgs(
                token = "non-existent-token",
                clientId = "confidential-client"
            )
        )

        // Per RFC 7009: unknown tokens still return success
        assertTrue(result.isOk)
    }

    @Test
    fun testClientMismatchReturnsSuccess() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Store an access token for client A
        val accessToken = TestFixtures.createAccessToken(
            accessToken = "at-mismatch-1",
            clientId = "client-a"
        )
        tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)

        // Try to revoke it as client B
        val result = command.execute(
            RevokeTokenArgs(
                token = "at-mismatch-1",
                clientId = "client-b"
            )
        )

        // Per RFC 7009: client mismatch still returns success
        assertTrue(result.isOk)

        // But the token should NOT be revoked
        val after = tokenStorage.getAccessToken("at-mismatch-1")
        assertTrue(after.isOk)
        assertFalse(after.value!!.revoked)
    }

    @Test
    fun testRevocationDisabledReturnsError() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider(revocation = FeaturePolicy.DISABLED)
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        val result = command.execute(
            RevokeTokenArgs(
                token = "any-token",
                clientId = "any-client"
            )
        )

        assertTrue(result.isErr)
    }

    @Test
    fun testRevokeWithAccessTokenHintFindsRefreshToken() = runTest {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val tokenStorage = InMemoryTokenStorageImpl(storage)
        val configProvider = createConfigProvider()
        val command = RevokeTokenCommandImpl(ctx.execution, tokenStorage, configProvider)

        // Store a refresh token
        val refreshToken = TestFixtures.createRefreshToken(
            refreshToken = "rt-hint-1",
            clientId = "confidential-client"
        )
        tokenStorage.storeRefreshToken(refreshToken.refreshToken, refreshToken)

        // Revoke with wrong hint (access_token), but token is actually refresh
        val result = command.execute(
            RevokeTokenArgs(
                token = "rt-hint-1",
                tokenTypeHint = "access_token",
                clientId = "confidential-client"
            )
        )
        assertTrue(result.isOk)

        // Verify refresh token is revoked (fallback search found it)
        val after = tokenStorage.getRefreshToken("rt-hint-1")
        assertTrue(after.isOk)
        assertTrue(after.value!!.revoked)
    }
}
