/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

interface ValidateX509TrustCommand : ServiceCommand<ValidateX509TrustArgs, TrustValidationResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.x509.validate"
    }
}

@Serializable
data class ValidateX509TrustArgs(
    val identifierJson: String,
    val checkRevocation: Boolean = true,
)

@Inject
@SingleIn(SessionScope::class)
class ValidateX509TrustCommandImpl(
    execution: SessionExecution,
    private val x509TrustValidationService: X509TrustValidationService,
) : TypedServiceCommandAdapter<ValidateX509TrustArgs, TrustValidationResult>(
        commandId = ValidateX509TrustCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateX509TrustArgs>(),
        outputTypeToken = typeToken<TrustValidationResult>(),
    ),
    ValidateX509TrustCommand {
    override val commandId: String get() = ValidateX509TrustCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ValidateX509TrustArgs,
        applyDuring: (ValidateX509TrustArgs) -> ValidateX509TrustArgs,
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)

        return try {
            val identifier = Json.decodeFromString<IdentifierOptsOrResult>(applied.identifierJson)
            val request =
                TrustValidationRequest(
                    identifier = identifier,
                    context = TrustContext(type = TrustContext.TYPE_X509),
                    checkRevocation = applied.checkRevocation,
                )
            Ok(x509TrustValidationService.validate(request))
        } catch (expected: Exception) {
            Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "X.509 validation failed: ${expected.message}",
                    validatedAt = Clock.System.now(),
                ),
            )
        }
    }
}

@ContributesTo(SessionScope::class)
interface X509TrustCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ValidateX509TrustCommand.COMMAND_ID)
    fun validateX509Trust(impl: ValidateX509TrustCommandImpl): ServiceCommand<*, *> = impl
}
