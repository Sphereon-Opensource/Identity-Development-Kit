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

package com.sphereon.jsonld.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * UNTP 0.7.0 `@vocab` MUST-NOT validator. Thin DI adapter; the algorithm
 * lives in [JsonLdContextValidator] so it can be exercised in unit tests
 * without a [SessionExecution].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<ValidateJsonLdContextServiceCommand>())
class ValidateJsonLdContextServiceCommandImpl(
    execution: SessionExecution,
    loader: LinkedDataDocumentLoader,
) : TypedServiceCommandAdapter<ValidateJsonLdContextInput, ValidateJsonLdContextOutput, IdkError>(
        commandId = ValidateJsonLdContextServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateJsonLdContextInput>(),
        outputTypeToken = typeToken<ValidateJsonLdContextOutput>(),
    ),
    ValidateJsonLdContextServiceCommand {
    // Diamond-resolution override required by Kotlin: TypedServiceCommandAdapter
    // and ValidateJsonLdContextServiceCommand both supply commandId. Both would
    // resolve to the same value (passed via super-ctor and via the interface's
    // default getter), but the compiler still demands an explicit choice.
    override val commandId: String get() = ValidateJsonLdContextServiceCommand.COMMAND_ID

    private val validator = JsonLdContextValidator(loader)

    override suspend fun doExecute(
        args: ValidateJsonLdContextInput,
        applyDuring: (ValidateJsonLdContextInput) -> ValidateJsonLdContextInput,
    ): IdkResult<ValidateJsonLdContextOutput, IdkError> {
        val input = applyDuring(args)
        val outcome = validator.validate(input)
        return if (outcome.isErr) Err(IdkError.fromDTO(outcome.error)) else Ok(outcome.value)
    }
}
