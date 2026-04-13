/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.etsi.validator.ETSITrustValidator
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class ValidateEtsiTrustCommandImpl(
    execution: SessionExecution,
    private val etsiTrustValidator: ETSITrustValidator
) : TypedServiceCommandAdapter<ValidateEtsiTrustArgs, TrustValidationResult>(
    commandId = ValidateEtsiTrustCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateEtsiTrustArgs>(),
    outputTypeToken = typeToken<TrustValidationResult>()
), ValidateEtsiTrustCommand {

    override val commandId: String get() = ValidateEtsiTrustCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ValidateEtsiTrustArgs,
        applyDuring: (ValidateEtsiTrustArgs) -> ValidateEtsiTrustArgs
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        return try {
            val identifier = Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            val parameters = mutableMapOf<String, String>()
            if (applied.territory != null) {
                parameters["territory"] = applied.territory
            }

            val request = TrustValidationRequest(
                identifier = identifier,
                context = TrustContext(
                    type = TrustContext.TYPE_ETSI_TSL,
                    framework = applied.trustListUri,
                    parameters = parameters
                ),
                checkRevocation = applied.checkRevocation
            )
            Ok(etsiTrustValidator.validate(request))
        } catch (e: Exception) {
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "ETSI trust validation failed: ${e.message}",
                    validatedAt = Clock.System.now()
                )
            )
        }
    }
}

@ContributesTo(SessionScope::class)
interface EtsiTrustCommandDescriptors {
    @Provides @IntoSet
    fun validateEtsiTrust(impl: Lazy<ValidateEtsiTrustCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateEtsiTrustCommand.COMMAND_ID) { impl.value }
}
