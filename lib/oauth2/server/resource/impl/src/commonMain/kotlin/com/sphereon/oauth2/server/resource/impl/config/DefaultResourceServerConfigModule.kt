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

package com.sphereon.oauth2.server.resource.impl.config

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthenticationScheme
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.resource.model.TokenValidationStrategy
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Default Resource Server configuration providers.
 *
 * These provide default values for resource server configuration parameters.
 * In production, these should be overridden with actual configuration values.
 *
 * **Configuration Points:**
 * - `resourceServer.identifier` - The resource server identifier (for audience validation)
 * - `resourceServer.authorizationServers` - List of trusted authorization server URLs
 * - `resourceServer.clientAuthentication` - Client authentication for token introspection (optional)
 * - `resourceServer.tokenValidationStrategy` - Token validation strategy (JWT_FIRST, JWT_ONLY, INTROSPECTION_ONLY)
 * - `resourceServer.allowedAuthenticationSchemes` - Allowed authentication schemes (Bearer, DPoP)
 * - `resourceServer.tokenCacheTtl` - Token cache TTL
 * - `resourceServer.dpopReplayWindow` - DPoP replay protection window
 * - `resourceServer.dpopNonceTtl` - DPoP nonce cache TTL
 * - `resourceServer.requireAudienceValidation` - Whether to require audience validation
 * - `resourceServer.clockSkewTolerance` - Clock skew tolerance
 *
 * **Override Example:**
 * ```kotlin
 * @ContributesTo(SessionScope::class)
 * interface ProductionResourceServerConfigModule {
 *     @Provides
 *     @SingleIn(SessionScope::class)
 *     @Named("resourceServer.identifier")
 *     fun provideResourceServerIdentifier(): String {
 *         return "https://api.example.com"
 *     }
 *
 *     @Provides
 *     @SingleIn(SessionScope::class)
 *     @Named("resourceServer.authorizationServers")
 *     fun provideAuthorizationServers(): List<String> {
 *         return listOf("https://auth.example.com")
 *     }
 *
 *     @Provides
 *     @SingleIn(SessionScope::class)
 *     @Named("resourceServer.clientAuthentication")
 *     fun provideClientAuthentication(): ClientAuthenticationConfig {
 *         return ClientAuthenticationConfig.ClientSecretBasic(
 *             clientId = "resource-server-client-id",
 *             clientSecret = "resource-server-client-secret"
 *         )
 *     }
 * }
 * ```
 */
@ContributesTo(SessionScope::class)
interface DefaultResourceServerConfigModule {
    /**
     * Default resource server identifier.
     * Used for audience validation.
     *
     * **MUST be overridden in production.**
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.identifier")
    fun provideDefaultResourceServerIdentifier(): String = "http://localhost:8080"

    /**
     * Default list of trusted authorization servers.
     * Resource server will only accept tokens from these issuers.
     *
     * **MUST be overridden in production.**
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.authorizationServers")
    fun provideDefaultAuthorizationServers(): List<String> = listOf("http://localhost:8080")

    /**
     * Default client authentication configuration for token introspection.
     *
     * When null, token introspection will not be available.
     * To enable introspection, override this provider with actual credentials.
     *
     * **Override in production to enable token introspection.**
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.clientAuthentication")
    fun provideDefaultClientAuthentication(): ClientAuthenticationConfig? = null

    /**
     * Default token validation strategy.
     * JWT_FIRST: Try JWT verification first, fall back to introspection.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.tokenValidationStrategy")
    fun provideDefaultTokenValidationStrategy(): TokenValidationStrategy = TokenValidationStrategy.JWT_FIRST

    /**
     * Default allowed authentication schemes.
     * Both Bearer and DPoP are allowed by default.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.allowedAuthenticationSchemes")
    fun provideDefaultAllowedAuthenticationSchemes(): List<AuthenticationScheme> = listOf(AuthenticationScheme.BEARER, AuthenticationScheme.DPOP)

    /**
     * Default token cache TTL (1 hour).
     * Tokens will be cached up to their expiration time, with this as maximum.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.tokenCacheTtl")
    fun provideDefaultTokenCacheTtl(): Duration = 1.hours

    /**
     * Default DPoP replay protection window (1 minute).
     * DPoP proofs older than this will be rejected.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.dpopReplayWindow")
    fun provideDefaultDpopReplayWindow(): Duration = 1.minutes

    /**
     * Default DPoP nonce cache TTL (5 minutes).
     * Used JTIs will be remembered for this duration to prevent replay.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.dpopNonceTtl")
    fun provideDefaultDpopNonceTtl(): Duration = 5.minutes

    /**
     * Default audience validation requirement (true).
     * When true, tokens must contain the resource server identifier in their audience.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.requireAudienceValidation")
    fun provideDefaultRequireAudienceValidation(): Boolean = true

    /**
     * Default clock skew tolerance (0 seconds).
     * No clock skew is tolerated by default for maximum security.
     */
    @Provides
    @SingleIn(SessionScope::class)
    @Named("resourceServer.clockSkewTolerance")
    fun provideDefaultClockSkewTolerance(): Duration = Duration.ZERO
}
