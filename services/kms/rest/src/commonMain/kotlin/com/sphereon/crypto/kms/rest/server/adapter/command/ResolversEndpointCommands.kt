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

package com.sphereon.crypto.kms.rest.server.adapter.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.kms.rest.api.generated.models.ListResolversResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvePublicKey
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvedKeyInfo
import com.sphereon.crypto.kms.rest.api.generated.models.Resolver
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toRestResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.adapter.JsonConfig
import com.sphereon.crypto.kms.rest.server.service.ResolversRestService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/*
 * Endpoint commands for the Resolvers API.
 *
 * These commands demonstrate the endpoint-as-command pattern where each endpoint
 * is a standalone injectable command with:
 * - Its own endpoint descriptor (for routing metadata)
 * - Lifecycle integration via ExecutionScopedCommandAdapter
 * - Enablement/feature gating via isEnabled
 * - DI injection - commands are injected, not instantiated
 *
 * **Reference Implementation**: This serves as the canonical example of how to
 * convert inline route handlers to endpoint commands for adapters that need
 * command infrastructure features.
 */

// ========== List Resolvers Endpoint ==========

/**
 * Command interface for listing all resolvers.
 */
interface ListResolversEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "kms.resolvers.list"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/resolvers",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listResolvers",
                tags = setOf("resolvers"),
                summary = "List all available resolvers",
            )
    }
}

/**
 * Implementation of [ListResolversEndpointCommand].
 *
 * GET /resolvers
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListResolversEndpointCommand>())
class ListResolversEndpointCommandImpl(
    execution: SessionExecution,
    private val resolversService: ResolversRestService,
) : HttpEndpointCommandAdapter(
        id = ListResolversEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListResolversEndpointCommand.ENDPOINT,
    ),
    ListResolversEndpointCommand {
    private val json: Json get() = JsonConfig.instance

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val resolvers = resolversService.listResolvers().map { it.toRest() }.toTypedArray()
        return Ok(jsonResponse(200, json.encodeToString<ListResolversResponse>(resolvers.toRestResponse())))
    }
}

// ========== Get Resolver Endpoint ==========

/**
 * Command interface for getting a specific resolver by ID.
 */
interface GetResolverEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "kms.resolvers.get"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/resolvers/{resolverId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getResolver",
                tags = setOf("resolvers"),
                summary = "Get resolver details by ID",
            )
    }
}

/**
 * Implementation of [GetResolverEndpointCommand].
 *
 * GET /resolvers/{resolverId}
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetResolverEndpointCommand>())
class GetResolverEndpointCommandImpl(
    execution: SessionExecution,
    private val resolversService: ResolversRestService,
) : HttpEndpointCommandAdapter(
        id = GetResolverEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetResolverEndpointCommand.ENDPOINT,
    ),
    GetResolverEndpointCommand {
    private val json: Json get() = JsonConfig.instance

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/resolvers/{resolverId}")
        val resolverId = req.requirePathParam("resolverId").getOrElse { return Err(it) }

        val resolver = resolversService.getResolver(resolverId)
        return Ok(jsonResponse(200, json.encodeToString<Resolver>(resolver.toRest())))
    }
}

// ========== Resolve Public Key Endpoint ==========

/**
 * Command interface for resolving a public key using a specific resolver.
 */
interface ResolvePublicKeyEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "kms.resolvers.resolve"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/resolvers/{resolverId}/resolve",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "resolvePublicKey",
                tags = setOf("resolvers"),
                summary = "Resolve a public key using the specified resolver",
            )
    }
}

/**
 * Implementation of [ResolvePublicKeyEndpointCommand].
 *
 * POST /resolvers/{resolverId}/resolve
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolvePublicKeyEndpointCommand>())
class ResolvePublicKeyEndpointCommandImpl(
    execution: SessionExecution,
    private val resolversService: ResolversRestService,
) : HttpEndpointCommandAdapter(
        id = ResolvePublicKeyEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ResolvePublicKeyEndpointCommand.ENDPOINT,
    ),
    ResolvePublicKeyEndpointCommand {
    private val json: Json get() = JsonConfig.instance

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/resolvers/{resolverId}/resolve")
        val resolverId = req.requirePathParam("resolverId").getOrElse { return Err(it) }
        val resolveRequest = req.requireJsonBody<ResolvePublicKey>(json).getOrElse { return Err(it) }

        val resolvedKeyInfo =
            resolversService.resolveKey(
                resolverId = resolverId,
                keyInfo = resolveRequest.keyInfo.toSdk(),
                identifierMethod = resolveRequest.identifierMethod?.let { IdentifierMethod.valueOf(it.value) },
                trustedCerts = resolveRequest.trustedCerts,
                verifyX509CertificateChain = resolveRequest.verifyX509CertificateChain,
            )
        return Ok(jsonResponse(200, json.encodeToString<ResolvedKeyInfo>(resolvedKeyInfo.toRest())))
    }
}
