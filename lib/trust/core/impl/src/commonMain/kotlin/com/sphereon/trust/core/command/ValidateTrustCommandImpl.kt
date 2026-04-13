/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.model.TrustStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Composite dispatcher that routes to type-specific TrustValidationService implementations
 * based on the TrustContext type.
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateTrustCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>
) : TypedServiceCommandAdapter<ValidateTrustArgs, TrustValidationResult>(
    commandId = ValidateTrustCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateTrustArgs>(),
    outputTypeToken = typeToken<TrustValidationResult>()
), ValidateTrustCommand {

    override val commandId: String get() = ValidateTrustCommand.COMMAND_ID

    private val logger = execution.log.logManager.withTagAsync("ValidateTrustCommand")

    override suspend fun doExecute(
        args: ValidateTrustArgs,
        applyDuring: (ValidateTrustArgs) -> ValidateTrustArgs
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        val context = TrustContext(
            type = applied.contextType,
            framework = applied.framework,
            parameters = applied.parameters
        )

        val validator = validators.firstOrNull { it.supports(context) }
            ?: return Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "No validator found for trust context type: ${applied.contextType}",
                    validatedAt = Clock.System.now()
                )
            )

        return try {
            val identifier = Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            val request = TrustValidationRequest(
                identifier = identifier,
                context = context,
                validationTime = applied.validationTime,
                checkRevocation = applied.checkRevocation
            )
            val result = validator.validate(request)
            Ok(result)
        } catch (e: Exception) {
            logger.error("Trust validation failed", exception = e)
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Validation failed: ${e.message}",
                    validatedAt = Clock.System.now()
                )
            )
        }
    }

}
