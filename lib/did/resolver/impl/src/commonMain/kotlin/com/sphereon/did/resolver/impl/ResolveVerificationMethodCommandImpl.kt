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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.extern.NoOpVerificationMethodKeyResolverImpl
import com.sphereon.crypto.resolution.extern.VerificationMethodKeyResolver
import com.sphereon.di.session.SessionScope
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.ResolveVerificationMethodArgs
import com.sphereon.did.resolver.ResolveVerificationMethodCommand
import com.sphereon.did.resolver.ResolveVerificationMethodCommandService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command implementation for resolving a specific verification method by kid.
 *
 * Convenience command for common kid lookup use case.
 * Also implements [VerificationMethodKeyResolver] for CNF resolution.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerificationMethodKeyResolver>(), replaces = [NoOpVerificationMethodKeyResolverImpl::class])
class ResolveVerificationMethodCommandImpl(
    execution: SessionExecution,
    private val registry: DidResolverRegistry,
) : TypedServiceCommandAdapter<ResolveVerificationMethodArgs, VerificationMethod>(
        commandId = ResolveVerificationMethodCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveVerificationMethodArgs>(),
        outputTypeToken = typeToken<VerificationMethod>(),
    ),
    ResolveVerificationMethodCommand,
    ResolveVerificationMethodCommandService,
    VerificationMethodKeyResolver {
    override val commandId: String get() = ResolveVerificationMethodCommand.COMMAND_ID

    override suspend fun resolveVerificationMethod(args: ResolveVerificationMethodArgs): IdkResult<VerificationMethod, IdkError> = execute(args)

    /**
     * Resolves a DID verification method and extracts its public key JWK.
     * Implementation of [VerificationMethodKeyResolver] for CNF holder binding resolution.
     *
     * @param did The DID to resolve (e.g., "did:key:z6Mk...")
     * @param verificationMethodId Optional verification method ID (fragment like "key-1")
     * @return The public key JWK from the resolved verification method, or an error
     */
    override suspend fun resolveVerificationMethodKey(
        did: String,
        verificationMethodId: String?,
    ): IdkResult<JwkType, IdkErrorType> {
        // If no verification method ID provided, we need to pick one
        val kid =
            verificationMethodId ?: run {
                // Resolve DID and pick first verification method
                val result =
                    registry.resolve(did, DidResolutionOptions()).getOrElse {
                        return Err(it)
                    }
                result.didDocument
                    ?.verificationMethod
                    ?.firstOrNull()
                    ?.id
                    ?: return Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "DID $did has no verification methods",
                        ),
                    )
            }

        val args = ResolveVerificationMethodArgs(did = did, kid = kid)
        return resolveVerificationMethod(args).fold(
            success = { vm ->
                vm.publicKeyJwk?.let { Ok(it) }
                    ?: Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "Verification method $kid does not contain a public key JWK",
                        ),
                    )
            },
            failure = { Err(it) },
        )
    }

    override suspend fun supports(args: Any): Boolean = args is ResolveVerificationMethodArgs

    override suspend fun doExecute(
        args: ResolveVerificationMethodArgs,
        applyDuring: (ResolveVerificationMethodArgs) -> ResolveVerificationMethodArgs,
    ): IdkResult<VerificationMethod, IdkError> {
        val processedArgs = applyDuring(args)
        val did = processedArgs.did
        val kid = processedArgs.kid

        log.debug("Resolving verification method $kid from DID: ${did.take(50)}...")

        // Resolve the DID
        val result =
            registry.resolve(did, DidResolutionOptions()).getOrElse {
                return Err(it)
            }

        // Find the verification method by kid
        val vm =
            result.didDocument?.getVerificationMethodById(kid)
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Verification method not found: $kid in DID: $did",
                    ),
                )

        log.info("Resolved verification method: $kid")

        return Ok(vm)
    }
}
