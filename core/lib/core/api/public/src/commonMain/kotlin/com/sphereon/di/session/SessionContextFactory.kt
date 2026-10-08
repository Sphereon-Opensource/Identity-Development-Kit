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

package com.sphereon.di.session

import com.sphereon.di.context.IdentityResolutionResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Imperative entry point for building a [SessionContext] from an
 * [IdentityResolutionResult].
 *
 * This SPI complements the DI-graph-based construction path (see
 * `SessionContextImpl`, which receives `UserContext` + `@Named("sessionId")`
 * through the `SessionGraph`). Use this factory when a caller — for example
 * the Ktor authentication plugin — already holds a concrete resolution
 * result and needs a [SessionContext] without first opening a session graph.
 *
 * The IDK default implementation is deliberately minimal: it maps
 * `tenantId` / `principalId` (falling back to the anonymous constants when
 * null) and wraps them in a plain [SessionContext]. Downstream layers can
 * replace the binding to add tracing, metrics, policy enforcement, or
 * richer transport metadata.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextFactory", exact = true)
interface SessionContextFactory {
    /**
     * Build a [SessionContext] from the supplied session identifier and
     * identity resolution outcome.
     *
     * @param sessionId Caller-supplied session identifier. The factory does
     *   not generate one; callers that want a random id should pass it in.
     * @param correlationId Cross-cutting trace key for the business
     *   operation this session belongs to. Callers must supply one — typical
     *   sources are HTTP `X-Correlation-Id`, a parent execution's
     *   correlationId, the stored correlationId on a durable row being
     *   replayed, or the session id itself when no business correlation
     *   applies.
     * @param resolution The resolved tenant, principal, and metadata.
     * @param metadata Free-form metadata the caller wants attached to the
     *   resulting context. The default IDK implementation ignores this;
     *   richer implementations (e.g. the VDX transport factory) may surface
     *   it through a transport-specific [com.sphereon.di.context.UserContext].
     */
    fun create(
        sessionId: String,
        correlationId: String = sessionId,
        resolution: IdentityResolutionResult,
        metadata: Map<String, Any> = emptyMap(),
    ): SessionContext
}
