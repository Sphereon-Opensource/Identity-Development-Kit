/*
 * Minimal starter template for a typed ServiceCommand.
 *
 * Copy into your module and replace package/type names.
 */

package com.example.idk.extensions

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter

data class ExampleInput(
    val value: String
)

data class ExampleOutput(
    val normalized: String
)

interface ExampleServiceCommand : ServiceCommand<ExampleInput, ExampleOutput>

class ExampleServiceCommandImpl(
    execution: SessionExecution
) : TypedServiceCommandAdapter<ExampleInput, ExampleOutput>(
    commandId = COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ExampleInput>(),
    outputTypeToken = typeToken<ExampleOutput>()
), ExampleServiceCommand {

    override suspend fun doExecute(
        args: ExampleInput,
        applyDuring: (ExampleInput) -> ExampleInput
    ): IdkResult<ExampleOutput, IdkError> {
        val input = applyDuring(args)
        return Ok(ExampleOutput(normalized = input.value.trim().lowercase()))
    }

    companion object {
        const val COMMAND_ID = "example.command.execute"
    }
}
