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

package com.sphereon.oauth2.server.authorization.audit

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/**
 * Default DI graph providing [NoOpOAuth2AuditEmitter] for the [OAuth2AuditEmitter] SPI. Wired
 * so consumers without an audit-pipeline implementation still compile and run; production
 * deployments override the binding by contributing a graph that replaces this one (e.g. the
 * EDK `audit-impl` module ships a graph that bridges emissions onto the
 * [com.sphereon.edk.audit.AuditEventSink] pipeline).
 *
 * Lives in the `public` module rather than `impl` so EDK / VDX modules that need to reference
 * this graph for their `replaces = [...]` clause can do so without taking a hard dependency on
 * the IDK impl module (which would invert the public/impl boundary).
 *
 * The graph-replacement pattern mirrors the audit-sink default at
 * [com.sphereon.edk.audit.DefaultAuditEventSinkGraph], so downstream overrides use a uniform
 * `@ContributesTo(AppScope::class, replaces = [DefaultGraph::class])` shape.
 */
@ContributesTo(AppScope::class)
interface DefaultOAuth2AuditEmitterGraph {
    @Provides
    fun provideOAuth2AuditEmitter(): OAuth2AuditEmitter = NoOpOAuth2AuditEmitter
}
