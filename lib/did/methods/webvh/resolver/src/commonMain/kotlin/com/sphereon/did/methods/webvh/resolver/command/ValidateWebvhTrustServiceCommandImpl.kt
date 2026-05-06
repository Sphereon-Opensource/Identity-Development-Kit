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

package com.sphereon.did.methods.webvh.resolver.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.methods.webvh.command.ValidateWebvhTrustInput
import com.sphereon.did.methods.webvh.command.ValidateWebvhTrustServiceCommand
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.did.ValidateDidTrustArgs
import com.sphereon.trust.did.ValidateDidTrustCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Bridges the webvh REST surface to `trust.did.validate`. Validates that
 * the input string is a `did:webvh` DID (rejects other methods at the
 * boundary so callers don't accidentally trust a `did:web` or `did:key`
 * via this endpoint), then delegates with webvh-aware defaults.
 *
 * The underlying `ValidateDidTrustCommand` reads `trust.anchors.did.*`
 * configuration and applies request-level overrides. Cryptographic log
 * trust (signatures, pre-rotation, witnesses) is handled by
 * `did.webvh.replay-log`; this command is purely about external anchoring.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ValidateWebvhTrustServiceCommand>())
class ValidateWebvhTrustServiceCommandImpl(
    execution: SessionExecution,
    private val didTrustCommand: ValidateDidTrustCommand,
) : TypedServiceCommandAdapter<ValidateWebvhTrustInput, TrustValidationResult, IdkError>(
        commandId = ValidateWebvhTrustServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateWebvhTrustInput>(),
        outputTypeToken = typeToken<TrustValidationResult>(),
    ),
    ValidateWebvhTrustServiceCommand {
    override val commandId: String get() = ValidateWebvhTrustServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateWebvhTrustInput

    override suspend fun doExecute(
        args: ValidateWebvhTrustInput,
        applyDuring: (ValidateWebvhTrustInput) -> ValidateWebvhTrustInput,
    ): IdkResult<TrustValidationResult, IdkError> {
        val input = applyDuring(args)

        // Boundary check: reject non-webvh DIDs so this endpoint can't be
        // misused to trust a sibling did:web or did:key. The DID method
        // allow-list inside ValidateDidTrustCommand is independent and
        // covers different policy (whether webvh itself is enabled).
        WebvhDidUrlBuilder.decompose(input.did).let { decomposed ->
            if (decomposed.isErr) {
                return Err(decomposed.error)
            }
        }

        return didTrustCommand.execute(
            ValidateDidTrustArgs(
                did = input.did,
                allowedMethods = input.allowedMethods ?: emptyList(),
                trustedDids = input.trustedDids ?: emptyList(),
                identifierJson = null,
            ),
        )
    }
}
