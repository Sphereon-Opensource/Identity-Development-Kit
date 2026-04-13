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
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.resolver.DidDereferenceOptions
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolver
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Implementation of the DID resolver registry.
 *
 * Manages a collection of method-specific resolvers and provides
 * lookup functionality by DID method.
 *
 * Resolvers are automatically discovered via DI multibinding.
 * Any class annotated with `@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())`
 * will be automatically registered.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidResolverRegistry>())
class DidResolverRegistryImpl(
    resolvers: Set<DidResolver>
) : DidResolverRegistry {

    private val resolversByMethod: Map<String, DidResolver> by lazy {
        resolvers.flatMap { resolver ->
            resolver.supportedMethods.map { method -> method to resolver }
        }.toMap()
    }

    override fun getResolver(method: String): DidResolver? {
        return resolversByMethod[method]
    }

    override fun getSupportedMethods(): List<String> {
        return resolversByMethod.keys.toList()
    }

    override fun getCapabilities(method: String): DidMethodCapabilities? {
        return resolversByMethod[method]?.capabilities
    }

    override fun hasResolver(method: String): Boolean {
        return resolversByMethod.containsKey(method)
    }

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions
    ): IdkResult<DidResolutionResult, IdkError> {
        val parsed = ParsedDid.parse(did)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val resolver = getResolver(parsed.method)
            ?: return Err(IdkError.fromString(
                message = "No resolver registered for DID method: ${parsed.method}",
                code = "UNSUPPORTED_OPERATION"
            ))

        return resolver.resolve(did, options)
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions
    ): IdkResult<DidDereferenceResult, IdkError> {
        val parsed = ParsedDid.parse(didUrl)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        val resolver = getResolver(parsed.method)
            ?: return Err(IdkError.fromString(
                message = "No resolver registered for DID method: ${parsed.method}",
                code = "UNSUPPORTED_OPERATION"
            ))

        return resolver.dereference(didUrl, options)
    }

    @ContributesTo(SessionScope::class)
    interface Component : DidResolverRegistry.Component {
        override val didResolverRegistry: DidResolverRegistry
    }
}
