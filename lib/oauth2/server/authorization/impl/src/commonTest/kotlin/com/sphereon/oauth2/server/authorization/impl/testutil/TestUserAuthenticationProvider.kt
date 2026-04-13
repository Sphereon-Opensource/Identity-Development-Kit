package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
/**
 * Test implementation of UserAuthenticationProvider for manual construction in tests.
 */
class TestUserAuthenticationProvider : UserAuthenticationProvider {

    override suspend fun getAuthenticatedUser(
        sessionId: String
    ): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?
    ): IdkResult<String, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "Not implemented in test"))

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials
    ): IdkResult<String?, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "Not implemented in test"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> =
        Ok(
            UserInfo(
                userId = userId,
                username = "test-user",
                displayName = "Test User",
                email = "test@example.com",
                emailVerified = true
            )
        )

    override suspend fun isAuthenticationMethodAvailable(
        method: AuthenticationMethod
    ): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.PASSWORD)
}
