/*
 * © 2026 Sphereon International B.V.
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
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Composite dispatcher that routes to type-specific TrustValidationService implementations
 * based on the TrustContext type.
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateTrustCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>,
    private val extractors: Set<EntityInfoExtractor>,
) : TypedServiceCommandAdapter<ValidateTrustArgs, TrustValidationResult, IdkError>(
        commandId = ValidateTrustCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateTrustArgs>(),
        outputTypeToken = typeToken<TrustValidationResult>(),
    ),
    ValidateTrustCommand {
    override val commandId: String get() = ValidateTrustCommand.COMMAND_ID

    private val logger = execution.log.logManager.withTagAsync("ValidateTrustCommand")

    override suspend fun doExecute(
        args: ValidateTrustArgs,
        applyDuring: (ValidateTrustArgs) -> ValidateTrustArgs,
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        val context =
            TrustContext(
                type = applied.contextType,
                framework = applied.framework,
                parameters = applied.parameters,
            )

        val validator =
            validators.firstOrNull { it.supports(context) }
                ?: return Ok(
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "No validator found for trust context type: ${applied.contextType}",
                        validatedAt = Clock.System.now(),
                    ),
                )

        return try {
            val identifier = Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            val request =
                TrustValidationRequest(
                    identifier = identifier,
                    context = context,
                    validationTime = applied.validationTime,
                    checkRevocation = applied.checkRevocation,
                    entityDiscovery = applied.entityDiscovery,
                )
            var result = validator.validate(request)

            // Deferred entity discovery: enrich after the winning path is found
            val discoveryOpts = applied.entityDiscovery
            if (discoveryOpts != null && discoveryOpts.enabled && discoveryOpts.deferred &&
                result.trusted && result.discoveredEntities.isEmpty()
            ) {
                val extractor = extractors.firstOrNull { it.supports(context) }
                if (extractor != null) {
                    val entities =
                        extractor.extractEntityInfo(
                            context = context,
                            validationPath = result.validationPath,
                            options = discoveryOpts.copy(deferred = false), // prevent infinite loop
                        )
                    result = result.copy(discoveredEntities = entities)
                }
            }

            Ok(result)
        } catch (expected: Exception) {
            logger.error("Trust validation failed", exception = expected)
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Validation failed: ${expected.message}",
                    validatedAt = Clock.System.now(),
                ),
            )
        }
    }
}
