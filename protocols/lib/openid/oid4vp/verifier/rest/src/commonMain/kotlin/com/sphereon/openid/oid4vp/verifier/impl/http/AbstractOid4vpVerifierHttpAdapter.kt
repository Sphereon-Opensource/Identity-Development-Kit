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

package com.sphereon.openid.oid4vp.verifier.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.command.ResolvedHttpRequest
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceResolver

/**
 * Shared request boundary for OID4VP verifier adapters. Resolves the active verifier instance once,
 * publishes it for every downstream session-scoped collaborator, and always clears it after the
 * dispatch. A null result is the intentional singular-verifier namespace; persistence code assigns
 * that deployment the canonical `default` identity via `currentInstanceIdOrDefault()`.
 */
abstract class AbstractOid4vpVerifierHttpAdapter(
    id: String,
    execution: SessionExecution,
    mount: HttpAdapterMount,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
    private val verifierInstanceResolver: Oid4vpVerifierInstanceResolver,
    private val verifierInstanceIdProvider: MutableOid4vpVerifierInstanceIdProvider,
) : CommandBackedHttpAdapter(
        id = id,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount = mount,
    ) {
    override suspend fun doExecute(
        args: ResolvedHttpRequest,
        applyDuring: (ResolvedHttpRequest) -> ResolvedHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val resolution = verifierInstanceResolver.resolve(args.request)
        if (resolution.isErr) return Err(resolution.error)

        val instanceId = resolution.value?.trim()?.takeIf(String::isNotEmpty)
        if (instanceId == null) {
            verifierInstanceIdProvider.clearCurrentInstanceId()
        } else {
            verifierInstanceIdProvider.setCurrentInstanceId(instanceId)
        }
        return try {
            super.doExecute(args, applyDuring)
        } finally {
            verifierInstanceIdProvider.clearCurrentInstanceId()
        }
    }
}
