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

package com.sphereon.did.rest.resolver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/*
 * Endpoint commands for the DIF Universal Resolver REST API.
 *
 * Implements the DIF Universal Resolver HTTP API:
 * - GET /identifiers/{identifier} - Resolve a DID
 * - GET /methods - List supported DID methods
 * - GET /properties - Get resolver properties
 *
 * Note: These are relative paths. The /1.0 base path is configured on the adapter.
 *
 * @see <a href="https://github.com/decentralized-identity/universal-resolver/blob/main/openapi/openapi.yaml">DIF Universal Resolver OpenAPI</a>
 */

// ========== Resolve DID Endpoint ==========

/**
 * Command interface for resolving a DID to its DID Document.
 */
interface ResolveDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "did.resolver.resolve"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{identifier}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "resolve",
                handlerCommandId = COMMAND_ID,
                tags = setOf("universal-resolver"),
                summary = "Resolve a DID to its DID Document",
            )
    }
}

/**
 * Implementation of [ResolveDidEndpointCommand].
 *
 * GET /identifiers/{identifier}
 *
 * Resolves the given DID and returns a DID Resolution Result containing:
 * - The DID Document (if resolution succeeded)
 * - Resolution metadata (content type, error codes, etc.)
 * - Document metadata (created, updated, deactivated, etc.)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ResolveDidEndpointCommand.COMMAND_ID)
class ResolveDidEndpointCommandImpl(
    execution: SessionExecution,
    private val resolverRegistry: DidResolverRegistry,
) : HttpEndpointCommandAdapter(
        id = ResolveDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ResolveDidEndpointCommand.ENDPOINT,
    ),
    ResolveDidEndpointCommand {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/identifiers/{identifier}")
        val did = req.requirePathParam("identifier").getOrElse { return Err(it) }

        // Parse Accept header for content negotiation
        val accept = req.headers["Accept"]

        // Extract DID method
        val method =
            extractMethod(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        // Get resolver for this method
        val resolver =
            resolverRegistry.getResolver(method)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Unsupported DID method: $method"))

        // Resolve the DID
        val options = DidResolutionOptions(accept = accept)
        val result = resolver.resolve(did, options)

        return result.fold(
            success = { resolutionResult ->
                Ok(jsonResponse(200, json.encodeToString(resolutionResult.toResponse())))
            },
            failure = { error ->
                Err(IdkError.UNKNOWN_ERROR(message = error.message.defaultMessage))
            },
        )
    }

    private fun extractMethod(did: String): String? {
        val parts = did.split(":")
        return if (parts.size >= 2 && parts[0] == "did") {
            parts[1]
        } else {
            null
        }
    }
}

// ========== List Methods Endpoint ==========

/**
 * Command interface for listing supported DID methods.
 */
interface GetResolverMethodsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "did.resolver.methods"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/methods",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getMethods",
                handlerCommandId = COMMAND_ID,
                tags = setOf("universal-resolver"),
                summary = "Get list of supported DID methods",
            )
    }
}

/**
 * Implementation of [GetResolverMethodsEndpointCommand].
 *
 * GET /methods
 *
 * Returns an array of supported DID method identifiers.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetResolverMethodsEndpointCommand.COMMAND_ID)
class GetResolverMethodsEndpointCommandImpl(
    execution: SessionExecution,
    private val resolverRegistry: DidResolverRegistry,
) : HttpEndpointCommandAdapter(
        id = GetResolverMethodsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetResolverMethodsEndpointCommand.ENDPOINT,
    ),
    GetResolverMethodsEndpointCommand {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val methods = resolverRegistry.getSupportedMethods()
        return Ok(jsonResponse(200, json.encodeToString(methods)))
    }
}

// ========== Get Properties Endpoint ==========

/**
 * Command interface for getting resolver properties.
 */
interface GetResolverPropertiesEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "did.resolver.properties"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/properties",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getProperties",
                handlerCommandId = COMMAND_ID,
                tags = setOf("universal-resolver"),
                summary = "Get resolver properties and capabilities",
            )
    }
}

/**
 * Implementation of [GetResolverPropertiesEndpointCommand].
 *
 * GET /properties
 *
 * Returns resolver properties including supported methods and their capabilities.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetResolverPropertiesEndpointCommand.COMMAND_ID)
class GetResolverPropertiesEndpointCommandImpl(
    execution: SessionExecution,
    private val resolverRegistry: DidResolverRegistry,
) : HttpEndpointCommandAdapter(
        id = GetResolverPropertiesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetResolverPropertiesEndpointCommand.ENDPOINT,
    ),
    GetResolverPropertiesEndpointCommand {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val methods = resolverRegistry.getSupportedMethods()
        val methodCapabilities =
            methods.associateWith { method ->
                resolverRegistry.getResolver(method)?.capabilities
            }

        val properties =
            ResolverPropertiesResponse(
                methods = methods,
                methodCapabilities =
                    methodCapabilities
                        .filterValues { it != null }
                        .mapValues { (_, caps) -> caps!! },
            )

        return Ok(jsonResponse(200, json.encodeToString(properties)))
    }
}
