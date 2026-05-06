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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * Map-bound under the `"noop"` key alongside the federated and local providers. Every operation
 * fails or returns null. Selected by config `oauth2.user-provider.mode = noop` for deployments
 * that want to assert "no auth provider configured" deterministically rather than rely on a
 * default that might quietly accept traffic.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<UserAuthenticationProvider>())
@StringKey("noop")
class NoOpUserAuthenticationProvider : UserAuthenticationProvider {
    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
    ): IdkResult<String, AuthenticationError> = Err(AuthenticationError.Generic(description = "No authentication provider configured"))

    override suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError> =
        Err(AuthenticationError.Generic(description = "No authentication provider configured"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Err(AuthenticationError.UserNotFound("No authentication provider configured"))

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(false)
}
