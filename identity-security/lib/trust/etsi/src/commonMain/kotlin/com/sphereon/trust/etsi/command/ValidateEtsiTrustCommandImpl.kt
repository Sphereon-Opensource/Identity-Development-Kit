/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ValidateEtsiTrustCommand>())
class ValidateEtsiTrustCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>,
) : TypedServiceCommandAdapter<ValidateEtsiTrustArgs, TrustValidationResult, IdkError>(
        commandId = ValidateEtsiTrustCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateEtsiTrustArgs>(),
        outputTypeToken = typeToken<TrustValidationResult>(),
    ),
    ValidateEtsiTrustCommand {
    override val commandId: String get() = ValidateEtsiTrustCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ValidateEtsiTrustArgs,
        applyDuring: (ValidateEtsiTrustArgs) -> ValidateEtsiTrustArgs,
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        return try {
            val identifier = Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            val parameters = mutableMapOf<String, String>()
            if (applied.territory != null) {
                parameters["territory"] = applied.territory
            }

            val context =
                TrustContext(
                    type = TrustContext.TYPE_ETSI_TSL,
                    framework = applied.trustListUri,
                    parameters = parameters,
                )
            val validator =
                validators.firstOrNull { it.supports(context) }
                    ?: return Ok(
                        TrustValidationResult(
                            trusted = false,
                            status = TrustStatus.VALIDATION_ERROR,
                            details = "No ETSI trust validator on classpath",
                            validatedAt = Clock.System.now(),
                        ),
                    )
            val request =
                TrustValidationRequest(
                    identifier = identifier,
                    context = context,
                    checkRevocation = applied.checkRevocation,
                )
            Ok(validator.validate(request))
        } catch (expected: Exception) {
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "ETSI trust validation failed: ${expected.message}",
                    validatedAt = Clock.System.now(),
                ),
            )
        }
    }
}

@ContributesTo(SessionScope::class)
interface EtsiTrustCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ValidateEtsiTrustCommand.COMMAND_ID)
    fun validateEtsiTrust(command: ValidateEtsiTrustCommand): ServiceCommand<*, *, *> = command
}
