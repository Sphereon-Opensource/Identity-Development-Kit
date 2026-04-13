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

package com.sphereon.did.resolver.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.DereferenceDidArgs
import com.sphereon.did.resolver.DereferenceDidCommand
import com.sphereon.did.resolver.DereferenceDidCommandService
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Command implementation for dereferencing DID URLs.
 *
 * Supports:
 * - Kid resolution: `did:example:123#key-1`
 * - Service resolution: `did:example:123?service=hub`
 * - Full document: `did:example:123`
 */
@Inject
@SingleIn(SessionScope::class)
class DereferenceDidCommandImpl(
    execution: SessionExecution,
    private val registry: DidResolverRegistry,
) : TypedServiceCommandAdapter<DereferenceDidArgs, DidDereferenceResult>(
        commandId = DereferenceDidCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DereferenceDidArgs>(),
        outputTypeToken = typeToken<DidDereferenceResult>(),
    ),
    DereferenceDidCommand,
    DereferenceDidCommandService {
    override val commandId: String get() = DereferenceDidCommand.COMMAND_ID

    override suspend fun dereference(args: DereferenceDidArgs): IdkResult<DidDereferenceResult, IdkError> = execute(args)

    override suspend fun supports(args: Any): Boolean = args is DereferenceDidArgs

    override suspend fun doExecute(
        args: DereferenceDidArgs,
        applyDuring: (DereferenceDidArgs) -> DereferenceDidArgs,
    ): IdkResult<DidDereferenceResult, IdkError> {
        val processedArgs = applyDuring(args)
        val didUrl = processedArgs.didUrl
        val options = processedArgs.options

        log.debug("Dereferencing DID URL: ${didUrl.take(80)}...")

        // Parse the DID URL to get the method
        val parsed =
            ParsedDid.parse(didUrl)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        val method = parsed.method

        // Get the resolver for this method
        val resolver =
            registry.getResolver(method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No resolver registered for DID method: $method",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Dereference using the method-specific resolver
        val result = resolver.dereference(didUrl, options)

        log.info("Dereferenced DID URL: ${didUrl.take(80)}")

        return result
    }
}
