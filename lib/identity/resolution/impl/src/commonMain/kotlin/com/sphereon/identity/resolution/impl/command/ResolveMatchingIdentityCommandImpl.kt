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

package com.sphereon.identity.resolution.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.LookupIdentityMatchCommand
import com.sphereon.identity.matching.model.LookupIdentityMatchArgs
import com.sphereon.identity.matching.model.MatchResult
import com.sphereon.identity.resolution.command.ResolveMatchingIdentityCommand
import com.sphereon.identity.resolution.model.IdentityResolutionResult
import com.sphereon.identity.resolution.model.ResolveMatchingIdentityArgs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves an identifier to an internal identity using HMAC hashing + match lookup.
 *
 * Flow:
 * 1. Compute HMAC of the raw identifier via [GenerateMacCommand] (key never leaves KMS)
 * 2. Look up the resulting multihash in the identity match store
 * 3. Return the matched internal identity or [IdentityResolutionResult.NOT_FOUND]
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveMatchingIdentityCommandImpl(
    execution: SessionExecution,
    private val lookupMatchCommand: LookupIdentityMatchCommand,
    private val generateMacCommand: GenerateMacCommand,
) : TypedServiceCommandAdapter<ResolveMatchingIdentityArgs, IdentityResolutionResult>(
        commandId = ResolveMatchingIdentityCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveMatchingIdentityArgs>(),
        outputTypeToken = typeToken<IdentityResolutionResult>(),
    ),
    ResolveMatchingIdentityCommand {
    override val commandId: String get() = ResolveMatchingIdentityCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveMatchingIdentityArgs

    override suspend fun doExecute(
        args: ResolveMatchingIdentityArgs,
        applyDuring: (ResolveMatchingIdentityArgs) -> ResolveMatchingIdentityArgs,
    ): IdkResult<IdentityResolutionResult, IdkError> {
        val applied = applyDuring(args)

        val digestAlg = resolveDigestAlg(applied.macAlgorithm)

        // 1. HMAC hash via KMS (returns multihash-encoded result)
        val mac =
            generateMacCommand
                .execute(
                    GenerateMacArgs(
                        keyId = applied.kmsKeyId,
                        message = applied.identifier.encodeToByteArray(),
                        digestAlgorithm = digestAlg,
                    ),
                ).getOrElse { return Ok(IdentityResolutionResult.NOT_FOUND) }

        // 2. Lookup match via command (using multibase-encoded multihash)
        val matchResult =
            lookupMatchCommand
                .execute(
                    LookupIdentityMatchArgs(
                        identifierHash = mac.macMultibase,
                        identifierType = applied.identifierType,
                        tenantId = applied.tenantId,
                    ),
                ).getOrElse { return Ok(IdentityResolutionResult.NOT_FOUND) }

        // 3. Return result
        return when (matchResult) {
            is MatchResult.Found -> {
                Ok(
                    IdentityResolutionResult.found(
                        internalIdentityId = matchResult.match.internalIdentityId,
                        resolverId = RESOLVER_ID,
                        identifierType = matchResult.match.identifierType,
                        metadata = matchResult.match.metadata,
                    ),
                )
            }

            is MatchResult.NotFound -> {
                Ok(IdentityResolutionResult.NOT_FOUND)
            }
        }
    }

    private fun resolveDigestAlg(macAlgorithm: String): DigestAlg =
        when (macAlgorithm) {
            "HMAC_SHA256" -> DigestAlg.SHA256
            "HMAC_SHA384" -> DigestAlg.SHA384
            "HMAC_SHA512" -> DigestAlg.SHA512
            else -> DigestAlg.SHA256
        }

    companion object {
        const val RESOLVER_ID = "identity-matching"
    }
}
