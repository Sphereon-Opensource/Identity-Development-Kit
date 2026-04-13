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
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.ResolveVerificationMethodsByPurposeArgs
import com.sphereon.did.resolver.ResolveVerificationMethodsByPurposeCommand
import com.sphereon.did.resolver.ResolveVerificationMethodsByPurposeCommandService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Command implementation for resolving all verification methods for a specific purpose.
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveVerificationMethodsByPurposeCommandImpl(
    execution: SessionExecution,
    private val registry: DidResolverRegistry
) : TypedServiceCommandAdapter<ResolveVerificationMethodsByPurposeArgs, List<VerificationMethod>>(
    commandId = ResolveVerificationMethodsByPurposeCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ResolveVerificationMethodsByPurposeArgs>(),
    outputTypeToken = typeToken<List<VerificationMethod>>(),
), ResolveVerificationMethodsByPurposeCommand, ResolveVerificationMethodsByPurposeCommandService {

    override val commandId: String get() = ResolveVerificationMethodsByPurposeCommand.COMMAND_ID

    override suspend fun resolveVerificationMethodsByPurpose(
        args: ResolveVerificationMethodsByPurposeArgs
    ): IdkResult<List<VerificationMethod>, IdkError> {
        return execute(args)
    }

    override suspend fun supports(args: Any): Boolean = args is ResolveVerificationMethodsByPurposeArgs

    override suspend fun doExecute(
        args: ResolveVerificationMethodsByPurposeArgs,
        applyDuring: (ResolveVerificationMethodsByPurposeArgs) -> ResolveVerificationMethodsByPurposeArgs
    ): IdkResult<List<VerificationMethod>, IdkError> {
        val processedArgs = applyDuring(args)
        val did = processedArgs.did
        val purpose = processedArgs.purpose

        log.debug("Resolving verification methods for purpose $purpose from DID: ${did.take(50)}...")

        // Resolve the DID
        val result = registry.resolve(did, DidResolutionOptions()).getOrElse {
            return Err(it)
        }

        // Get verification methods for the purpose
        val vms = result.verificationMethodsByPurpose[purpose] ?: emptyList()

        log.info("Resolved ${vms.size} verification methods for purpose: $purpose")

        return Ok(vms)
    }
}
