/*
 * Copyright 2023-2026 Sphereon International B.V.
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
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
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
    fun provideNoOpUserAuthenticationProvider(): UserAuthenticationProvider = NoOpUserAuthenticationProvider()
}

/**
 * No-op implementation — all operations return errors or null.
 */
class NoOpUserAuthenticationProvider : UserAuthenticationProvider {
    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
    ): IdkResult<String, AuthenticationError> = Err(AuthenticationError.Generic(message = "No authentication provider configured"))

    override suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "No authentication provider configured"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Err(AuthenticationError.UserNotFound("No authentication provider configured"))

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(false)
}
