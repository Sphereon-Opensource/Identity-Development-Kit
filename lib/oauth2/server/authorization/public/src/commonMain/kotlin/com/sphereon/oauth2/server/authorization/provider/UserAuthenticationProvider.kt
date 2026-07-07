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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import kotlin.time.Clock

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
     * @param context Optional authentication context (session id, application id)
     * @return Redirect URL to authentication system, or error
     */
    suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint? = null,
        context: AuthenticationContext? = null,
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
     * @param context Optional authentication context (session id, application id)
     * @return User ID if authenticated, null if invalid credentials, or error
     */
    suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext? = null,
    ): IdkResult<String?, AuthenticationError>

    /**
     * Authenticate user with credentials and preserve the achieved authentication context.
     *
     * This is the context-bearing form used by login surfaces that mint OIDC sessions. The legacy
     * [authenticateWithCredentials] method remains for existing providers and call sites; its
     * default bridge intentionally carries only the subject and method, because older providers did
     * not expose trustworthy ACR/AMR/auth_time values.
     */
    suspend fun authenticateUserWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext? = null,
    ): IdkResult<AuthenticatedUser?, AuthenticationError> {
        val result = authenticateWithCredentials(credentials, context)
        return result.fold(
            success = { userId ->
                Ok(
                    userId?.let {
                        AuthenticatedUser(
                            userId = it,
                            authenticatedAt = Clock.System.now(),
                            authenticationMethod = AuthenticationMethod.PASSWORD,
                        )
                    },
                )
            },
            failure = { Err(it) },
        )
    }

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
 * Context for an authentication attempt. Carries the authorization session identity and the
 * opaque application / login-surface id resolved at session mint (see
 * [ClientApplicationResolver]), so providers can scope user lookup or login UX per application.
 * All fields optional: a `null` context or `null` fields mean "no application binding" and
 * providers must keep working (legacy / application-agnostic mode).
 */
data class AuthenticationContext(
    val sessionId: String? = null,
    val applicationId: String? = null,
    val acrValues: List<String> = emptyList(),
)

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
) {
    /**
     * Project this [UserInfo] into the flat `Map<String, Any>` shape the AS uses for
     * `userClaims` on `CreateAuthorizationCodeArgs` / `CreateIdTokenArgs`. Top-level
     * fields are mapped to their OIDC standard claim names; everything in [attributes]
     * is included verbatim. Any field whose value is `null` is omitted so we never
     * emit `null`-valued claims into the id_token / userinfo response.
     *
     * One source of truth for this projection — both the post-authn-callback path
     * (`HandleAuthorizeCallbackCommandImpl`) and the SSO short-circuit path
     * (`StandardAuthorizeRequestCommandImpl.issueCodeFromActiveSession`) build
     * `userClaims` from this same helper.
     */
    fun toClaimsMap(): Map<String, Any> =
        buildMap {
            username?.let { put("preferred_username", it) }
            displayName?.let { put("name", it) }
            email?.let { put("email", it) }
            emailVerified?.let { put("email_verified", it) }
            phoneNumber?.let { put("phone_number", it) }
            phoneNumberVerified?.let { put("phone_number_verified", it) }
            putAll(attributes)
        }
}

/**
 * Authentication failures surfaced by [UserAuthenticationProvider]. Implements [IdkErrorType] so
 * federation [com.sphereon.core.api.service.ServiceCommand]s can return [AuthenticationError]
 * directly without an `IdkError`-to-domain mapping shim in the facade.
 *
 * The `description` constructor parameter carries the user-facing text; the [IdkErrorType.message]
 * override projects it into the [IdkError.Message] shape the framework expects.
 */
sealed interface AuthenticationError : IdkErrorType {
    val description: String

    override val message: IdkError.Message
        get() = IdkError.Message(i18nKey = code, defaultMessage = description)

    override val severity: IdkError.Severity
        get() = IdkError.Severity.ERROR

    override val exception: Throwable?
        get() = null

    override val causes: List<IdkErrorType>
        get() = emptyList()

    override val meta: Map<String, Any?>
        get() = emptyMap()

    data class UserNotFound(
        override val description: String = "User not found",
    ) : AuthenticationError {
        override val code: String = "AUTH_USER_NOT_FOUND"
    }

    data class InvalidCredentials(
        override val description: String = "Invalid credentials",
    ) : AuthenticationError {
        override val code: String = "AUTH_INVALID_CREDENTIALS"
    }

    data class Timeout(
        override val description: String = "Authentication timeout",
    ) : AuthenticationError {
        override val code: String = "AUTH_TIMEOUT"
    }

    data class MethodUnavailable(
        val method: AuthenticationMethod,
        override val description: String = "Authentication method unavailable: $method",
    ) : AuthenticationError {
        override val code: String = "AUTH_METHOD_UNAVAILABLE"
    }

    data class MfaRequired(
        val userId: String,
        val methods: List<String>,
        override val description: String = "Multi-factor authentication required",
    ) : AuthenticationError {
        override val code: String = "AUTH_MFA_REQUIRED"
    }

    data class AccountLocked(
        val userId: String,
        val unlockAt: kotlin.time.Instant?,
        override val description: String = "Account locked",
    ) : AuthenticationError {
        override val code: String = "AUTH_ACCOUNT_LOCKED"
    }

    data class Generic(
        override val exception: Throwable? = null,
        override val description: String = "Authentication error",
    ) : AuthenticationError {
        override val code: String = "AUTH_GENERIC"
    }
}
