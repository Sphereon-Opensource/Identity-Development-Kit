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

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.crypto.core.kms.GetAllCapabilitiesArgs
import com.sphereon.crypto.core.kms.GetAllCapabilitiesCommand
import com.sphereon.crypto.core.kms.GetAllCapabilitiesResult
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ProviderMatch
import com.sphereon.crypto.core.kms.QueryProviderArgs
import com.sphereon.crypto.core.kms.QueryProviderCommand
import com.sphereon.crypto.core.kms.QueryProviderResult
import com.sphereon.crypto.core.kms.QueryProvidersArgs
import com.sphereon.crypto.core.kms.QueryProvidersCommand
import com.sphereon.crypto.core.kms.QueryProvidersResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of QueryProviderCommand.
 * Queries for a single KMS provider matching the capability criteria.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<QueryProviderCommand>())
class QueryProviderCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : ExecutionScopedCommandAdapter<QueryProviderArgs, QueryProviderResult, IdkError>(
        id = QueryProviderCommand.COMMAND_ID,
        execution = execution,
    ),
    QueryProviderCommand {
    override val id: String = QueryProviderCommand.COMMAND_ID

    override suspend fun doExecute(
        args: QueryProviderArgs,
        applyDuring: (QueryProviderArgs) -> QueryProviderArgs,
    ): IdkResult<QueryProviderResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val query =
            appliedArgs.query
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "query is required").asErrorResult()

        log.debug("Querying for KMS provider with criteria: $query")

        return try {
            // Query providers directly by iterating and checking capabilities
            val providerIds = providerRegistry.getProviderIds()
            val matchingProvider =
                providerIds
                    .asSequence()
                    .map { providerRegistry.getProviderById(it) }
                    .firstOrNull { provider -> query.matches(provider.getCapabilities()) }

            if (matchingProvider == null) {
                log.warn("No provider found matching query: $query")
                return IdkError
                    .NOT_FOUND_ERROR(
                        resource = "KMS provider matching query criteria",
                        message = "No provider found matching: $query",
                    ).asErrorResult()
            }

            val capabilities = matchingProvider.getCapabilities()
            log.debug("Found matching provider: ${matchingProvider.id} (${capabilities.providerType})")

            // Return provider ID + capabilities, NOT the provider instance
            QueryProviderResult(
                match =
                    ProviderMatch(
                        providerId = matchingProvider.id,
                        capabilities = capabilities,
                        matchScore = 100,
                    ),
            ).asOkResult()
        } catch (expected: Exception) {
            log.warn("Error querying providers: ${expected.message}")
            IdkError
                .NOT_FOUND_ERROR(
                    resource = "KMS provider matching query criteria",
                    message = expected.message ?: "No provider found",
                ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is QueryProviderArgs && args.query != null
}

/**
 * Implementation of QueryProvidersCommand.
 * Queries for all KMS providers matching the capability criteria.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<QueryProvidersCommand>())
class QueryProvidersCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : ExecutionScopedCommandAdapter<QueryProvidersArgs, QueryProvidersResult, IdkError>(
        id = QueryProvidersCommand.COMMAND_ID,
        execution = execution,
    ),
    QueryProvidersCommand {
    override val id: String = QueryProvidersCommand.COMMAND_ID

    override suspend fun doExecute(
        args: QueryProvidersArgs,
        applyDuring: (QueryProvidersArgs) -> QueryProvidersArgs,
    ): IdkResult<QueryProvidersResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val query =
            appliedArgs.query
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "query is required").asErrorResult()

        log.debug("Querying for all KMS providers matching criteria: $query")

        return try {
            val allProviderIds = providerRegistry.getProviderIds()
            val matchingProviders =
                allProviderIds
                    .map { providerRegistry.getProviderById(it) }
                    .filter { provider -> query.matches(provider.getCapabilities()) }

            log.debug("Found ${matchingProviders.size} of ${allProviderIds.size} providers matching query")

            // Convert provider instances to ProviderMatch (ID + capabilities)
            val matches =
                matchingProviders
                    .map { provider ->
                        ProviderMatch(
                            providerId = provider.id,
                            capabilities = provider.getCapabilities(),
                            matchScore = 100,
                        )
                    }.toTypedArray()

            QueryProvidersResult(
                matches = matches,
                totalProviders = allProviderIds.size,
            ).asOkResult()
        } catch (expected: Exception) {
            log.error("Error querying providers: ${expected.message}", expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Error querying KMS providers: ${expected.message}",
                ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is QueryProvidersArgs && args.query != null
}

/**
 * Implementation of GetAllCapabilitiesCommand.
 * Returns all registered providers' capabilities.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetAllCapabilitiesCommand>())
class GetAllCapabilitiesCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : ExecutionScopedCommandAdapter<GetAllCapabilitiesArgs, GetAllCapabilitiesResult, IdkError>(
        id = GetAllCapabilitiesCommand.COMMAND_ID,
        execution = execution,
    ),
    GetAllCapabilitiesCommand {
    override val id: String = GetAllCapabilitiesCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetAllCapabilitiesArgs,
        applyDuring: (GetAllCapabilitiesArgs) -> GetAllCapabilitiesArgs,
    ): IdkResult<GetAllCapabilitiesResult, IdkError> {
        val appliedArgs = applyDuring(args)
        log.debug("Getting all provider capabilities (includeDisabled: ${appliedArgs.includeDisabled})")

        return try {
            val providerIds = providerRegistry.getProviderIds()
            val capabilitiesMap =
                providerIds
                    .mapNotNull { id ->
                        val provider = providerRegistry.getProviderById(id)
                        val capabilities = provider.getCapabilities()

                        // Filter based on enabled status if requested
                        if (!appliedArgs.includeDisabled && !provider.enabled) {
                            null
                        } else {
                            id to capabilities
                        }
                    }.toMap()

            log.debug("Retrieved capabilities for ${capabilitiesMap.size} providers")

            GetAllCapabilitiesResult(
                capabilities = capabilitiesMap,
            ).asOkResult()
        } catch (expected: Exception) {
            log.error("Error getting provider capabilities: ${expected.message}", expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Error retrieving provider capabilities: ${expected.message}",
                ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is GetAllCapabilitiesArgs
}

/**
 * Graph interface for DI graph integration.
 * Allows other components to access the KMS query commands from the session scope.
 */
@ContributesTo(SessionScope::class)
interface KmsQueryCommandsGraph {
    val queryProviderCommand: QueryProviderCommand
    val queryProvidersCommand: QueryProvidersCommand
    val getAllCapabilitiesCommand: GetAllCapabilitiesCommand
}
