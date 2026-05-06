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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import kotlinx.serialization.json.Json

/**
 * Shared base for the per-area OAuth2 AS [CommandBackedHttpAdapter] subclasses (discovery, token,
 * authorization, userinfo, federation, internal). Resolves the active authorization server
 * instance id from the request via [asInstanceResolver] and publishes it on
 * [asInstanceIdProvider] for the duration of [doExecute] so downstream session-scoped
 * collaborators (e.g. user authentication providers) can scope config under
 * `${OAuth2ServerInstanceConfig.CONFIG_PREFIX}.<asId>` without taking the id as a method
 * argument.
 *
 * On resolver failure the deployment config is broken, so the helper short-circuits with an
 * RFC 6749 §5.2 `server_error` (HTTP 500) instead of routing to a guessed AS.
 */
abstract class AbstractOAuth2HttpAdapter(
    id: String,
    execution: SessionExecution,
    mount: HttpAdapterMount,
    private val asInstanceResolver: OAuth2ServerInstanceResolver,
    private val asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    /**
     * Per RFC 8414 §3 / RFC 8615 the well-known endpoints place the issuer path
     * AS A SUFFIX after the well-known name (`/.well-known/openid-configuration/<issuer-path>`),
     * while the issuer-path-relative endpoints (authorize, token, par, jwks-at-issuer,
     * callbacks) put it as a leading prefix (`/<issuer-path>/authorize`).
     *
     * Concrete subclasses pass the right policy. `WellKnownSuffix(2)` for
     * discovery / federation entity statements; `LeadingSlug(2)` for the rest.
     * Default is `WellKnownSuffix(2)` because the most common AS adapter served
     * here is discovery — explicit subclasses for token/auth/par override.
     */
    tenantPathPolicy: TenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
) : CommandBackedHttpAdapter(
        id = id,
        execution = execution,
        mount = mount,
        tenantPathPolicy = tenantPathPolicy,
    ) {
    private val errorJson =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val resolution = asInstanceResolver.resolve(args)
        if (resolution.isErr) {
            return Ok(oauth2ErrorResponse(500, "server_error", resolution.error.message.defaultMessage, errorJson))
        }
        asInstanceIdProvider.setCurrentAsInstanceId(resolution.value)
        return try {
            super.doExecute(args, applyDuring).withSecurityHeadersOnSuccess()
        } finally {
            asInstanceIdProvider.clearCurrentAsInstanceId()
        }
    }

    /**
     * Apply [securityHeadersFor] to every successful response leaving the AS, choosing the
     * category from the response's `Content-Type`. Errors propagate as-is — the OAuth2 error
     * helpers already apply their own headers, so an early-return error response is still
     * defended.
     */
    private fun IdkResult<GenericHttpResponse, IdkError>.withSecurityHeadersOnSuccess(): IdkResult<GenericHttpResponse, IdkError> =
        if (isOk) {
            val category = value.responseCategoryFromContentType()
            Ok(value.withSecurityHeaders(category))
        } else {
            this
        }
}
