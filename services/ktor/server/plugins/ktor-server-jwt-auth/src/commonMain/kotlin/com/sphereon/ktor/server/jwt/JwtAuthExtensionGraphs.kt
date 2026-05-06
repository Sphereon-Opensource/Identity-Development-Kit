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
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.jwt.validation.JwtValidationService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Metro extension graph that publishes the session-scoped
 * [JwtValidationService] on every merged session graph.
 *
 * `DefaultJwtValidationService` is contributed in [SessionScope] because its
 * transitive dependency chain (`VerifyJwtCommand` -> `JwtService` ->
 * `VerifyJwsCommand` -> `IdentifierService`) needs a live `SessionExecution`.
 * The binding alone is not enough: Metro only exposes declared accessors on
 * the generated graph, so reflection-based service lookup
 * (`CallExtensions.getSessionService`) cannot find it without this interface.
 *
 * Contributing the interface here means every service that depends on
 * `ktor-server-jwt-auth` automatically gets the accessor on its session
 * graph; the `JwtAuthentication` plugin's default resolver can then pull the
 * validator out per request with no per-deployment wiring.
 *
 * [com.sphereon.di.session.SessionGraph] is a `@GraphExtension`, so
 * `asContribution<...>()` cannot be used; callers fetch the accessor
 * transparently through `call.getSessionService<JwtValidationService>()`.
 */
@SingleIn(SessionScope::class)
@ContributesTo(SessionScope::class)
interface JwtAuthSessionExtensionGraph {
    /**
     * Per-request session-scoped JWT validation service. Resolved fresh on
     * every call so each request gets a live session execution.
     */
    val jwtValidationService: JwtValidationService
}

/**
 * Metro extension graph that publishes the app-scoped
 * [IdentityResolutionPipeline] and [SessionContextFactory] on every merged
 * app graph.
 *
 * Both services are AppScope (see `DefaultIdentityResolutionPipeline` and
 * `DefaultSessionContextFactory` in `lib-core-api-default`). Contributing
 * accessors here lets the reflection-based
 * `CallExtensions.getAppService<T>()` locate them without requiring every
 * deployment to declare bespoke extension graphs. EDK/VDX replacements of
 * these bindings are honoured automatically: Metro resolves the contributed
 * accessor against whichever binding won `replaces`.
 */
@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface JwtAuthAppExtensionGraph {
    /**
     * App-scoped identity resolution pipeline. IDK supplies a default chain;
     * EDK replaces it with a security-aware implementation.
     */
    val identityResolutionPipeline: IdentityResolutionPipeline

    /**
     * App-scoped session-context factory. IDK supplies a minimal default;
     * VDX's transport-aware factory replaces it in platform deployments.
     */
    val sessionContextFactory: SessionContextFactory
}
