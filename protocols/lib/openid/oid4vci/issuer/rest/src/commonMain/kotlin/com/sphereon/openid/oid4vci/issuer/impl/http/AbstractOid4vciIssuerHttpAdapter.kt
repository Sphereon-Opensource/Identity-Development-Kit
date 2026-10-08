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

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.command.ResolvedHttpRequest
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.openid.oid4vci.issuer.config.MutableOid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceResolver
import com.sphereon.openid.oid4vci.issuer.config.requireCanonicalOid4vciIssuerInstanceId
import com.sphereon.openid.oid4vci.issuer.impl.http.command.Oid4vciErrorRenderer

/**
 * Shared request boundary for OID4VCI issuer adapters. Resolves the active issuer instance once,
 * publishes it for every downstream session-scoped collaborator, and always clears it after the
 * dispatch. Missing, invalid, alias, and slug selectors fail closed before endpoint execution.
 */
abstract class AbstractOid4vciIssuerHttpAdapter(
    id: String,
    execution: SessionExecution,
    mount: HttpAdapterMount,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
    private val issuerInstanceResolver: Oid4vciIssuerInstanceResolver,
    private val issuerInstanceIdProvider: MutableOid4vciIssuerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    tenantPathPolicy: TenantPathPolicy,
) : CommandBackedHttpAdapter(
        id = id,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount = mount,
        tenantPathPolicy = tenantPathPolicy,
        errorRenderer = Oid4vciErrorRenderer(),
    ) {
    override val routableSlugLookup: RoutableSlugLookup = slugLookup
    override val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = tenantIdProvider

    override suspend fun doExecute(
        args: ResolvedHttpRequest,
        applyDuring: (ResolvedHttpRequest) -> ResolvedHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val resolution = issuerInstanceResolver.resolve(args.request)
        if (resolution.isErr) return Err(resolution.error)

        val instanceId =
            try {
                requireCanonicalOid4vciIssuerInstanceId(resolution.value)
            } catch (error: IllegalArgumentException) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = error.message ?: "Invalid OID4VCI issuer instance selector"))
            }
        issuerInstanceIdProvider.setCurrentInstanceId(instanceId)
        return try {
            super.doExecute(args, applyDuring)
        } finally {
            issuerInstanceIdProvider.clearCurrentInstanceId()
        }
    }
}
