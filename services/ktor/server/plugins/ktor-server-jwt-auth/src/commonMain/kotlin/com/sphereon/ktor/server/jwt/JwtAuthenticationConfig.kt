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
 *
 */

package com.sphereon.ktor.server.jwt

import com.sphereon.di.context.IdentityResolutionPipeline
import com.sphereon.di.session.SessionContextFactory
import com.sphereon.ktor.server.inject.getAppService
import com.sphereon.ktor.server.inject.getSessionService
import com.sphereon.oauth2.jwt.validation.JwtValidationService
import io.ktor.server.application.ApplicationCall

/**
 * Configuration for [JwtAuthentication].
 *
 * The three IDK services the plugin needs are resolved per request through
 * lambdas. The default lambdas read the services from the graphs that
 * [com.sphereon.ktor.server.inject.KotlinInjectPlugin] installs on each call,
 * so the common case requires no wiring beyond:
 *
 * ```
 * install(KotlinInjectPlugin) { appGraph = myAppGraph }
 * install(JwtAuthentication)  { requireAuth = true }
 * ```
 *
 * [com.sphereon.ktor.server.inject.KotlinInjectPlugin] must be installed
 * before [JwtAuthentication] so that the per-request session graph is
 * attached to the call by the time the JWT plugin's `onCall` handler runs.
 *
 * Consumers that do not use `KotlinInjectPlugin` (pure Koin, hand-wired, or
 * framework-less deployments) override the lambdas with their own resolver
 * logic. The plugin itself depends on neither Metro nor kotlin-inject.
 *
 * Scope-aware defaults:
 * - [JwtValidationService] is SessionScope in IDK, so it is resolved via
 *   `call.getSessionService<JwtValidationService>()`. Resolving per call means
 *   each request gets a fresh session-scoped validator, which is required
 *   because the validator's transitive dependency chain
 *   (`VerifyJwtCommand` -> `JwtService` -> `VerifyJwsCommand` -> `IdentifierService`)
 *   needs a live `SessionExecution`.
 * - [IdentityResolutionPipeline] is AppScope, so it is resolved via
 *   `call.getAppService<IdentityResolutionPipeline>()`.
 * - [SessionContextFactory] is AppScope, so it is resolved via
 *   `call.getAppService<SessionContextFactory>()`.
 *
 * Single-IdP scope: the plugin validates whatever the bound
 * [JwtValidationService] accepts. Multi-IdP routing belongs to EDK.
 */
class JwtAuthenticationConfig {
    /**
     * Resolves the JWT validation service for the current request.
     *
     * Defaults to `call.getSessionService<JwtValidationService>()`, which
     * pulls the session-scoped validator out of the session graph that
     * [com.sphereon.ktor.server.inject.KotlinInjectPlugin] builds per call.
     *
     * Override when not using the kotlin-inject plugin (Koin, manual wiring,
     * tests).
     */
    var jwtValidationService: (ApplicationCall) -> JwtValidationService = { call ->
        call.getSessionService<JwtValidationService>()
    }

    /**
     * Resolves the identity resolution pipeline for the current request.
     *
     * Defaults to `call.getAppService<IdentityResolutionPipeline>()` because
     * IDK's `DefaultIdentityResolutionPipeline` (and EDK's replacement) are
     * AppScope.
     *
     * Override when not using the kotlin-inject plugin.
     */
    var identityResolutionPipeline: (ApplicationCall) -> IdentityResolutionPipeline = { call ->
        call.getAppService<IdentityResolutionPipeline>()
    }

    /**
     * Resolves the session-context factory for the current request.
     *
     * Defaults to `call.getAppService<SessionContextFactory>()` because IDK's
     * `DefaultSessionContextFactory` is AppScope.
     *
     * Override when not using the kotlin-inject plugin.
     */
    var sessionContextFactory: (ApplicationCall) -> SessionContextFactory = { call ->
        call.getAppService<SessionContextFactory>()
    }

    /**
     * When `true` (default), requests without a valid token to a non-anonymous
     * path receive a 401 response. When `false`, the plugin still resolves an
     * (anonymous) session context and lets the request continue; downstream
     * authorization rules then decide whether to serve the request.
     */
    var requireAuth: Boolean = true

    /**
     * Glob-style path patterns that bypass token validation entirely.
     *
     * Supported syntax:
     * - Exact match: `/health`
     * - Single-segment wildcard: `/api/v1/foo-star` (matches `/api/v1/anything`, no slashes)
     * - Prefix wildcard: `/api/v1/public/double-star` (matches any nested path)
     */
    var anonymousPaths: List<String> = emptyList()

    /**
     * Optional cookie name from which to read the bearer token when the
     * `Authorization` header is absent. Null disables cookie-based extraction.
     */
    var cookieName: String? = null

    /**
     * Optional expected-audience override passed to
     * [com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions.expectedAudience].
     * When `null`, the audience bound to the resolved IdP is used.
     */
    var expectedAudience: String? = null
}
