/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.oidfed

import com.sphereon.core.api.Ok
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
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
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Command for validating trust via OpenID Federation trust chains.
 *
 * The command is bound in this port module so a holder graph can inject it. The validator that
 * actually walks the chain is [TrustValidationService] for [TrustContext.TYPE_OPENID_FEDERATION],
 * contributed at runtime by the OpenID Federation trust module when that module is on the graph.
 */
interface ValidateOidfTrustCommand : ServiceCommand<ValidateOidfTrustArgs, TrustValidationResult, IdkError> {
    companion object {
        const val COMMAND_ID = "trust.oidfed.validate"

        internal fun callerSuppliedAnchorsRejected(args: ValidateOidfTrustArgs): Boolean =
            args.trustAnchors.isNotEmpty()
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class ValidateOidfTrustArgs(
    val entityIdentifier: String,
    /**
     * Retained for wire compatibility with older clients. A caller supplied anchor is never
     * admissible; the command rejects a non-empty value before invoking a validator.
     */
    val trustAnchors: List<String> = emptyList(),
    val requiredTrustMarks: List<String> = emptyList(),
    val maxChainDepth: Int = 5,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ValidateOidfTrustCommand>())
class ValidateOidfTrustCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>,
) : TypedServiceCommandAdapter<ValidateOidfTrustArgs, TrustValidationResult, IdkError>(
        commandId = ValidateOidfTrustCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateOidfTrustArgs>(),
        outputTypeToken = typeToken<TrustValidationResult>(),
    ),
    ValidateOidfTrustCommand {
    override val commandId: String get() = ValidateOidfTrustCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ValidateOidfTrustArgs,
        applyDuring: (ValidateOidfTrustArgs) -> ValidateOidfTrustArgs,
    ): IdkResult<TrustValidationResult, IdkError> {
        val applied = applyDuring(args)
        if (ValidateOidfTrustCommand.callerSuppliedAnchorsRejected(applied)) {
            return Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Caller supplied OID Federation trust anchors are not permitted; anchors are selected from persisted tenant trust configuration",
                    validatedAt = Clock.System.now(),
                ),
            )
        }
        val context =
            TrustContext(
                type = TrustContext.TYPE_OPENID_FEDERATION,
                parameters =
                    buildMap {
                        put("entityIdentifier", applied.entityIdentifier)
                        if (applied.requiredTrustMarks.isNotEmpty()) {
                            put("requiredTrustMarks", applied.requiredTrustMarks.joinToString(","))
                        }
                        put("maxChainDepth", applied.maxChainDepth.toString())
                    },
            )
        val validator = validators.firstOrNull { it.supports(context) }
        if (validator == null) {
            return Ok(
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "No validator found for trust context type: ${TrustContext.TYPE_OPENID_FEDERATION}",
                    validatedAt = Clock.System.now(),
                ),
            )
        }
        return Ok(
            validator.validate(
                TrustValidationRequest(
                    identifier = ExternalIdentifierOIDFEntityIdOpts(identifier = applied.entityIdentifier),
                    context = context,
                ),
            ),
        )
    }
}

@ContributesTo(SessionScope::class)
interface OidfedTrustCommandDescriptors {
    @Provides
    @IntoMap
    @StringKey(ValidateOidfTrustCommand.COMMAND_ID)
    fun validateOidfTrust(impl: ValidateOidfTrustCommandImpl): ServiceCommand<*, *, *> = impl
}
