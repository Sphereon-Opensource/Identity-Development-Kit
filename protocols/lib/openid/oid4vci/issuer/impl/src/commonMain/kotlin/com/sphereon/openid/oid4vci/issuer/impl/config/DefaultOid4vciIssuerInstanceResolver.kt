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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceResolver
import com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vci.issuer.config.requireCanonicalOid4vciIssuerInstanceId
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Fail-closed IDK implementation used when no tenant-aware resource resolver is installed.
 * Greenfield issuer routes require a persisted issuer resource UUID and must not silently activate
 * the retired singular `oid4vci.issuer.*` namespace.
 *
 * Multi-instance routing (request → persisted issuer party id under `oid4vci.issuers.<id>.*`) is a
 * higher-layer concern: EDK/VDX contribute a resolver that replaces this default binding.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOid4vciIssuerInstanceResolver(
    private val execution: SessionExecution,
) : Oid4vciIssuerInstanceResolver {
    override suspend fun resolve(request: GenericHttpRequest): IdkResult<String?, IdkError> = runCatching {
        val config = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService
        val instanceId = requireCanonicalOid4vciIssuerInstanceId(config.getPropertyAsString(ROUTED_ISSUER_RESOURCE_ID))
        val identifier = config.getPropertyAsString("$INSTANCES_NAMESPACE.$instanceId.identifier")
            ?.takeIf { it.isNotBlank() }
            ?: error("The routed OID4VCI issuer resource '$instanceId' does not exist")
        require(request.requestUrl().startsWith(identifier.trimEnd('/'))) {
            "The request URL is not owned by routed OID4VCI issuer resource '$instanceId'"
        }
        instanceId
    }.fold(
        onSuccess = { Ok(it) },
        onFailure = { Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = it.message ?: "OID4VCI issuer routing failed")) },
    )

    private fun GenericHttpRequest.requestUrl(): String {
        val proto = headers["x-forwarded-proto"] ?: headers["X-Forwarded-Proto"]
        val scheme = if (proto.equals("https", ignoreCase = true)) "https" else "http"
        val host = headers["host"] ?: headers["Host"] ?: "localhost"
        return "$scheme://$host$path"
    }

    private companion object {
        const val ROUTED_ISSUER_RESOURCE_ID = "oid4vci.routing.issuerResourceId"
    }
}
