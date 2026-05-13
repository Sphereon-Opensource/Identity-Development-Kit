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
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Validates a credential payload against the registered JSON Schema for its
 * type. Thin DI adapter; the algorithm lives in [JsonLdSchemaValidator].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<ValidateJsonLdSchemaServiceCommand>())
class ValidateJsonLdSchemaServiceCommandImpl(
    execution: SessionExecution,
    registry: JsonLdSchemaRegistry,
) : TypedServiceCommandAdapter<ValidateJsonLdSchemaInput, ValidateJsonLdSchemaOutput, IdkError>(
        commandId = ValidateJsonLdSchemaServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateJsonLdSchemaInput>(),
        outputTypeToken = typeToken<ValidateJsonLdSchemaOutput>(),
    ),
    ValidateJsonLdSchemaServiceCommand {
    // Diamond-resolution override required by Kotlin (see counterpart on
    // ValidateJsonLdContextServiceCommandImpl).
    override val commandId: String get() = ValidateJsonLdSchemaServiceCommand.COMMAND_ID

    private val validator = JsonLdSchemaValidator(registry)

    override suspend fun doExecute(
        args: ValidateJsonLdSchemaInput,
        applyDuring: (ValidateJsonLdSchemaInput) -> ValidateJsonLdSchemaInput,
    ): IdkResult<ValidateJsonLdSchemaOutput, IdkError> {
        val input = applyDuring(args)
        val outcome = validator.validate(input)
        return if (outcome.isErr) Err(IdkError.fromDTO(outcome.error)) else Ok(outcome.value)
    }
}
