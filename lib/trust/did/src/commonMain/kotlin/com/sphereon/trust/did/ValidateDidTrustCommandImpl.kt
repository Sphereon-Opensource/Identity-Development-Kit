/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.did

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

interface ValidateDidTrustCommand : ServiceCommand<ValidateDidTrustArgs, TrustValidationResult> {
    companion object {
        const val COMMAND_ID = "trust.did.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class ValidateDidTrustArgs(
    val did: String,
    val allowedMethods: List<String> = emptyList(),
    val trustedDids: List<String> = emptyList(),
    val identifierJson: String? = null
)

@Inject
@SingleIn(SessionScope::class)
class ValidateDidTrustCommandImpl(
    execution: SessionExecution,
    private val didTrustValidationService: DidTrustValidationService
) : TypedServiceCommandAdapter<ValidateDidTrustArgs, TrustValidationResult>(
    commandId = ValidateDidTrustCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateDidTrustArgs>(),
    outputTypeToken = typeToken<TrustValidationResult>()
), ValidateDidTrustCommand {

    override val commandId: String get() = ValidateDidTrustCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ValidateDidTrustArgs,
        applyDuring: (ValidateDidTrustArgs) -> ValidateDidTrustArgs
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        return try {
            val parameters = mutableMapOf("did" to applied.did)
            if (applied.allowedMethods.isNotEmpty()) {
                parameters["allowedMethods"] = applied.allowedMethods.joinToString(",")
            }
            if (applied.trustedDids.isNotEmpty()) {
                parameters["trustedDids"] = applied.trustedDids.joinToString(",")
            }

            val identifier = if (applied.identifierJson != null) {
                Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            } else {
                // Create a minimal identifier for DID-only validation
                null
            }

            val effectiveIdentifier = identifier
                ?: com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts(identifier = applied.did)

            val request = TrustValidationRequest(
                identifier = effectiveIdentifier,
                context = TrustContext(
                    type = TrustContext.TYPE_DID,
                    parameters = parameters
                )
            )

            Ok(didTrustValidationService.validate(request))
        } catch (e: Exception) {
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "DID trust validation failed: ${e.message}",
                    validatedAt = Clock.System.now()
                )
            )
        }
    }

}

@ContributesTo(SessionScope::class)
interface DidTrustCommandDescriptors {
    @Provides @IntoSet
    fun validateDidTrust(impl: Lazy<ValidateDidTrustCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateDidTrustCommand.COMMAND_ID) { impl.value }
}
