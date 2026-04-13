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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult

/**
 * User authentication provider abstraction
 *
 * The Authorization Server needs to authenticate users before issuing authorization codes
 * or tokens. However, the authentication mechanism varies widely across deployments:
 * - Form-based login (username/password)
 * - OAuth/OIDC (social login)
 * - SAML
 * - LDAP/Active Directory
 * - Multi-factor authentication (MFA)
 * - Biometric authentication
 * - Passwordless (WebAuthn, magic links)
 *
 * This abstraction allows the Authorization Server to integrate with any authentication
 * system without being tightly coupled to a specific implementation.
 *
 * Implementation must be provided by the application deploying the Authorization Server.
 */
interface UserAuthenticationProvider {
    /**
     * Check if a user is currently authenticated
     *
     * Checks if the current request context has an authenticated user session.
     * This typically involves checking for:
     * - Valid session cookie
     * - Valid authentication token
     * - Valid SSO session
     *
     * @param sessionId Authorization session identifier (used to track auth flow)
     * @return User ID if authenticated, null if not authenticated, or error
     */
    suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError>

    /**
     * Initiate user authentication
     *
     * Creates a redirect URL to the authentication system.
     * After successful authentication, the user should be redirected back
     * to the returnUrl with authentication context.
     *
     * The returnUrl should be the authorization endpoint with the session ID
     * so the flow can continue after authentication.
     *
     * @param sessionId Authorization session identifier
     * @param returnUrl URL to redirect back to after authentication
     * @param hint Optional login hint (e.g., email address, username)
     * @return Redirect URL to authentication system, or error
     */
    suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint? = null,
    ): IdkResult<String, AuthenticationError>

    /**
     * Authenticate user with credentials
     *
     * Directly authenticate a user with provided credentials.
     * Useful for:
     * - Resource Owner Password Credentials Grant (RFC 6749 Section 4.3)
     * - Direct authentication flows
     * - Testing/development
     *
     * Returns a user ID if authentication succeeds, null if credentials are invalid.
     *
     * SECURITY NOTE: This method should implement rate limiting and brute-force protection.
     *
     * @param credentials User credentials (username/password, token, etc.)
     * @return User ID if authenticated, null if invalid credentials, or error
     */
    suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError>

    /**
     * Logout user
     *
     * Terminates the user's authentication session.
     * Should be called when:
     * - User explicitly logs out
     * - Authorization is denied
     * - Session expires
     *
     * @param userId User identifier
     * @return Success or error
     */
    suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError>

    /**
     * Get user information
     *
     * Retrieves basic user information for display in consent screens
     * and token claims.
     *
     * @param userId User identifier
     * @return User information or error
     */
    suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError>

    /**
     * Check if authentication method is available
     *
     * Some authentication methods may not always be available
     * (e.g., MFA provider is down, SSO is unavailable).
     *
     * @param method Authentication method to check
     * @return true if available, false otherwise
     */
    suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError>
}

/**
 * Authenticated user information
 */
data class AuthenticatedUser(
    /**
     * Unique user identifier
     */
    val userId: String,
    /**
     * When the user authenticated
     */
    val authenticatedAt: kotlin.time.Instant,
    /**
     * Authentication method used
     */
    val authenticationMethod: AuthenticationMethod,
    /**
     * Authentication context reference (acr)
     * OpenID Connect Core 1.0 Section 2
     */
    val acr: String? = null,
    /**
     * Authentication Methods References (amr)
     * OpenID Connect Core 1.0 Section 2
     */
    val amr: List<String>? = null,
)

/**
 * User credentials for direct authentication
 */
sealed interface UserCredentials {
    /**
     * Username and password credentials
     */
    data class UsernamePassword(
        val username: String,
        val password: String,
    ) : UserCredentials

    /**
     * Bearer token credentials (e.g., API key, SSO token)
     */
    data class BearerToken(
        val token: String,
    ) : UserCredentials

    /**
     * OAuth/OIDC token
     */
    data class OAuthToken(
        val accessToken: String,
        val provider: String,
    ) : UserCredentials

    /**
     * Custom credentials
     */
    data class Custom(
        val type: String,
        val data: Map<String, String>,
    ) : UserCredentials
}

/**
 * Authentication hint to pre-fill login forms
 */
data class AuthenticationHint(
    /**
     * Login hint (email, username, phone number)
     */
    val loginHint: String? = null,
    /**
     * Preferred authentication method
     */
    val preferredMethod: AuthenticationMethod? = null,
    /**
     * UI locales (preferred language)
     */
    val uiLocales: List<String>? = null,
    /**
     * Federation provider ID — selects which upstream IdP to use for federation login.
     * When set, overrides the default provider in [FederatedUserAuthenticationProvider].
     */
    val providerId: String? = null,
)

/**
 * Authentication method
 */
enum class AuthenticationMethod {
    /**
     * Username and password
     */
    PASSWORD,

    /**
     * Multi-factor authentication
     */
    MFA,

    /**
     * OAuth/OpenID Connect (social login)
     */
    OAUTH,

    /**
     * SAML
     */
    SAML,

    /**
     * WebAuthn (biometric, security key)
     */
    WEBAUTHN,

    /**
     * Passwordless (magic link, OTP)
     */
    PASSWORDLESS,

    /**
     * Certificate-based
     */
    CERTIFICATE,

    /**
     * Custom method
     */
    CUSTOM,
}

/**
 * User information for display and claims
 */
data class UserInfo(
    /**
     * User identifier
     */
    val userId: String,
    /**
     * Username or email
     */
    val username: String? = null,
    /**
     * Display name
     */
    val displayName: String? = null,
    /**
     * Email address
     */
    val email: String? = null,
    /**
     * Email verified
     */
    val emailVerified: Boolean? = null,
    /**
     * Phone number
     */
    val phoneNumber: String? = null,
    /**
     * Phone verified
     */
    val phoneNumberVerified: Boolean? = null,
    /**
     * Additional user attributes
     */
    val attributes: Map<String, Any> = emptyMap(),
)

/**
 * Authentication errors
 */
sealed interface AuthenticationError {
    val message: String

    /**
     * User not found
     */
    data class UserNotFound(
        override val message: String = "User not found",
    ) : AuthenticationError

    /**
     * Invalid credentials
     */
    data class InvalidCredentials(
        override val message: String = "Invalid credentials",
    ) : AuthenticationError

    /**
     * Authentication timeout
     */
    data class Timeout(
        override val message: String = "Authentication timeout",
    ) : AuthenticationError

    /**
     * Authentication method unavailable
     */
    data class MethodUnavailable(
        val method: AuthenticationMethod,
        override val message: String = "Authentication method unavailable: $method",
    ) : AuthenticationError

    /**
     * MFA required
     */
    data class MfaRequired(
        val userId: String,
        val methods: List<String>,
        override val message: String = "Multi-factor authentication required",
    ) : AuthenticationError

    /**
     * Account locked
     */
    data class AccountLocked(
        val userId: String,
        val unlockAt: kotlin.time.Instant?,
        override val message: String = "Account locked",
    ) : AuthenticationError

    /**
     * Generic error
     */
    data class Generic(
        val exception: Throwable? = null,
        override val message: String = "Authentication error",
    ) : AuthenticationError
}
