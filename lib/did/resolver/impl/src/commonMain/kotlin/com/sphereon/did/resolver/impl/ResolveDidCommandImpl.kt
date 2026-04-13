/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.did.resolver.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.did.resolver.DidResolutionCache
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.ResolveDidArgs
import com.sphereon.did.resolver.ResolveDidCommand
import com.sphereon.did.resolver.ResolveDidCommandService
import com.sphereon.did.utils.ParsedDid
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.seconds

/**
 * Command implementation for resolving DIDs.
 *
 * This command resolves a DID using registered method-specific resolvers
 * and optionally caches results for methods that support caching.
 *
 * Uses proper DI with interface injection.
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveDidCommandImpl(
    execution: SessionExecution,
    private val registry: DidResolverRegistry,
    private val cache: DidResolutionCache
) : TypedServiceCommandAdapter<ResolveDidArgs, DidResolutionResult>(
    commandId = ResolveDidCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ResolveDidArgs>(),
    outputTypeToken = typeToken<DidResolutionResult>(),
), ResolveDidCommand, ResolveDidCommandService {

    override val commandId: String get() = ResolveDidCommand.COMMAND_ID

    override suspend fun resolve(args: ResolveDidArgs): IdkResult<DidResolutionResult, IdkError> {
        return execute(args)
    }

    override suspend fun supports(args: Any): Boolean = args is ResolveDidArgs

    override suspend fun doExecute(
        args: ResolveDidArgs,
        applyDuring: (ResolveDidArgs) -> ResolveDidArgs
    ): IdkResult<DidResolutionResult, IdkError> {
        val processedArgs = applyDuring(args)
        val did = processedArgs.did
        val options = processedArgs.options

        log.debug("Resolving DID: ${did.take(50)}...")

        // Parse the DID to get the method
        val parsed = ParsedDid.parse(did)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val method = parsed.method

        // Check cache first (unless noCache is specified)
        if (!options.noCache) {
            val cached = cache.get(did)
            if (cached != null) {
                log.debug("Cache hit for DID: ${did.take(50)}")
                return Ok(applyFilter(cached, options))
            }
        }

        // Get the resolver for this method
        val resolver = registry.getResolver(method)
            ?: return Err(IdkError.fromString(
                message = "No resolver registered for DID method: $method",
                code = "UNSUPPORTED_OPERATION"
            ))

        // Resolve using the method-specific resolver
        val result = resolver.resolve(did, options).getOrElse {
            return Err(it)
        }

        // Cache the result if caching is allowed
        if (resolver.capabilities.resolution.allowsCaching) {
            val ttl = resolver.capabilities.resolution.cacheTtlSeconds?.seconds
            cache.put(did, result, ttl)
        }

        log.info("Resolved DID: ${did.take(50)} via $method resolver")

        return Ok(applyFilter(result, options))
    }

    /**
     * Applies filter options to a resolution result.
     */
    private fun applyFilter(
        result: DidResolutionResult,
        options: DidResolutionOptions
    ): DidResolutionResult {
        val filter = options.filter ?: return result

        // If no filtering requested, return as-is
        if (filter.purposes == null &&
            filter.keyTypes == null &&
            filter.verificationMethodTypes == null &&
            filter.kid == null &&
            filter.serviceId == null) {
            return result
        }

        val document = result.didDocument ?: return result

        // Filter verification methods by purpose
        val purposesFilter = filter.purposes
        val filteredVmByPurpose = if (purposesFilter != null) {
            result.verificationMethodsByPurpose.filterKeys { it in purposesFilter }
        } else {
            result.verificationMethodsByPurpose
        }

        // Further filter by key type if specified
        val keyTypesFilter = filter.keyTypes
        val afterKeyTypeFilter = if (keyTypesFilter != null) {
            filteredVmByPurpose.mapValues { (_, vms) ->
                vms.filter { vm ->
                    val jwk = vm.publicKeyJwk
                    jwk != null && keyTypesFilter.contains(getKeyType(jwk))
                }
            }
        } else {
            filteredVmByPurpose
        }

        // Filter by verification method type if specified
        val vmTypesFilter = filter.verificationMethodTypes
        val finalFilteredVmByPurpose = if (vmTypesFilter != null) {
            afterKeyTypeFilter.mapValues { (_, vms) ->
                vms.filter { vm ->
                    vmTypesFilter.contains(vm.type)
                }
            }
        } else {
            afterKeyTypeFilter
        }

        return result.copy(
            verificationMethodsByPurpose = finalFilteredVmByPurpose
        )
    }

    /**
     * Gets the key type string from a JWK.
     */
    private fun getKeyType(jwk: Jwk): String {
        return when (jwk.kty) {
            JwaKeyType.EC -> "EC"
            JwaKeyType.OKP -> "OKP"
            JwaKeyType.RSA -> "RSA"
            JwaKeyType.oct -> "oct"
            null -> "unknown"
        }
    }
}
