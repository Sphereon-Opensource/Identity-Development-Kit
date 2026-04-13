/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.oauth2.server.authorization.model.ConsentDecision

/**
 * User consent provider abstraction
 *
 * RFC 6749 Section 3.3: The authorization server SHOULD display the scope
 * information to the resource owner when requested.
 *
 * Before issuing an authorization code or tokens, the Authorization Server must:
 * 1. Authenticate the user (UserAuthenticationProvider)
 * 2. Obtain user consent (ConsentProvider) - THIS INTERFACE
 * 3. Issue authorization code/tokens
 *
 * Consent requirements vary by deployment:
 * - First-party apps may skip consent (implicit trust)
 * - Third-party apps typically require explicit consent
 * - Some deployments remember consent to avoid repeated prompts
 * - Some scopes may require re-consent periodically
 *
 * This abstraction allows the Authorization Server to integrate with different
 * consent UIs and policies without being tightly coupled.
 *
 * Implementation must be provided by the application deploying the Authorization Server.
 */
interface ConsentProvider {

    /**
     * Check if user has previously granted consent
     *
     * Checks if the user has already consented to the requested scopes for this client.
     * If consent was previously granted and is still valid, the consent prompt can be skipped.
     *
     * RFC 6749 Section 3.3: The authorization server MAY fully or partially
     * ignore the scope requested by the client, based on the authorization
     * server policy or the resource owner's instructions.
     *
     * @param userId User identifier
     * @param clientId Client identifier
     * @param requestedScopes List of requested scopes
     * @return Existing consent decision if found and valid, null otherwise, or error
     */
    suspend fun getExistingConsent(
        userId: String,
        clientId: String,
        requestedScopes: List<String>?
    ): IdkResult<ConsentDecision?, ConsentError>

    /**
     * Check if consent is required
     *
     * Determines whether a consent prompt is needed based on:
     * - Client trust level (first-party vs third-party)
     * - Previously granted consent
     * - Consent expiration policies
     * - Sensitive scope requirements
     * - Regulatory requirements (GDPR, etc.)
     *
     * @param userId User identifier
     * @param clientId Client identifier
     * @param requestedScopes List of requested scopes
     * @return true if consent prompt is required, false if can skip
     */
    suspend fun isConsentRequired(
        userId: String,
        clientId: String,
        requestedScopes: List<String>?
    ): IdkResult<Boolean, ConsentError>

    /**
     * Create consent prompt URL
     *
     * Generates a URL to the consent screen where the user can review
     * and approve/deny the authorization request.
     *
     * The consent screen should display:
     * - Client name and information
     * - Requested scopes and their descriptions
     * - What data will be shared
     * - Option to approve or deny
     * - Option to remember consent
     *
     * After the user makes a decision, they should be redirected back to
     * the returnUrl with consent context.
     *
     * @param request Consent request details
     * @param returnUrl URL to redirect back to after consent decision
     * @return Redirect URL to consent screen, or error
     */
    suspend fun createConsentPrompt(
        request: ConsentRequest,
        returnUrl: String
    ): IdkResult<String, ConsentError>

    /**
     * Store user consent decision
     *
     * Persists the user's consent decision for future reference.
     * If rememberConsent is true, future authorization requests may skip the consent prompt.
     *
     * @param decision User's consent decision
     * @return Success or error
     */
    suspend fun storeConsent(
        decision: ConsentDecision
    ): IdkResult<Unit, ConsentError>

    /**
     * Revoke user consent
     *
     * Removes previously granted consent for a client.
     * Should be called when:
     * - User explicitly revokes consent in settings
     * - Client is deregistered
     * - Security incident
     *
     * @param userId User identifier
     * @param clientId Client identifier
     * @return Success or error
     */
    suspend fun revokeConsent(
        userId: String,
        clientId: String
    ): IdkResult<Unit, ConsentError>

    /**
     * List all consents for a user
     *
     * Returns all active consent grants for a user.
     * Useful for:
     * - User consent management UI
     * - Privacy dashboards
     * - Security audits
     *
     * @param userId User identifier
     * @return List of consent decisions, or error
     */
    suspend fun listUserConsents(
        userId: String
    ): IdkResult<List<ConsentDecision>, ConsentError>

    /**
     * Get consent expiration policy
     *
     * Returns the expiration policy for consent grants.
     * Some scopes may require periodic re-consent (e.g., financial data every 90 days).
     *
     * @param scopes List of scopes
     * @return Expiration policy (seconds until expiration, null = never expires)
     */
    suspend fun getConsentExpirationPolicy(
        scopes: List<String>
    ): IdkResult<Int?, ConsentError>

    /**
     * Validate consent decision
     *
     * Verifies that a consent decision is valid and not tampered with.
     * Useful when consent is provided via signed tokens or assertions.
     *
     * @param decision Consent decision to validate
     * @return true if valid, false otherwise, or error
     */
    suspend fun validateConsentDecision(
        decision: ConsentDecision
    ): IdkResult<Boolean, ConsentError>
}

/**
 * Consent request information for the consent prompt
 */
data class ConsentRequest(
    /**
     * User identifier
     */
    val userId: String,

    /**
     * Client identifier
     */
    val clientId: String,

    /**
     * Client name for display
     */
    val clientName: String?,

    /**
     * Requested scopes
     */
    val requestedScopes: List<String>?,

    /**
     * Authorization session ID
     */
    val sessionId: String,

    /**
     * State parameter from authorization request
     */
    val state: String? = null,

    /**
     * Requested resource indicators (RFC 8707)
     */
    val resources: List<String>? = null,

    /**
     * Authorization details (RFC 9396)
     */
    val authorizationDetails: List<Map<String, Any>>? = null,

    /**
     * UI locales for consent screen
     */
    val uiLocales: List<String>? = null
)

/**
 * Consent errors
 */
sealed interface ConsentError {
    val message: String

    /**
     * Consent not found
     */
    data class NotFound(
        override val message: String = "Consent not found"
    ) : ConsentError

    /**
     * Consent expired
     */
    data class Expired(
        override val message: String = "Consent expired"
    ) : ConsentError

    /**
     * Consent revoked
     */
    data class Revoked(
        override val message: String = "Consent was revoked"
    ) : ConsentError

    /**
     * Consent denied by user
     */
    data class Denied(
        val reason: String? = null,
        override val message: String = "User denied consent"
    ) : ConsentError

    /**
     * Invalid consent decision
     */
    data class InvalidDecision(
        val reason: String,
        override val message: String = "Invalid consent decision: $reason"
    ) : ConsentError

    /**
     * Consent timeout
     */
    data class Timeout(
        override val message: String = "Consent prompt timeout"
    ) : ConsentError

    /**
     * Consent UI unavailable
     */
    data class UiUnavailable(
        override val message: String = "Consent UI unavailable"
    ) : ConsentError

    /**
     * Generic error
     */
    data class Generic(
        val exception: Throwable? = null,
        override val message: String = "Consent error"
    ) : ConsentError
}

/**
 * Scope metadata for consent display
 */
data class ScopeMetadata(
    /**
     * Scope identifier (e.g., "profile", "email")
     */
    val scope: String,

    /**
     * Display name for consent screen
     */
    val displayName: String,

    /**
     * Description of what this scope allows
     */
    val description: String,

    /**
     * Whether this scope is sensitive (requires explicit consent)
     */
    val sensitive: Boolean = false,

    /**
     * Icon URL for display
     */
    val iconUrl: String? = null,

    /**
     * Consent expiration policy (seconds, null = never expires)
     */
    val expirationSeconds: Int? = null
)
