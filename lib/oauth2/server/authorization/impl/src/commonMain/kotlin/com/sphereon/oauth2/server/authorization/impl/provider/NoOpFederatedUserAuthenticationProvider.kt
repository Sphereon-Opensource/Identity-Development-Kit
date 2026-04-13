package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Default no-op UserAuthenticationProvider module.
 *
 * Replaced at the service level by a real provider module.
 */
@ContributesTo(SessionScope::class)
interface NoOpUserAuthenticationProviderModule {

    @Provides
    @SingleIn(SessionScope::class)
    fun provideNoOpUserAuthenticationProvider(): UserAuthenticationProvider {
        return NoOpUserAuthenticationProvider()
    }
}

/**
 * No-op implementation — all operations return errors or null.
 */
class NoOpUserAuthenticationProvider : UserAuthenticationProvider {

    override suspend fun getAuthenticatedUser(
        sessionId: String
    ): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?
    ): IdkResult<String, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "No authentication provider configured"))

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials
    ): IdkResult<String?, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "No authentication provider configured"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> =
        Err(AuthenticationError.UserNotFound("No authentication provider configured"))

    override suspend fun isAuthenticationMethodAvailable(
        method: AuthenticationMethod
    ): IdkResult<Boolean, AuthenticationError> = Ok(false)
}
