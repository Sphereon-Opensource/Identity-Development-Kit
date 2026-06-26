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

package com.sphereon.core.defaults.session

import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [SessionContextFactory].
 *
 * Maps an [IdentityResolutionResult] into a plain [SessionContext], falling
 * back to [IdentityConstants.ANONYMOUS_TENANT_ID] /
 * [IdentityConstants.ANONYMOUS_PRINCIPAL_ID] when the resolution did not
 * produce a tenant or principal. Intentionally free of tracing, metrics,
 * and policy enforcement so that IDK-only deployments and IDK tests can
 * construct a session context without pulling in higher-layer concerns.
 *
 * EDK and VDX layer their own behaviour on top:
 * - VDX's `com.sphereon.vdx.transport.server.DefaultSessionContextFactory`
 *   provides a richer, transport-scoped factory with secure details,
 *   trace/correlation ids, and per-request metadata. Its interface now
 *   extends this IDK [SessionContextFactory] and its Metro binding
 *   declares `replaces = [DefaultSessionContextFactory::class]`, so in
 *   deployments that include VDX transport-server on the classpath the
 *   VDX factory wins for both the minimal IDK contract and the richer
 *   transport overload. This IDK default remains the active binding for
 *   pure-IDK deployments.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SessionContextFactory>())
class DefaultSessionContextFactory : SessionContextFactory {
    override fun create(
        sessionId: String,
        correlationId: String,
        resolution: IdentityResolutionResult,
        metadata: Map<String, Any>,
    ): SessionContext {
        val resolvedTenantId = resolution.tenantId ?: IdentityConstants.ANONYMOUS_TENANT_ID
        val resolvedPrincipalId = resolution.principalId ?: IdentityConstants.ANONYMOUS_PRINCIPAL_ID

        val userContext =
            DefaultUserContext(
                id = "$resolvedTenantId:$resolvedPrincipalId:default",
                tenant = DefaultTenantContextData(resolvedTenantId),
                principal = resolvedPrincipalId,
                principalType = resolution.principalType,
            )

        return DefaultSessionContext(
            sessionId = sessionId,
            context = userContext,
            correlationId = correlationId,
        )
    }
}

internal data class DefaultTenantContextData(
    override val tenantId: String,
) : TenantContextData

internal data class DefaultUserContext(
    override val id: String,
    override val tenant: TenantContextData,
    override val principal: Any?,
    override val secureDetails: SecuredTenantContextDetails? = null,
    override val principalType: PrincipalType = PrincipalType.USER,
) : UserContext

internal data class DefaultSessionContext(
    override val sessionId: String,
    override val context: UserContext,
    override val correlationId: String,
) : SessionContext
