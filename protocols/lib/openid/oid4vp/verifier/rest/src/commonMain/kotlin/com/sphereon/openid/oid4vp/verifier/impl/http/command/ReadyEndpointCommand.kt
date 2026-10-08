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

package com.sphereon.openid.oid4vp.verifier.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Readiness endpoint — validates that the verifier is actually serviceable, not just alive.
 *
 * `/health` says "the process is up". `/ready` says "the next authorization request will
 * actually work". It exercises the request-object signing config so misconfigurations
 * (wrong key alias, missing DID provider, absent x5c chain, invalid mode) surface at the
 * probe rather than on the first wallet-facing request.
 */
interface ReadyEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.verifier.ready"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/ready",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "readinessProbe",
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vp", "health"),
                summary = "Readiness probe — validates signing configuration",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ReadyEndpointCommand.COMMAND_ID)
class ReadyEndpointCommandImpl(
    execution: SessionExecution,
    private val signingConfig: RequestObjectSigningConfig,
) : HttpEndpointCommandAdapter(
        id = ReadyEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ReadyEndpointCommand.ENDPOINT,
    ),
    ReadyEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val checks = mutableListOf<Pair<String, String>>()
        var allOk = true

        // Always reachable checks
        checks += "process" to "up"

        // Signing config: only meaningful to validate when signing is enabled. When it is,
        // failure to resolve the binding means every authorization request will 500 — we
        // want that to fail the probe, not the first wallet.
        if (signingConfig.enabled) {
            val bindingOutcome =
                try {
                    val binding = signingConfig.resolveSignerBinding()
                    if (binding == null) {
                        allOk = false
                        "null (check request-object.signing.mode)"
                    } else {
                        "${binding.scheme.name} → ${binding.clientId}"
                    }
                } catch (t: Throwable) {
                    allOk = false
                    "error: ${t.message ?: t::class.simpleName}"
                }
            checks += "request-object.signing.binding" to bindingOutcome
        } else {
            checks += "request-object.signing" to "disabled"
        }

        val statusCode = if (allOk) STATUS_OK else STATUS_SERVICE_UNAVAILABLE
        val status = if (allOk) "ready" else "not-ready"
        val body =
            buildString {
                append("{\"status\":\"").append(status).append("\",\"checks\":{")
                checks.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(',')
                    append('"')
                        .append(k)
                        .append("\":\"")
                        .append(v.replace("\"", "\\\""))
                        .append('"')
                }
                append("}}")
            }
        return Ok(
            GenericHttpResponse(
                statusCode = statusCode,
                headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                body = body,
            ),
        )
    }

    private companion object {
        const val STATUS_OK = 200
        const val STATUS_SERVICE_UNAVAILABLE = 503
    }
}
