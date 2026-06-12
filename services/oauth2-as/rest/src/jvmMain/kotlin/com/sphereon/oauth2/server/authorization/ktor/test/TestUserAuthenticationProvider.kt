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

package com.sphereon.oauth2.server.authorization.ktor.test

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

/**
 * Simple test [UserAuthenticationProvider] for E2E development testing.
 *
 * Accepts hardcoded credentials: `testuser` / `testpass`.
 *
 * **The DI binding that activates this as the session-scoped
 * [UserAuthenticationProvider] lives in `jvmTest` as
 * `TestUserAuthenticationProviderModule`.** Production assemblies that pull
 * this module's `jvmMain` artifact see the class but get no binding
 * contribution, so the no-op default remains in force until a real provider
 * replaces it. The [OAuth2AsKtorServer.kt] `fun main()` dev-mode standalone
 * runner uses the class's companion (`testAuthenticatedSessions`) directly —
 * no DI indirection there.
 */
@Inject
@SingleIn(SessionScope::class)
class TestUserAuthenticationProvider(
    private val configProvider: OAuth2ServersConfigProvider,
) : UserAuthenticationProvider {
    // Use the shared static registry so the Ktor login route can mark sessions authenticated
    private val authenticatedSessions get() = testAuthenticatedSessions

    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> {
        val userId = authenticatedSessions[sessionId] ?: return Ok(null)
        return Ok(
            AuthenticatedUser(
                userId = userId,
                authenticatedAt = Clock.System.now(),
                authenticationMethod = AuthenticationMethod.PASSWORD,
                acr = "urn:mace:incommon:iap:bronze",
                amr = listOf("pwd"),
            ),
        )
    }

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
        context: AuthenticationContext?,
    ): IdkResult<String, AuthenticationError> {
        // Prepend the configured issuer (e.g. https://host/auth) so the browser redirect
        // stays inside the AS path prefix when hosted behind a reverse proxy.
        val base = configProvider.serverConfig.issuer?.trimEnd('/') ?: ""
        return Ok("$base/login?session_id=$sessionId&return_url=${urlEncode(returnUrl)}")
    }

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<String?, AuthenticationError> {
        if (credentials !is UserCredentials.UsernamePassword) {
            return Ok(null)
        }
        if (credentials.username == TEST_USERNAME && credentials.password == TEST_PASSWORD) {
            return Ok(TEST_USERNAME)
        }
        return Ok(null)
    }

    fun authenticateSession(
        sessionId: String,
        userId: String,
    ) {
        authenticatedSessions[sessionId] = userId
    }

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        authenticatedSessions.entries.removeAll { it.value == userId }
        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        if (userId == TEST_USERNAME) {
            return Ok(
                UserInfo(
                    userId = TEST_USERNAME,
                    username = TEST_USERNAME,
                    displayName = "Test User",
                    email = "test@example.com",
                    emailVerified = true,
                    attributes =
                        mapOf(
                            "given_name" to "Test",
                            "family_name" to "User",
                        ),
                ),
            )
        }
        return Err(AuthenticationError.UserNotFound("User $userId not found"))
    }

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.PASSWORD)

    private fun urlEncode(value: String): String = value.replace("&", "%26").replace("=", "%3D").replace("?", "%3F")

    companion object {
        const val TEST_USERNAME = "testuser"
        const val TEST_PASSWORD = "testpass"

        /**
         * Shared registry for test-authenticated sessions.
         * The Ktor login route writes here; the provider reads from it.
         */
        val testAuthenticatedSessions = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
